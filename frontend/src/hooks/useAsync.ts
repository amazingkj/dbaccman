import { useState, useCallback, useRef, useEffect } from 'react'

export interface UseAsyncState<T> {
  data: T | null
  error: Error | null
  isLoading: boolean
  isSuccess: boolean
  isError: boolean
}

export interface UseAsyncReturn<T, P extends unknown[]> extends UseAsyncState<T> {
  execute: (...params: P) => Promise<T | null>
  reset: () => void
}

export interface UseAsyncOptions<T> {
  immediate?: boolean
  onSuccess?: (data: T) => void
  onError?: (error: Error) => void
  initialParams?: unknown[]
}

/**
 * Custom hook for handling async operations with loading, error, and success states.
 *
 * @param asyncFunction - The async function to execute
 * @param options - Configuration options
 * @returns State and control functions for the async operation
 *
 * @example
 * ```tsx
 * const { data, isLoading, error, execute } = useAsync(fetchUsers)
 *
 * useEffect(() => {
 *   execute()
 * }, [execute])
 *
 * if (isLoading) return <Spin />
 * if (error) return <ErrorDisplay error={error} />
 * return <UserList users={data} />
 * ```
 */
export function useAsync<T, P extends unknown[] = []>(
  asyncFunction: (...params: P) => Promise<T>,
  options?: UseAsyncOptions<T>
): UseAsyncReturn<T, P> {
  const [state, setState] = useState<UseAsyncState<T>>({
    data: null,
    error: null,
    isLoading: false,
    isSuccess: false,
    isError: false,
  })

  // Track if component is mounted
  const mountedRef = useRef(true)
  const { immediate = false, onSuccess, onError, initialParams } = options || {}

  const execute = useCallback(
    async (...params: P): Promise<T | null> => {
      setState((prev) => ({
        ...prev,
        isLoading: true,
        error: null,
        isError: false,
      }))

      try {
        const result = await asyncFunction(...params)

        if (mountedRef.current) {
          setState({
            data: result,
            error: null,
            isLoading: false,
            isSuccess: true,
            isError: false,
          })
          onSuccess?.(result)
        }

        return result
      } catch (err) {
        const error = err instanceof Error ? err : new Error(String(err))

        if (mountedRef.current) {
          setState({
            data: null,
            error,
            isLoading: false,
            isSuccess: false,
            isError: true,
          })
          onError?.(error)
        }

        return null
      }
    },
    [asyncFunction, onSuccess, onError]
  )

  const reset = useCallback(() => {
    setState({
      data: null,
      error: null,
      isLoading: false,
      isSuccess: false,
      isError: false,
    })
  }, [])

  // Execute immediately if specified
  useEffect(() => {
    if (immediate) {
      execute(...((initialParams || []) as P))
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      mountedRef.current = false
    }
  }, [])

  return {
    ...state,
    execute,
    reset,
  }
}

export default useAsync
