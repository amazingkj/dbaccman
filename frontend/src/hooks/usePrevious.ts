import { useRef, useEffect } from 'react'

/**
 * Returns the previous value of the given variable.
 * Useful for comparing current and previous values in effects.
 *
 * @param value - The current value to track
 * @returns The previous value (undefined on first render)
 *
 * @example
 * ```tsx
 * const [count, setCount] = useState(0)
 * const prevCount = usePrevious(count)
 *
 * useEffect(() => {
 *   if (prevCount !== undefined && count !== prevCount) {
 *     console.log(`Count changed from ${prevCount} to ${count}`)
 *   }
 * }, [count, prevCount])
 * ```
 */
export function usePrevious<T>(value: T): T | undefined {
  const ref = useRef<T>()

  useEffect(() => {
    ref.current = value
  }, [value])

  return ref.current
}

export default usePrevious
