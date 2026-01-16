import { useState, useCallback, useMemo } from 'react'
import {
  validateSqlInput,
  validateIdentifier,
  validatePassword,
  type SqlValidationResult,
} from '../utils/sqlValidation'

type ValidationType = 'sql' | 'identifier' | 'password'

interface UseInputValidationOptions {
  type?: ValidationType
  debounceMs?: number
}

interface UseInputValidationReturn {
  validation: SqlValidationResult | null
  validateInput: (value: string) => SqlValidationResult
  clearValidation: () => void
  hasWarning: boolean
  hasDanger: boolean
}

/**
 * Hook for validating user inputs for SQL injection patterns
 */
export function useInputValidation(
  options: UseInputValidationOptions = {}
): UseInputValidationReturn {
  const { type = 'sql' } = options
  const [validation, setValidation] = useState<SqlValidationResult | null>(null)

  const validateInput = useCallback(
    (value: string): SqlValidationResult => {
      let result: SqlValidationResult

      switch (type) {
        case 'identifier':
          result = validateIdentifier(value)
          break
        case 'password':
          result = validatePassword(value)
          break
        case 'sql':
        default:
          result = validateSqlInput(value)
          break
      }

      setValidation(result)
      return result
    },
    [type]
  )

  const clearValidation = useCallback(() => {
    setValidation(null)
  }, [])

  const hasWarning = useMemo(
    () => validation?.severity === 'warning',
    [validation]
  )

  const hasDanger = useMemo(
    () => validation?.severity === 'danger',
    [validation]
  )

  return {
    validation,
    validateInput,
    clearValidation,
    hasWarning,
    hasDanger,
  }
}

/**
 * Hook for validating multiple inputs in a form
 */
export function useFormValidation() {
  const [validations, setValidations] = useState<Record<string, SqlValidationResult>>({})

  const validateField = useCallback(
    (
      fieldName: string,
      value: string,
      type: ValidationType = 'sql'
    ): SqlValidationResult => {
      let result: SqlValidationResult

      switch (type) {
        case 'identifier':
          result = validateIdentifier(value)
          break
        case 'password':
          result = validatePassword(value)
          break
        case 'sql':
        default:
          result = validateSqlInput(value)
          break
      }

      setValidations(prev => ({
        ...prev,
        [fieldName]: result,
      }))

      return result
    },
    []
  )

  const clearFieldValidation = useCallback((fieldName: string) => {
    setValidations(prev => {
      const newState = { ...prev }
      delete newState[fieldName]
      return newState
    })
  }, [])

  const clearAllValidations = useCallback(() => {
    setValidations({})
  }, [])

  const hasAnyWarning = useMemo(
    () => Object.values(validations).some(v => v.severity === 'warning'),
    [validations]
  )

  const hasAnyDanger = useMemo(
    () => Object.values(validations).some(v => v.severity === 'danger'),
    [validations]
  )

  const getFieldValidation = useCallback(
    (fieldName: string): SqlValidationResult | null => {
      return validations[fieldName] || null
    },
    [validations]
  )

  return {
    validations,
    validateField,
    clearFieldValidation,
    clearAllValidations,
    hasAnyWarning,
    hasAnyDanger,
    getFieldValidation,
  }
}

export default useInputValidation
