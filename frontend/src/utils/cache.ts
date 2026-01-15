/**
 * In-memory cache utility with TTL support and request deduplication.
 * Used for caching API responses to reduce unnecessary network requests.
 */

interface CacheEntry<T> {
  data: T
  expiry: number
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
   * Set cache with TTL (in milliseconds).
   */
  set<T>(key: string, data: T, ttlMs: number): void {
    this.cache.set(key, {
      data,
      expiry: Date.now() + ttlMs,
    })
  }

  /**
   * Get cached data or fetch it, preventing duplicate requests.
   * If a request is already in flight, returns the pending promise.
   */
  async getOrFetch<T>(
    key: string,
    fetcher: () => Promise<T>,
    ttlMs: number
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
        this.set(key, data, ttlMs)
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
   * Clear all cache.
   */
  clear(): void {
    this.cache.clear()
  }

  /**
   * Get cache statistics for debugging.
   */
  getStats(): { cacheSize: number; pendingRequests: number } {
    return {
      cacheSize: this.cache.size,
      pendingRequests: this.pendingRequests.size,
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
}
