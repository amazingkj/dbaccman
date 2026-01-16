/**
 * In-memory cache utility with TTL support, request deduplication,
 * and tag-based invalidation for cache coherence.
 * Used for caching API responses to reduce unnecessary network requests.
 */

interface CacheEntry<T> {
  data: T
  expiry: number
  tags: string[]
}

class ApiCache {
  private cache = new Map<string, CacheEntry<unknown>>()
  private pendingRequests = new Map<string, Promise<unknown>>()

  /**
   * Get cached data if valid (not expired).
   */
  get<T>(key: string): T | null {
    const entry = this.cache.get(key)
    if (!entry) return null

    if (Date.now() > entry.expiry) {
      this.cache.delete(key)
      return null
    }

    return entry.data as T
  }

  /**
   * Set cache with TTL (in milliseconds) and optional tags for grouped invalidation.
   */
  set<T>(key: string, data: T, ttlMs: number, tags: string[] = []): void {
    this.cache.set(key, {
      data,
      expiry: Date.now() + ttlMs,
      tags,
    })
  }

  /**
   * Get cached data or fetch it, preventing duplicate requests.
   * If a request is already in flight, returns the pending promise.
   */
  async getOrFetch<T>(
    key: string,
    fetcher: () => Promise<T>,
    ttlMs: number,
    tags: string[] = []
  ): Promise<T> {
    // Check cache first
    const cached = this.get<T>(key)
    if (cached !== null) return cached

    // Check if request is already in flight
    const pending = this.pendingRequests.get(key)
    if (pending) {
      return pending as Promise<T>
    }

    // Make new request
    const request = fetcher()
      .then((data) => {
        this.set(key, data, ttlMs, tags)
        this.pendingRequests.delete(key)
        return data
      })
      .catch((error) => {
        this.pendingRequests.delete(key)
        throw error
      })

    this.pendingRequests.set(key, request)
    return request
  }

  /**
   * Check if there's a pending request for the given key.
   */
  hasPendingRequest(key: string): boolean {
    return this.pendingRequests.has(key)
  }

  /**
   * Invalidate specific cache entry.
   */
  invalidate(key: string): void {
    this.cache.delete(key)
  }

  /**
   * Invalidate all entries matching a prefix.
   */
  invalidatePrefix(prefix: string): void {
    for (const key of this.cache.keys()) {
      if (key.startsWith(prefix)) {
        this.cache.delete(key)
      }
    }
  }

  /**
   * Invalidate all entries with a specific tag.
   * Useful for invalidating related data when an entity changes.
   */
  invalidateByTag(tag: string): void {
    for (const [key, entry] of this.cache.entries()) {
      if (entry.tags.includes(tag)) {
        this.cache.delete(key)
      }
    }
  }

  /**
   * Invalidate all entries with any of the specified tags.
   */
  invalidateByTags(tags: string[]): void {
    for (const [key, entry] of this.cache.entries()) {
      if (tags.some(tag => entry.tags.includes(tag))) {
        this.cache.delete(key)
      }
    }
  }

  /**
   * Clear all cache.
   */
  clear(): void {
    this.cache.clear()
  }

  /**
   * Get cache statistics for debugging.
   */
  getStats(): { cacheSize: number; pendingRequests: number; keys: string[] } {
    return {
      cacheSize: this.cache.size,
      pendingRequests: this.pendingRequests.size,
      keys: Array.from(this.cache.keys()),
    }
  }
}

// Singleton instance
export const apiCache = new ApiCache()

// TTL constants (in milliseconds)
export const CACHE_TTL = {
  SHORT: 30 * 1000,       // 30 seconds - for frequently changing data
  MEDIUM: 2 * 60 * 1000,  // 2 minutes - for moderately stable data
  LONG: 5 * 60 * 1000,    // 5 minutes - for rarely changing data
}

// Cache key generators
export const CACHE_KEYS = {
  DASHBOARD_STATS: 'dashboard:stats',
  DATABASES: 'databases:list',
  TABLESPACES: 'tablespaces:list',
  TABLESPACE_TABLES: (name: string) => `tablespace:${name}:tables`,
  TABLES: (db: string) => `tables:${db}`,
  COLUMNS: (db: string, table: string) => `columns:${db}:${table}`,
  INDEXES: (db: string, table: string) => `indexes:${db}:${table}`,
  ROLES: 'roles:list',
  COMMON_ROLES: 'roles:common',
  PDBS: 'pdbs:list',
  ACCOUNTS: 'accounts:list',
  ACCOUNTS_PAGINATED: (page: number, pageSize: number) => `accounts:page:${page}:${pageSize}`,
  PERMISSIONS: (username: string, host: string) => `permissions:${username}@${host}`,
  SESSIONS: 'sessions:active',
}

// Cache tags for grouped invalidation
export const CACHE_TAGS = {
  ACCOUNTS: 'accounts',
  PERMISSIONS: 'permissions',
  SESSIONS: 'sessions',
  DATABASES: 'databases',
  TABLES: 'tables',
  TABLESPACES: 'tablespaces',
  DASHBOARD: 'dashboard',
  ROLES: 'roles',
}

/**
 * Event-based cache invalidation mapping.
 * Maps WebSocket event types to cache tags that should be invalidated.
 */
export const EVENT_CACHE_INVALIDATION: Record<string, string[]> = {
  // Account events invalidate accounts and dashboard
  ACCOUNT_CREATED: [CACHE_TAGS.ACCOUNTS, CACHE_TAGS.DASHBOARD],
  ACCOUNT_DELETED: [CACHE_TAGS.ACCOUNTS, CACHE_TAGS.DASHBOARD, CACHE_TAGS.PERMISSIONS],
  ACCOUNT_UPDATED: [CACHE_TAGS.ACCOUNTS],
  ACCOUNT_LOCKED: [CACHE_TAGS.ACCOUNTS, CACHE_TAGS.DASHBOARD],
  ACCOUNT_UNLOCKED: [CACHE_TAGS.ACCOUNTS, CACHE_TAGS.DASHBOARD],

  // Permission events
  PERMISSION_GRANTED: [CACHE_TAGS.PERMISSIONS],
  PERMISSION_REVOKED: [CACHE_TAGS.PERMISSIONS],

  // Session events
  DB_SESSION_STARTED: [CACHE_TAGS.SESSIONS, CACHE_TAGS.DASHBOARD],
  DB_SESSION_KILLED: [CACHE_TAGS.SESSIONS, CACHE_TAGS.DASHBOARD],
}

/**
 * Invalidate cache based on WebSocket event type.
 */
export function invalidateCacheForEvent(eventType: string): void {
  const tagsToInvalidate = EVENT_CACHE_INVALIDATION[eventType]
  if (tagsToInvalidate) {
    apiCache.invalidateByTags(tagsToInvalidate)
  }
}
