import { KeyboardEvent, RefObject, useEffect } from 'react'

/**
 * ARIA label constants for consistent accessibility labels.
 */
export const ariaLabels = {
  // Navigation
  mainNav: 'Main navigation',
  userMenu: 'User menu',
  sidebar: 'Sidebar navigation',

  // Actions
  close: 'Close',
  refresh: 'Refresh',
  delete: 'Delete',
  edit: 'Edit',
  save: 'Save',
  cancel: 'Cancel',
  search: 'Search',
  filter: 'Filter',
  sort: 'Sort',
  expand: 'Expand',
  collapse: 'Collapse',

  // Tables
  sortAscending: (column: string) => `Sort by ${column} ascending`,
  sortDescending: (column: string) => `Sort by ${column} descending`,
  selectRow: (item: string) => `Select ${item}`,
  selectAllRows: 'Select all rows',

  // Forms
  required: (field: string) => `${field} is required`,
  optional: (field: string) => `${field} (optional)`,

  // Status
  loading: 'Loading...',
  error: (message: string) => `Error: ${message}`,
  success: (message: string) => `Success: ${message}`,

  // Session
  sessionTimeout: 'Session timeout warning',
  sessionCountdown: (time: string) => `Session expires in ${time}`,
}

/**
 * Keyboard navigation handler interface.
 */
export interface KeyboardHandlers {
  onEnter?: () => void
  onEscape?: () => void
  onArrowUp?: () => void
  onArrowDown?: () => void
  onArrowLeft?: () => void
  onArrowRight?: () => void
  onTab?: () => void
  onSpace?: () => void
}

/**
 * Handles keyboard navigation events.
 *
 * @param event - The keyboard event
 * @param handlers - Object containing handler functions for each key
 *
 * @example
 * ```tsx
 * <div
 *   onKeyDown={(e) => handleKeyboardNavigation(e, {
 *     onEnter: () => console.log('Enter pressed'),
 *     onEscape: () => closeModal(),
 *   })}
 * >
 * ```
 */
export function handleKeyboardNavigation(
  event: KeyboardEvent,
  handlers: KeyboardHandlers
): void {
  const { key } = event

  switch (key) {
    case 'Enter':
      handlers.onEnter?.()
      break
    case 'Escape':
      handlers.onEscape?.()
      break
    case 'ArrowUp':
      handlers.onArrowUp?.()
      event.preventDefault()
      break
    case 'ArrowDown':
      handlers.onArrowDown?.()
      event.preventDefault()
      break
    case 'ArrowLeft':
      handlers.onArrowLeft?.()
      break
    case 'ArrowRight':
      handlers.onArrowRight?.()
      break
    case 'Tab':
      handlers.onTab?.()
      break
    case ' ':
      handlers.onSpace?.()
      break
  }
}

/**
 * Focuses an element by selector.
 *
 * @param selector - CSS selector for the element to focus
 */
export function focusElement(selector: string): void {
  const element = document.querySelector<HTMLElement>(selector)
  element?.focus()
}

/**
 * Gets all focusable elements within a container.
 *
 * @param container - The container element
 * @returns Array of focusable elements
 */
export function getFocusableElements(container: HTMLElement): HTMLElement[] {
  const selector =
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
  return Array.from(container.querySelectorAll<HTMLElement>(selector))
}

/**
 * Creates a focus trap within a container element.
 * Useful for modals and dialogs.
 *
 * @param containerRef - React ref to the container element
 * @returns Cleanup function
 *
 * @example
 * ```tsx
 * useEffect(() => {
 *   return trapFocus(modalRef)
 * }, [])
 * ```
 */
export function trapFocus(containerRef: RefObject<HTMLElement>): () => void {
  const container = containerRef.current
  if (!container) return () => {}

  const focusableElements = getFocusableElements(container)

  if (focusableElements.length === 0) return () => {}

  const firstElement = focusableElements[0]
  const lastElement = focusableElements[focusableElements.length - 1]

  const handleTab = (e: globalThis.KeyboardEvent) => {
    if (e.key !== 'Tab') return

    if (e.shiftKey) {
      if (document.activeElement === firstElement) {
        e.preventDefault()
        lastElement.focus()
      }
    } else {
      if (document.activeElement === lastElement) {
        e.preventDefault()
        firstElement.focus()
      }
    }
  }

  container.addEventListener('keydown', handleTab)
  firstElement.focus()

  return () => container.removeEventListener('keydown', handleTab)
}

/**
 * Hook to trap focus within a container.
 *
 * @param containerRef - React ref to the container element
 * @param isActive - Whether the focus trap is active
 *
 * @example
 * ```tsx
 * const modalRef = useRef<HTMLDivElement>(null)
 * useFocusTrap(modalRef, isModalOpen)
 * ```
 */
export function useFocusTrap(
  containerRef: RefObject<HTMLElement>,
  isActive: boolean
): void {
  useEffect(() => {
    if (!isActive) return

    return trapFocus(containerRef)
  }, [containerRef, isActive])
}

/**
 * Announces a message to screen readers using an ARIA live region.
 *
 * @param message - The message to announce
 * @param priority - 'polite' or 'assertive'
 */
export function announceToScreenReader(
  message: string,
  priority: 'polite' | 'assertive' = 'polite'
): void {
  const announcement = document.createElement('div')
  announcement.setAttribute('aria-live', priority)
  announcement.setAttribute('aria-atomic', 'true')
  announcement.setAttribute(
    'style',
    'position: absolute; left: -10000px; width: 1px; height: 1px; overflow: hidden;'
  )
  announcement.textContent = message

  document.body.appendChild(announcement)

  // Remove after announcement
  setTimeout(() => {
    document.body.removeChild(announcement)
  }, 1000)
}

/**
 * Generates a unique ID for accessibility purposes.
 *
 * @param prefix - Optional prefix for the ID
 * @returns A unique ID string
 */
export function generateId(prefix = 'a11y'): string {
  return `${prefix}-${Math.random().toString(36).substring(2, 9)}`
}
