import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useAsync } from './useAsync'

describe('useAsync', () => {

  describe('initial state', () => {
    it('should have correct initial state', () => {
      const asyncFn = vi.fn()
      const { result } = renderHook(() => useAsync(asyncFn))

      expect(result.current.data).toBeNull()
      expect(result.current.error).toBeNull()
      expect(result.current.isLoading).toBe(false)
      expect(result.current.isSuccess).toBe(false)
      expect(result.current.isError).toBe(false)
    })
  })

  describe('execute', () => {
    it('should set loading state during execution', async () => {
      let resolvePromise: (value: string) => void
      const asyncFn = vi.fn(() => new Promise<string>((resolve) => {
        resolvePromise = resolve
      }))

      const { result } = renderHook(() => useAsync(asyncFn))

      act(() => {
        result.current.execute()
      })

      expect(result.current.isLoading).toBe(true)

      await act(async () => {
        resolvePromise!('test data')
      })

      expect(result.current.isLoading).toBe(false)
    })

    it('should set data on successful execution', async () => {
      const testData = { id: 1, name: 'test' }
      const asyncFn = vi.fn().mockResolvedValue(testData)

      const { result } = renderHook(() => useAsync(asyncFn))

      await act(async () => {
        await result.current.execute()
      })

      expect(result.current.data).toEqual(testData)
      expect(result.current.isSuccess).toBe(true)
      expect(result.current.isError).toBe(false)
      expect(result.current.error).toBeNull()
    })

    it('should set error on failed execution', async () => {
      const testError = new Error('Test error')
      const asyncFn = vi.fn().mockRejectedValue(testError)

      const { result } = renderHook(() => useAsync(asyncFn))

      await act(async () => {
        await result.current.execute()
      })

      expect(result.current.data).toBeNull()
      expect(result.current.isSuccess).toBe(false)
      expect(result.current.isError).toBe(true)
      expect(result.current.error).toEqual(testError)
    })

    it('should convert non-Error throws to Error', async () => {
      const asyncFn = vi.fn().mockRejectedValue('string error')

      const { result } = renderHook(() => useAsync(asyncFn))

      await act(async () => {
        await result.current.execute()
      })

      expect(result.current.error).toBeInstanceOf(Error)
      expect(result.current.error?.message).toBe('string error')
    })

    it('should pass parameters to async function', async () => {
      const asyncFn = vi.fn((a: number, b: string) => Promise.resolve(`${a}-${b}`))

      const { result } = renderHook(() => useAsync(asyncFn))

      await act(async () => {
        await result.current.execute(42, 'test')
      })

      expect(asyncFn).toHaveBeenCalledWith(42, 'test')
      expect(result.current.data).toBe('42-test')
    })

    it('should return data on success', async () => {
      const testData = 'success'
      const asyncFn = vi.fn().mockResolvedValue(testData)

      const { result } = renderHook(() => useAsync(asyncFn))

      let returnValue: string | null = null
      await act(async () => {
        returnValue = await result.current.execute()
      })

      expect(returnValue).toBe(testData)
    })

    it('should return null on error', async () => {
      const asyncFn = vi.fn().mockRejectedValue(new Error('error'))

      const { result } = renderHook(() => useAsync(asyncFn))

      let returnValue: unknown = 'not null'
      await act(async () => {
        returnValue = await result.current.execute()
      })

      expect(returnValue).toBeNull()
    })
  })

  describe('callbacks', () => {
    it('should call onSuccess callback on success', async () => {
      const testData = { success: true }
      const asyncFn = vi.fn().mockResolvedValue(testData)
      const onSuccess = vi.fn()

      const { result } = renderHook(() => useAsync(asyncFn, { onSuccess }))

      await act(async () => {
        await result.current.execute()
      })

      expect(onSuccess).toHaveBeenCalledWith(testData)
    })

    it('should call onError callback on error', async () => {
      const testError = new Error('Test error')
      const asyncFn = vi.fn().mockRejectedValue(testError)
      const onError = vi.fn()

      const { result } = renderHook(() => useAsync(asyncFn, { onError }))

      await act(async () => {
        await result.current.execute()
      })

      expect(onError).toHaveBeenCalledWith(testError)
    })
  })

  describe('immediate execution', () => {
    it('should execute immediately when immediate option is true', async () => {
      const asyncFn = vi.fn().mockResolvedValue('immediate')

      const { result } = renderHook(() => useAsync(asyncFn, { immediate: true }))

      // Wait for the async function to complete
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0))
      })

      expect(asyncFn).toHaveBeenCalled()
      expect(result.current.data).toBe('immediate')
    })

    it('should pass initialParams when executing immediately', async () => {
      const asyncFn = vi.fn((x: number) => Promise.resolve(x * 2))

      const { result } = renderHook(() =>
        useAsync(asyncFn, {
          immediate: true,
          initialParams: [5],
        })
      )

      // Wait for the async function to complete
      await act(async () => {
        await new Promise(resolve => setTimeout(resolve, 0))
      })

      expect(asyncFn).toHaveBeenCalledWith(5)
      expect(result.current.data).toBe(10)
    })
  })

  describe('reset', () => {
    it('should reset state to initial values', async () => {
      const asyncFn = vi.fn().mockResolvedValue('data')

      const { result } = renderHook(() => useAsync(asyncFn))

      await act(async () => {
        await result.current.execute()
      })

      expect(result.current.data).toBe('data')
      expect(result.current.isSuccess).toBe(true)

      act(() => {
        result.current.reset()
      })

      expect(result.current.data).toBeNull()
      expect(result.current.error).toBeNull()
      expect(result.current.isLoading).toBe(false)
      expect(result.current.isSuccess).toBe(false)
      expect(result.current.isError).toBe(false)
    })
  })
})
