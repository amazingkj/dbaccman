import { useEffect, useRef, useCallback, useState, useLayoutEffect } from 'react';
import { useAuthStore } from '../store/authStore';
import { invalidateCacheForEvent } from '../utils/cache';

export interface WebSocketEvent {
  type: string;
  data?: Record<string, string>;
  timestamp: number;
  sessionId?: string;
}

export type EventHandler = (event: WebSocketEvent) => void;

export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting';

interface UseWebSocketOptions {
  onEvent?: EventHandler;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: Event) => void;
  baseReconnectInterval?: number;
  maxReconnectAttempts?: number;
  maxReconnectDelay?: number;
}

/**
 * Store event handler in ref to avoid re-creating callbacks (advanced-event-handler-refs pattern)
 */
function useLatest<T>(value: T): { readonly current: T } {
  const ref = useRef(value);
  useLayoutEffect(() => {
    ref.current = value;
  });
  return ref;
}

interface UseWebSocketReturn {
  isConnected: boolean;
  connectionState: ConnectionState;
  reconnectAttempt: number;
  send: (message: string) => void;
  lastEvent: WebSocketEvent | null;
}

/**
 * Calculate exponential backoff delay with jitter.
 */
function getReconnectDelay(attempt: number, baseDelay: number, maxDelay: number): number {
  // Exponential backoff: baseDelay * 2^attempt
  const exponentialDelay = baseDelay * Math.pow(2, attempt);
  // Add random jitter (0-25% of delay)
  const jitter = exponentialDelay * Math.random() * 0.25;
  // Cap at maxDelay
  return Math.min(exponentialDelay + jitter, maxDelay);
}

/**
 * Custom hook for WebSocket connection with automatic reconnection using exponential backoff.
 */
export function useWebSocket(options: UseWebSocketOptions = {}): UseWebSocketReturn {
  const {
    onEvent,
    onConnect,
    onDisconnect,
    onError,
    baseReconnectInterval = 1000,
    maxReconnectAttempts = 10,
    maxReconnectDelay = 30000,
  } = options;

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const intentionalCloseRef = useRef(false);

  const [connectionState, setConnectionState] = useState<ConnectionState>('disconnected');
  const [lastEvent, setLastEvent] = useState<WebSocketEvent | null>(null);
  const [reconnectAttempt, setReconnectAttempt] = useState(0);

  const { sessionId } = useAuthStore();

  // Store callbacks in refs to avoid re-creating connect/scheduleReconnect
  const onEventRef = useLatest(onEvent);
  const onConnectRef = useLatest(onConnect);
  const onDisconnectRef = useLatest(onDisconnect);
  const onErrorRef = useLatest(onError);

  const scheduleReconnect = useCallback(() => {
    if (reconnectAttemptsRef.current >= maxReconnectAttempts) {
      setConnectionState('disconnected');
      return;
    }

    const delay = getReconnectDelay(
      reconnectAttemptsRef.current,
      baseReconnectInterval,
      maxReconnectDelay
    );

    reconnectAttemptsRef.current += 1;
    setReconnectAttempt(reconnectAttemptsRef.current);
    setConnectionState('reconnecting');

    reconnectTimeoutRef.current = setTimeout(() => {
      connect();
    }, delay);
  }, [baseReconnectInterval, maxReconnectAttempts, maxReconnectDelay]);

  const connect = useCallback(() => {
    if (!sessionId) return;

    // Close existing connection
    if (wsRef.current) {
      intentionalCloseRef.current = true;
      wsRef.current.close();
    }

    setConnectionState('connecting');
    intentionalCloseRef.current = false;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const url = `${protocol}//${host}/api/ws/${sessionId}`;

    try {
      const ws = new WebSocket(url);

      ws.onopen = () => {
        setConnectionState('connected');
        reconnectAttemptsRef.current = 0;
        setReconnectAttempt(0);
        onConnectRef.current?.();
      };

      ws.onclose = (event) => {
        setConnectionState('disconnected');
        onDisconnectRef.current?.();

        // Attempt reconnection if not intentionally closed and not normal closure
        if (!intentionalCloseRef.current && event.code !== 1000) {
          scheduleReconnect();
        }
      };

      ws.onerror = (error) => {
        onErrorRef.current?.(error);
      };

      ws.onmessage = (event) => {
        try {
          const data: WebSocketEvent = JSON.parse(event.data);
          setLastEvent(data);

          // Skip heartbeat events for the callback
          if (data.type !== 'HEARTBEAT' && data.type !== 'CONNECTED' && data.type !== 'PONG') {
            // Automatically invalidate cache for this event type
            invalidateCacheForEvent(data.type);
            onEventRef.current?.(data);
          }
        } catch {
          // Silently ignore parse errors - non-JSON messages are not expected
        }
      };

      wsRef.current = ws;
    } catch {
      // Connection failed, schedule reconnect
      scheduleReconnect();
    }
  }, [sessionId, scheduleReconnect, onEventRef, onConnectRef, onDisconnectRef, onErrorRef]);

  const disconnect = useCallback(() => {
    intentionalCloseRef.current = true;

    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    if (wsRef.current) {
      wsRef.current.close(1000, 'User disconnected');
      wsRef.current = null;
    }

    setConnectionState('disconnected');
    reconnectAttemptsRef.current = 0;
    setReconnectAttempt(0);
  }, []);

  const send = useCallback((message: string) => {
    if (wsRef.current?.readyState === WebSocket.OPEN) {
      wsRef.current.send(message);
    }
  }, []);

  // Connect when sessionId is available
  useEffect(() => {
    if (sessionId) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [sessionId, connect, disconnect]);

  // Ping to keep connection alive
  useEffect(() => {
    if (connectionState !== 'connected') return;

    const pingInterval = setInterval(() => {
      send('ping');
    }, 25000);

    return () => clearInterval(pingInterval);
  }, [connectionState, send]);

  return {
    isConnected: connectionState === 'connected',
    connectionState,
    reconnectAttempt,
    send,
    lastEvent,
  };
}

/**
 * Hook to listen for specific event types.
 */
export function useWebSocketEvent(
  eventType: string | string[],
  handler: EventHandler
): UseWebSocketReturn {
  const types = Array.isArray(eventType) ? eventType : [eventType];

  return useWebSocket({
    onEvent: (event) => {
      if (types.includes(event.type)) {
        handler(event);
      }
    },
  });
}
