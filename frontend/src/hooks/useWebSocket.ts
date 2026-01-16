import { useEffect, useRef, useCallback, useState } from 'react';
import { useAuthStore } from '../store/authStore';
import { invalidateCacheForEvent } from '../utils/cache';

export interface WebSocketEvent {
  type: string;
  data?: Record<string, string>;
  timestamp: number;
  sessionId?: string;
}

export type EventHandler = (event: WebSocketEvent) => void;

interface UseWebSocketOptions {
  onEvent?: EventHandler;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: Event) => void;
  reconnectInterval?: number;
  maxReconnectAttempts?: number;
}

interface UseWebSocketReturn {
  isConnected: boolean;
  send: (message: string) => void;
  lastEvent: WebSocketEvent | null;
}

/**
 * Custom hook for WebSocket connection with automatic reconnection.
 */
export function useWebSocket(options: UseWebSocketOptions = {}): UseWebSocketReturn {
  const {
    onEvent,
    onConnect,
    onDisconnect,
    onError,
    reconnectInterval = 5000,
    maxReconnectAttempts = 5,
  } = options;

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectAttemptsRef = useRef(0);
  const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [isConnected, setIsConnected] = useState(false);
  const [lastEvent, setLastEvent] = useState<WebSocketEvent | null>(null);

  const { sessionId } = useAuthStore();

  const connect = useCallback(() => {
    if (!sessionId) return;

    // Close existing connection
    if (wsRef.current) {
      wsRef.current.close();
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const url = `${protocol}//${host}/api/ws/${sessionId}`;

    try {
      const ws = new WebSocket(url);

      ws.onopen = () => {
        setIsConnected(true);
        reconnectAttemptsRef.current = 0;
        onConnect?.();
      };

      ws.onclose = (event) => {
        setIsConnected(false);
        onDisconnect?.();

        // Attempt reconnection if not intentionally closed
        if (event.code !== 1000 && reconnectAttemptsRef.current < maxReconnectAttempts) {
          reconnectAttemptsRef.current += 1;
          reconnectTimeoutRef.current = setTimeout(connect, reconnectInterval);
        }
      };

      ws.onerror = (error) => {
        console.error('WebSocket error', error);
        onError?.(error);
      };

      ws.onmessage = (event) => {
        try {
          const data: WebSocketEvent = JSON.parse(event.data);
          setLastEvent(data);

          // Skip heartbeat events for the callback
          if (data.type !== 'HEARTBEAT' && data.type !== 'CONNECTED' && data.type !== 'PONG') {
            // Automatically invalidate cache for this event type
            invalidateCacheForEvent(data.type);
            onEvent?.(data);
          }
        } catch (e) {
          console.error('Failed to parse WebSocket message', e);
        }
      };

      wsRef.current = ws;
    } catch (error) {
      console.error('Failed to create WebSocket', error);
    }
  }, [sessionId, onEvent, onConnect, onDisconnect, onError, reconnectInterval, maxReconnectAttempts]);

  const disconnect = useCallback(() => {
    if (reconnectTimeoutRef.current) {
      clearTimeout(reconnectTimeoutRef.current);
      reconnectTimeoutRef.current = null;
    }

    if (wsRef.current) {
      wsRef.current.close(1000, 'User disconnected');
      wsRef.current = null;
    }
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
    if (!isConnected) return;

    const pingInterval = setInterval(() => {
      send('ping');
    }, 25000);

    return () => clearInterval(pingInterval);
  }, [isConnected, send]);

  return {
    isConnected,
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
