import { AxiosError } from 'axios'
import { message } from 'antd'

/**
 * Standard API error response structure from the backend.
 */
export interface ApiErrorResponse {
  error: string
  message?: string
  code?: string
  details?: Record<string, unknown>
}

/**
 * Structured error object for application use.
 */
export interface AppError {
  message: string
  code?: string
  originalError?: unknown
  isNetworkError: boolean
  isAuthError: boolean
  isValidationError: boolean
}

/**
 * Error code constants for consistent error handling.
 */
export const ErrorCodes = {
  NETWORK_ERROR: 'NETWORK_ERROR',
  AUTH_ERROR: 'AUTH_ERROR',
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  SERVER_ERROR: 'SERVER_ERROR',
  NOT_FOUND: 'NOT_FOUND',
  FORBIDDEN: 'FORBIDDEN',
  CSRF_ERROR: 'CSRF_ERROR',
  UNKNOWN: 'UNKNOWN',
} as const

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes]

/**
 * Type guard to check if an object is an ApiErrorResponse.
 */
export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  return (
    typeof value === 'object' &&
    value !== null &&
    'error' in value &&
    typeof (value as Record<string, unknown>).error === 'string'
  )
}

/**
 * Extracts a user-friendly error message from various error types.
 *
 * @param error - The error to extract message from
 * @returns A human-readable error message
 *
 * @example
 * ```tsx
 * catch (error) {
 *   const errorMessage = extractErrorMessage(error)
 *   message.error(errorMessage)
 * }
 * ```
 */
export function extractErrorMessage(error: unknown): string {
  // Axios error
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiErrorResponse | string | undefined

    // API error response object
    if (isApiErrorResponse(data)) {
      return data.error
    }

    // String error message
    if (typeof data === 'string') {
      return data
    }

    // Object with message property
    if (typeof data === 'object' && data !== null && 'message' in data) {
      return String((data as Record<string, unknown>).message)
    }

    // Network error
    if (error.code === 'ERR_NETWORK') {
      return 'Network error. Please check your connection.'
    }

    // Timeout
    if (error.code === 'ECONNABORTED') {
      return 'Request timed out. Please try again.'
    }

    // HTTP status messages
    if (error.response?.status) {
      switch (error.response.status) {
        case 400:
          return 'Bad request. Please check your input.'
        case 401:
          return 'Session expired. Please log in again.'
        case 403:
          return 'Access denied. You do not have permission.'
        case 404:
          return 'Resource not found.'
        case 500:
          return 'Server error. Please try again later.'
        case 502:
          return 'Server is temporarily unavailable.'
        case 503:
          return 'Service unavailable. Please try again later.'
      }
    }

    return error.message || 'An error occurred'
  }

  // Standard Error
  if (error instanceof Error) {
    return error.message
  }

  // String error
  if (typeof error === 'string') {
    return error
  }

  // Unknown
  return 'An unexpected error occurred'
}

/**
 * Creates a structured AppError from any error.
 *
 * @param error - The error to convert
 * @returns A structured AppError object
 */
export function createAppError(error: unknown): AppError {
  const errorMessage = extractErrorMessage(error)

  let code: ErrorCode = ErrorCodes.UNKNOWN
  const isAxiosError = error instanceof AxiosError

  if (isAxiosError) {
    if (error.code === 'ERR_NETWORK') {
      code = ErrorCodes.NETWORK_ERROR
    } else if (error.response?.status === 401) {
      code = ErrorCodes.AUTH_ERROR
    } else if (error.response?.status === 403) {
      // Check if it's a CSRF error
      const data = error.response?.data
      if (
        isApiErrorResponse(data) &&
        data.error.toLowerCase().includes('csrf')
      ) {
        code = ErrorCodes.CSRF_ERROR
      } else {
        code = ErrorCodes.FORBIDDEN
      }
    } else if (error.response?.status === 404) {
      code = ErrorCodes.NOT_FOUND
    } else if (error.response?.status === 400) {
      code = ErrorCodes.VALIDATION_ERROR
    } else if (error.response?.status && error.response.status >= 500) {
      code = ErrorCodes.SERVER_ERROR
    }
  }

  return {
    message: errorMessage,
    code,
    originalError: error,
    isNetworkError: code === ErrorCodes.NETWORK_ERROR,
    isAuthError: code === ErrorCodes.AUTH_ERROR,
    isValidationError: code === ErrorCodes.VALIDATION_ERROR,
  }
}

/**
 * Handles an API error with optional toast message display.
 *
 * @param error - The error to handle
 * @param options - Configuration options
 * @returns The structured AppError
 *
 * @example
 * ```tsx
 * catch (error) {
 *   handleApiError(error, { defaultMessage: 'Failed to save account' })
 * }
 * ```
 */
export function handleApiError(
  error: unknown,
  options?: {
    showMessage?: boolean
    defaultMessage?: string
    messageType?: 'error' | 'warning' | 'info'
  }
): AppError {
  const appError = createAppError(error)
  const {
    showMessage = true,
    defaultMessage,
    messageType = 'error',
  } = options || {}

  if (showMessage) {
    const displayMessage = defaultMessage || appError.message
    switch (messageType) {
      case 'warning':
        message.warning(displayMessage)
        break
      case 'info':
        message.info(displayMessage)
        break
      default:
        message.error(displayMessage)
    }
  }

  return appError
}

/**
 * Logs an error to the console with structured information.
 * Can be extended to send to error monitoring services.
 *
 * @param context - A description of where the error occurred
 * @param error - The error to log
 */
export function logError(context: string, error: unknown): void {
  const appError = createAppError(error)
  console.error(`[${context}] ${appError.code}: ${appError.message}`, {
    originalError: appError.originalError,
  })
}
