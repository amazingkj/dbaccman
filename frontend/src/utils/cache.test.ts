import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  apiCache,
  CACHE_TTL,
  CACHE_KEYS,
  CACHE_TAGS,
  invalidateCacheForEvent,
} from './cache'

describe('ApiCache', () => {
  beforeEach(() => {
    apiCache.clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('get/set', () => {
    it('should store and retrieve data', () => {
      const testData = { id: 1, name: 'test' }
      apiCache.set('test-key', testData, CACHE_TTL.SHORT)

      expect(apiCache.get('test-key')).toEqual(testData)
    })

    it('should return null for non-existent keys', () => {
      expect(apiCache.get('non-existent')).toBeNull()
    })

    it('should return null for expired cache', () => {
      apiCache.set('expire-test', 'data', 1000) // 1 second TTL

      expect(apiCache.get('expire-test')).toBe('data')

      vi.advanceTimersByTime(1001)

      expect(apiCache.get('expire-test')).toBeNull()
    })

    it('should store data with tags', () => {
      apiCache.set('tagged-data', 'value', CACHE_TTL.SHORT, [CACHE_TAGS.ACCOUNTS])

      expect(apiCache.get('tagged-data')).toBe('value')
    })
  })

  describe('getOrFetch', () => {
    it('should return cached data without calling fetcher', async () => {
      const fetcher = vi.fn().mockResolvedValue('new data')
      apiCache.set('cached', 'cached data', CACHE_TTL.LONG)

      const result = await apiCache.getOrFetch('cached', fetcher, CACHE_TTL.LONG)

      expect(result).toBe('cached data')
      expect(fetcher).not.toHaveBeenCalled()
    })

    it('should call fetcher when cache misses', async () => {
      const fetcher = vi.fn().mockResolvedValue('fetched data')

      const result = await apiCache.getOrFetch('new-key', fetcher, CACHE_TTL.SHORT)

      expect(result).toBe('fetched data')
      expect(fetcher).toHaveBeenCalled()
    })

    it('should cache fetched data', async () => {
      const fetcher = vi.fn().mockResolvedValue('fetched data')

      await apiCache.getOrFetch('fetch-test', fetcher, CACHE_TTL.SHORT)

      expect(apiCache.get('fetch-test')).toBe('fetched data')
    })

    it('should deduplicate concurrent requests', async () => {
      let resolvePromise: (value: string) => void
      const fetcher = vi.fn(() => new Promise<string>((resolve) => {
        resolvePromise = resolve
      }))

      // Start two concurrent requests
      const promise1 = apiCache.getOrFetch('dedup-test', fetcher, CACHE_TTL.SHORT)
      const promise2 = apiCache.getOrFetch('dedup-test', fetcher, CACHE_TTL.SHORT)

      expect(apiCache.hasPendingRequest('dedup-test')).toBe(true)
      expect(fetcher).toHaveBeenCalledTimes(1)

      // Resolve the promise
      resolvePromise!('result')

      const [result1, result2] = await Promise.all([promise1, promise2])

      expect(result1).toBe('result')
      expect(result2).toBe('result')
      expect(fetcher).toHaveBeenCalledTimes(1)
    })

    it('should remove pending request on error', async () => {
      const fetcher = vi.fn().mockRejectedValue(new Error('Fetch failed'))

      await expect(
        apiCache.getOrFetch('error-test', fetcher, CACHE_TTL.SHORT)
      ).rejects.toThrow('Fetch failed')

      expect(apiCache.hasPendingRequest('error-test')).toBe(false)
    })

    it('should set tags on fetched data', async () => {
      const fetcher = vi.fn().mockResolvedValue('data')

      await apiCache.getOrFetch('tagged', fetcher, CACHE_TTL.SHORT, [CACHE_TAGS.ACCOUNTS])

      // Invalidate by tag should remove this entry
      apiCache.invalidateByTag(CACHE_TAGS.ACCOUNTS)

      expect(apiCache.get('tagged')).toBeNull()
    })
  })

  describe('invalidation', () => {
    beforeEach(() => {
      apiCache.set('key1', 'value1', CACHE_TTL.LONG)
      apiCache.set('key2', 'value2', CACHE_TTL.LONG)
      apiCache.set('prefix:a', 'a', CACHE_TTL.LONG)
      apiCache.set('prefix:b', 'b', CACHE_TTL.LONG)
      apiCache.set('tagged1', 'v1', CACHE_TTL.LONG, [CACHE_TAGS.ACCOUNTS])
      apiCache.set('tagged2', 'v2', CACHE_TTL.LONG, [CACHE_TAGS.ACCOUNTS, CACHE_TAGS.DASHBOARD])
      apiCache.set('tagged3', 'v3', CACHE_TTL.LONG, [CACHE_TAGS.SESSIONS])
    })

    it('should invalidate specific key', () => {
      apiCache.invalidate('key1')

      expect(apiCache.get('key1')).toBeNull()
      expect(apiCache.get('key2')).toBe('value2')
    })

    it('should invalidate by prefix', () => {
      apiCache.invalidatePrefix('prefix:')

      expect(apiCache.get('prefix:a')).toBeNull()
      expect(apiCache.get('prefix:b')).toBeNull()
      expect(apiCache.get('key1')).toBe('value1')
    })

    it('should invalidate by single tag', () => {
      apiCache.invalidateByTag(CACHE_TAGS.ACCOUNTS)

      expect(apiCache.get('tagged1')).toBeNull()
      expect(apiCache.get('tagged2')).toBeNull()
      expect(apiCache.get('tagged3')).toBe('v3')
    })

    it('should invalidate by multiple tags', () => {
      apiCache.invalidateByTags([CACHE_TAGS.ACCOUNTS, CACHE_TAGS.SESSIONS])

      expect(apiCache.get('tagged1')).toBeNull()
      expect(apiCache.get('tagged2')).toBeNull()
      expect(apiCache.get('tagged3')).toBeNull()
    })

    it('should clear all cache', () => {
      apiCache.clear()

      expect(apiCache.get('key1')).toBeNull()
      expect(apiCache.get('key2')).toBeNull()
      expect(apiCache.get('prefix:a')).toBeNull()
    })
  })

  describe('getStats', () => {
    it('should return correct statistics', () => {
      apiCache.set('a', 1, CACHE_TTL.LONG)
      apiCache.set('b', 2, CACHE_TTL.LONG)

      const stats = apiCache.getStats()

      expect(stats.cacheSize).toBe(2)
      expect(stats.keys).toContain('a')
      expect(stats.keys).toContain('b')
    })
  })
})

describe('CACHE_KEYS', () => {
  it('should generate correct tablespace tables key', () => {
    expect(CACHE_KEYS.TABLESPACE_TABLES('USERS')).toBe('tablespace:USERS:tables')
  })

  it('should generate correct tables key', () => {
    expect(CACHE_KEYS.TABLES('mydb')).toBe('tables:mydb')
  })

  it('should generate correct columns key', () => {
    expect(CACHE_KEYS.COLUMNS('mydb', 'users')).toBe('columns:mydb:users')
  })

  it('should generate correct indexes key', () => {
    expect(CACHE_KEYS.INDEXES('mydb', 'users')).toBe('indexes:mydb:users')
  })

  it('should generate correct accounts paginated key', () => {
    expect(CACHE_KEYS.ACCOUNTS_PAGINATED(1, 20)).toBe('accounts:page:1:20')
  })

  it('should generate correct permissions key', () => {
    expect(CACHE_KEYS.PERMISSIONS('admin', 'localhost')).toBe('permissions:admin@localhost')
  })
})

describe('CACHE_TTL', () => {
  it('should have correct values', () => {
    expect(CACHE_TTL.SHORT).toBe(30 * 1000)
    expect(CACHE_TTL.MEDIUM).toBe(2 * 60 * 1000)
    expect(CACHE_TTL.LONG).toBe(5 * 60 * 1000)
  })
})

describe('invalidateCacheForEvent', () => {
  beforeEach(() => {
    apiCache.clear()
    apiCache.set('accounts', 'data', CACHE_TTL.LONG, [CACHE_TAGS.ACCOUNTS])
    apiCache.set('dashboard', 'data', CACHE_TTL.LONG, [CACHE_TAGS.DASHBOARD])
    apiCache.set('permissions', 'data', CACHE_TTL.LONG, [CACHE_TAGS.PERMISSIONS])
    apiCache.set('sessions', 'data', CACHE_TTL.LONG, [CACHE_TAGS.SESSIONS])
  })

  it('should invalidate accounts and dashboard on ACCOUNT_CREATED', () => {
    invalidateCacheForEvent('ACCOUNT_CREATED')

    expect(apiCache.get('accounts')).toBeNull()
    expect(apiCache.get('dashboard')).toBeNull()
    expect(apiCache.get('sessions')).toBe('data')
  })

  it('should invalidate accounts, dashboard, permissions on ACCOUNT_DELETED', () => {
    invalidateCacheForEvent('ACCOUNT_DELETED')

    expect(apiCache.get('accounts')).toBeNull()
    expect(apiCache.get('dashboard')).toBeNull()
    expect(apiCache.get('permissions')).toBeNull()
    expect(apiCache.get('sessions')).toBe('data')
  })

  it('should invalidate permissions on PERMISSION_GRANTED', () => {
    invalidateCacheForEvent('PERMISSION_GRANTED')

    expect(apiCache.get('permissions')).toBeNull()
    expect(apiCache.get('accounts')).toBe('data')
  })

  it('should invalidate sessions and dashboard on DB_SESSION_KILLED', () => {
    invalidateCacheForEvent('DB_SESSION_KILLED')

    expect(apiCache.get('sessions')).toBeNull()
    expect(apiCache.get('dashboard')).toBeNull()
    expect(apiCache.get('accounts')).toBe('data')
  })

  it('should do nothing for unknown events', () => {
    invalidateCacheForEvent('UNKNOWN_EVENT')

    expect(apiCache.get('accounts')).toBe('data')
    expect(apiCache.get('dashboard')).toBe('data')
    expect(apiCache.get('permissions')).toBe('data')
    expect(apiCache.get('sessions')).toBe('data')
  })
})
