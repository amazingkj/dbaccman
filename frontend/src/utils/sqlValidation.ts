/**
 * SQL Injection Prevention Utilities
 * Provides client-side validation and warnings for potentially dangerous SQL patterns
 */

// Dangerous SQL patterns that might indicate SQL injection attempts
const DANGEROUS_PATTERNS = [
  /['"].*?OR.*?['"].*?[=]/i,           // ' OR '1'='1
  /['"].*?AND.*?['"].*?[=]/i,          // ' AND '1'='1
  /;\s*DROP\s+/i,                       // ; DROP
  /;\s*DELETE\s+/i,                     // ; DELETE
  /;\s*UPDATE\s+/i,                     // ; UPDATE
  /;\s*INSERT\s+/i,                     // ; INSERT
  /UNION\s+SELECT/i,                    // UNION SELECT
  /--\s*$/,                             // SQL comment at end
  /\/\*.*?\*\//,                        // Block comments
  /EXEC\s*\(/i,                         // EXEC()
  /EXECUTE\s*\(/i,                      // EXECUTE()
  /xp_cmdshell/i,                       // xp_cmdshell
  /0x[0-9a-f]+/i,                       // Hex encoded strings
  /CHAR\s*\(\s*\d+\s*\)/i,             // CHAR() function
  /CONCAT\s*\(/i,                       // CONCAT() - often used in injection
  /LOAD_FILE\s*\(/i,                    // LOAD_FILE()
  /INTO\s+OUTFILE/i,                    // INTO OUTFILE
  /INTO\s+DUMPFILE/i,                   // INTO DUMPFILE
  /BENCHMARK\s*\(/i,                    // BENCHMARK() - timing attack
  /SLEEP\s*\(/i,                        // SLEEP() - timing attack
  /WAITFOR\s+DELAY/i,                   // WAITFOR DELAY - SQL Server timing
]

// Characters that should be escaped/handled carefully in SQL identifiers
const SPECIAL_CHARS_PATTERN = /[`'";\-\-\/\*]/

export interface SqlValidationResult {
  isValid: boolean
  warnings: string[]
  severity: 'none' | 'warning' | 'danger'
}

/**
 * Validates input for potentially dangerous SQL patterns
 */
export function validateSqlInput(input: string): SqlValidationResult {
  if (!input || input.trim() === '') {
    return { isValid: true, warnings: [], severity: 'none' }
  }

  const warnings: string[] = []
  let severity: 'none' | 'warning' | 'danger' = 'none'

  // Check for dangerous patterns
  for (const pattern of DANGEROUS_PATTERNS) {
    if (pattern.test(input)) {
      warnings.push('Potentially dangerous SQL pattern detected')
      severity = 'danger'
      break
    }
  }

  // Check for special characters that might be suspicious
  if (severity === 'none' && SPECIAL_CHARS_PATTERN.test(input)) {
    warnings.push('Special characters detected in input')
    severity = 'warning'
  }

  return {
    isValid: severity !== 'danger',
    warnings,
    severity,
  }
}

/**
 * Validates a database identifier (username, database name, table name, etc.)
 * More strict validation for identifiers
 */
export function validateIdentifier(identifier: string): SqlValidationResult {
  if (!identifier || identifier.trim() === '') {
    return { isValid: false, warnings: ['Identifier cannot be empty'], severity: 'danger' }
  }

  const warnings: string[] = []
  let severity: 'none' | 'warning' | 'danger' = 'none'

  // Valid identifier pattern: starts with letter or underscore, followed by letters, numbers, underscore, $
  // Also allow @ prefix for MySQL host (e.g., 'user'@'%')
  const validIdentifierPattern = /^[@%]?[a-zA-Z_][a-zA-Z0-9_$]*$/

  // Check if it looks like a valid identifier
  if (!validIdentifierPattern.test(identifier) && !identifier.includes('@')) {
    // Allow quoted identifiers for hosts like 'localhost', '192.168.1.%'
    const quotedOrHostPattern = /^[a-zA-Z0-9._\-%]+$/
    if (!quotedOrHostPattern.test(identifier)) {
      warnings.push('Invalid characters in identifier')
      severity = 'warning'
    }
  }

  // Run through dangerous pattern check too
  const sqlCheck = validateSqlInput(identifier)
  if (sqlCheck.severity === 'danger') {
    return sqlCheck
  }

  // At this point, severity can only be 'none' or 'warning', so always valid
  return {
    isValid: true,
    warnings,
    severity,
  }
}

/**
 * Validates a password - less strict but still checks for SQL injection
 */
export function validatePassword(password: string): SqlValidationResult {
  if (!password) {
    return { isValid: false, warnings: ['Password cannot be empty'], severity: 'danger' }
  }

  // Only check for the most dangerous patterns in passwords
  // Users might legitimately have special chars in passwords
  const dangerousPasswordPatterns = [
    /;\s*DROP\s+/i,
    /;\s*DELETE\s+/i,
    /UNION\s+SELECT/i,
    /--\s*$/,
  ]

  for (const pattern of dangerousPasswordPatterns) {
    if (pattern.test(password)) {
      return {
        isValid: false,
        warnings: ['Password contains potentially dangerous SQL pattern'],
        severity: 'danger',
      }
    }
  }

  return { isValid: true, warnings: [], severity: 'none' }
}

/**
 * Gets a user-friendly warning message based on validation result
 */
export function getWarningMessage(result: SqlValidationResult): string | null {
  if (result.severity === 'none') {
    return null
  }

  if (result.severity === 'danger') {
    return 'Security Warning: This input contains patterns that could be used for SQL injection attacks. Please check your input.'
  }

  if (result.severity === 'warning') {
    return 'Warning: This input contains special characters. Make sure this is intentional.'
  }

  return null
}
