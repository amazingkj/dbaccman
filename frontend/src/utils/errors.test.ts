import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import {
  extractErrorMessage,
  createAppError,
  isApiErrorResponse,
  ErrorCodes,
  logError,
} from './errors'

// Mock antd message
vi.mock('antd', () => ({
  message: {
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
}))

describe('isApiErrorResponse', () => {
  it('should return true for valid ApiErrorResponse', () => {
    expect(isApiErrorResponse({ error: 'test error' })).toBe(true)
    expect(isApiErrorResponse({ error: 'test', message: 'details' })).toBe(true)
    expect(isApiErrorResponse({ error: 'test', code: 'ERR001' })).toBe(true)
  })

  it('should return false for invalid objects', () => {
    expect(isApiErrorResponse(null)).toBe(false)
    expect(isApiErrorResponse(undefined)).toBe(false)
    expect(isApiErrorResponse('string')).toBe(false)
    expect(isApiErrorResponse(123)).toBe(false)
    expect(isApiErrorResponse({})).toBe(false)
    expect(isApiErrorResponse({ message: 'no error field' })).toBe(false)
    expect(isApiErrorResponse({ error: 123 })).toBe(false)
  })
})

describe('extractErrorMessage', () => {
  describe('AxiosError handling', () => {
    const createAxiosError = (
      status?: number,
      data?: unknown,
      code?: string
    ): AxiosError => {
      const error = new AxiosError('Axios error')
      error.code = code
      if (status !== undefined) {
        error.response = {
          status,
          statusText: 'Error',
          headers: new AxiosHeaders(),
          config: { headers: new AxiosHeaders() },
          data,
        }
      }
      return error
    }

    it('should extract error from ApiErrorResponse', () => {
      const error = createAxiosError(400, { error: 'Validation failed' })
      expect(extractErrorMessage(error)).toBe('Validation failed')
    })

    it('should handle string response data', () => {
      const error = createAxiosError(400, 'Plain string error')
      expect(extractErrorMessage(error)).toBe('Plain string error')
    })

    it('should handle object with message property', () => {
      const error = createAxiosError(400, { message: 'Message from object' })
      expect(extractErrorMessage(error)).toBe('Message from object')
    })

    it('should handle network error', () => {
      const error = createAxiosError(undefined, undefined, 'ERR_NETWORK')
      expect(extractErrorMessage(error)).toBe('Network error. Please check your connection.')
    })

    it('should handle timeout error', () => {
      const error = createAxiosError(undefined, undefined, 'ECONNABORTED')
      expect(extractErrorMessage(error)).toBe('Request timed out. Please try again.')
    })

    it('should handle 400 status', () => {
      const error = createAxiosError(400)
      expect(extractErrorMessage(error)).toBe('Bad request. Please check your input.')
    })

    it('should handle 401 status', () => {
      const error = createAxiosError(401)
      expect(extractErrorMessage(error)).toBe('Session expired. Please log in again.')
    })

    it('should handle 403 status', () => {
      const error = createAxiosError(403)
      expect(extractErrorMessage(error)).toBe('Access denied. You do not have permission.')
    })

    it('should handle 404 status', () => {
      const error = createAxiosError(404)
      expect(extractErrorMessage(error)).toBe('Resource not found.')
    })

    it('should handle 500 status', () => {
      const error = createAxiosError(500)
      expect(extractErrorMessage(error)).toBe('Server error. Please try again later.')
    })

    it('should handle 502 status', () => {
      const error = createAxiosError(502)
      expect(extractErrorMessage(error)).toBe('Server is temporarily unavailable.')
    })

    it('should handle 503 status', () => {
      const error = createAxiosError(503)
      expect(extractErrorMessage(error)).toBe('Service unavailable. Please try again later.')
    })
  })

  describe('other error types', () => {
    it('should extract message from standard Error', () => {
      const error = new Error('Standard error message')
      expect(extractErrorMessage(error)).toBe('Standard error message')
    })

    it('should return string errors directly', () => {
      expect(extractErrorMessage('String error')).toBe('String error')
    })

    it('should return default message for unknown errors', () => {
      expect(extractErrorMessage(null)).toBe('An unexpected error occurred')
      expect(extractErrorMessage(undefined)).toBe('An unexpected error occurred')
      expect(extractErrorMessage(123)).toBe('An unexpected error occurred')
      expect(extractErrorMessage({})).toBe('An unexpected error occurred')
    })
  })
})

describe('createAppError', () => {
  const createAxiosError = (
    status?: number,
    data?: unknown,
    code?: string
  ): AxiosError => {
    const error = new AxiosError('Axios error')
    error.code = code
    if (status !== undefined) {
      error.response = {
        status,
        statusText: 'Error',
        headers: new AxiosHeaders(),
        config: { headers: new AxiosHeaders() },
        data,
      }
    }
    return error
  }

  it('should create AppError with NETWORK_ERROR code', () => {
    const error = createAxiosError(undefined, undefined, 'ERR_NETWORK')
    const appError = createAppError(error)

    expect(appError.code).toBe(ErrorCodes.NETWORK_ERROR)
    expect(appError.isNetworkError).toBe(true)
    expect(appError.isAuthError).toBe(false)
    expect(appError.isValidationError).toBe(false)
  })

  it('should create AppError with AUTH_ERROR code for 401', () => {
    const error = createAxiosError(401)
    const appError = createAppError(error)

    expect(appError.code).toBe(ErrorCodes.AUTH_ERROR)
    expect(appError.isAuthError).toBe(true)
  })

  it('should create AppError with CSRF_ERROR code for csrf related 403', () => {
    const error = createAxiosError(403, { error: 'CSRF token invalid' })
    const appError = createAppError(error)

    expect(appError.code).toBe(ErrorCodes.CSRF_ERROR)
  })

  it('should create AppError with FORBIDDEN code for non-csrf 403', () => {
    const error = createAxiosError(403, { error: 'Access denied' })
    const appError = createAppError(error)

    expect(appError.code).toBe(ErrorCodes.FORBIDDEN)
  })

  it('should create AppError with NOT_FOUND code for 404', () => {
    const error = createAxiosError(404)
    const appError = createAppError(error)

    expect(appError.code).toBe(ErrorCodes.NOT_FOUND)
  })

  it('should create AppError with VALIDATION_ERROR code for 400', () => {
    const error = createAxiosError(400)
    const appError = createAppError(error)

    expect(appError.code).toBe(ErrorCodes.VALIDATION_ERROR)
    expect(appError.isValidationError).toBe(true)
  })

  it('should create AppError with SERVER_ERROR code for 5xx', () => {
    const error500 = createAxiosError(500)
    const error502 = createAxiosError(502)
    const error503 = createAxiosError(503)

    expect(createAppError(error500).code).toBe(ErrorCodes.SERVER_ERROR)
    expect(createAppError(error502).code).toBe(ErrorCodes.SERVER_ERROR)
    expect(createAppError(error503).code).toBe(ErrorCodes.SERVER_ERROR)
  })

  it('should create AppError with UNKNOWN code for non-axios errors', () => {
    const error = new Error('Regular error')
    const appError = createAppError(error)

    expect(appError.code).toBe(ErrorCodes.UNKNOWN)
    expect(appError.originalError).toBe(error)
  })

  it('should preserve original error', () => {
    const originalError = new Error('Original')
    const appError = createAppError(originalError)

    expect(appError.originalError).toBe(originalError)
  })
})

describe('logError', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('should log error with context', () => {
    const error = new Error('Test error')
    logError('TestContext', error)

    expect(console.error).toHaveBeenCalledWith(
      '[TestContext] UNKNOWN: Test error',
      expect.objectContaining({ originalError: error })
    )
  })
})
