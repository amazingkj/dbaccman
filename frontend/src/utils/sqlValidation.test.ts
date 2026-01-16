import { describe, it, expect } from 'vitest'
import {
  validateSqlInput,
  validateIdentifier,
  validatePassword,
  getWarningMessage,
} from './sqlValidation'

describe('validateSqlInput', () => {
  describe('valid inputs', () => {
    it('should return valid for empty input', () => {
      expect(validateSqlInput('')).toEqual({
        isValid: true,
        warnings: [],
        severity: 'none',
      })
    })

    it('should return valid for whitespace only', () => {
      expect(validateSqlInput('   ')).toEqual({
        isValid: true,
        warnings: [],
        severity: 'none',
      })
    })

    it('should return valid for normal text', () => {
      const result = validateSqlInput('normal_username')
      expect(result.isValid).toBe(true)
      expect(result.severity).toBe('none')
    })

    it('should return valid for SELECT statement', () => {
      const result = validateSqlInput('SELECT * FROM users')
      expect(result.isValid).toBe(true)
    })
  })

  describe('dangerous patterns (danger severity)', () => {
    it('should detect OR injection', () => {
      const result = validateSqlInput("' OR '1'='1")
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect AND injection', () => {
      const result = validateSqlInput("' AND '1'='1")
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect DROP statement', () => {
      const result = validateSqlInput('; DROP TABLE users')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect DELETE statement', () => {
      const result = validateSqlInput('; DELETE FROM users')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect UPDATE statement', () => {
      const result = validateSqlInput('; UPDATE users SET admin=1')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect INSERT statement', () => {
      const result = validateSqlInput('; INSERT INTO users VALUES')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect UNION SELECT', () => {
      const result = validateSqlInput('UNION SELECT * FROM passwords')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect SQL comment at end', () => {
      const result = validateSqlInput('admin-- ')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect block comments', () => {
      const result = validateSqlInput('admin /* comment */')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect EXEC function', () => {
      const result = validateSqlInput('EXEC(cmd)')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect EXECUTE function', () => {
      const result = validateSqlInput('EXECUTE(cmd)')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect xp_cmdshell', () => {
      const result = validateSqlInput('xp_cmdshell')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect hex encoded strings', () => {
      const result = validateSqlInput('0x414243')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect CHAR function', () => {
      const result = validateSqlInput('CHAR(65)')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect CONCAT function', () => {
      const result = validateSqlInput('CONCAT(a, b)')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect LOAD_FILE', () => {
      const result = validateSqlInput('LOAD_FILE(/etc/passwd)')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect INTO OUTFILE', () => {
      const result = validateSqlInput('INTO OUTFILE /tmp/file')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect INTO DUMPFILE', () => {
      const result = validateSqlInput('INTO DUMPFILE /tmp/file')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect BENCHMARK timing attack', () => {
      const result = validateSqlInput('BENCHMARK(1000000, SHA1(1))')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect SLEEP timing attack', () => {
      const result = validateSqlInput('SLEEP(5)')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should detect WAITFOR DELAY', () => {
      const result = validateSqlInput("WAITFOR DELAY '00:00:05'")
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })
  })

  describe('warning patterns', () => {
    it('should warn for single quote', () => {
      const result = validateSqlInput("user'name")
      expect(result.severity).toBe('warning')
      expect(result.isValid).toBe(true)
    })

    it('should warn for double quote', () => {
      const result = validateSqlInput('user"name')
      expect(result.severity).toBe('warning')
      expect(result.isValid).toBe(true)
    })

    it('should warn for semicolon', () => {
      const result = validateSqlInput('user;name')
      expect(result.severity).toBe('warning')
      expect(result.isValid).toBe(true)
    })

    it('should warn for backtick', () => {
      const result = validateSqlInput('user`name')
      expect(result.severity).toBe('warning')
      expect(result.isValid).toBe(true)
    })
  })
})

describe('validateIdentifier', () => {
  describe('valid identifiers', () => {
    it('should accept valid username', () => {
      const result = validateIdentifier('admin_user')
      expect(result.isValid).toBe(true)
      expect(result.severity).toBe('none')
    })

    it('should accept identifier starting with underscore', () => {
      const result = validateIdentifier('_private')
      expect(result.isValid).toBe(true)
    })

    it('should accept identifier with $', () => {
      const result = validateIdentifier('user$name')
      expect(result.isValid).toBe(true)
    })

    it('should accept identifier with @ prefix', () => {
      const result = validateIdentifier('@variable')
      expect(result.isValid).toBe(true)
    })

    it('should accept host-like patterns', () => {
      const result = validateIdentifier('localhost')
      expect(result.isValid).toBe(true)
    })

    it('should accept IP-like patterns', () => {
      const result = validateIdentifier('192.168.1.1')
      expect(result.isValid).toBe(true)
    })

    it('should accept % wildcard', () => {
      const result = validateIdentifier('%')
      expect(result.isValid).toBe(true)
    })
  })

  describe('invalid identifiers', () => {
    it('should reject empty identifier', () => {
      const result = validateIdentifier('')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should reject whitespace only', () => {
      const result = validateIdentifier('   ')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should reject SQL injection in identifier', () => {
      const result = validateIdentifier("; DROP TABLE users--")
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })
  })
})

describe('validatePassword', () => {
  describe('valid passwords', () => {
    it('should accept normal password', () => {
      const result = validatePassword('MyP@ssw0rd!')
      expect(result.isValid).toBe(true)
      expect(result.severity).toBe('none')
    })

    it('should accept password with special characters', () => {
      const result = validatePassword("P@ss'word\"123")
      expect(result.isValid).toBe(true)
    })

    it('should accept complex password', () => {
      const result = validatePassword('Compl3x!@#$%^&*()')
      expect(result.isValid).toBe(true)
    })
  })

  describe('invalid passwords', () => {
    it('should reject empty password', () => {
      const result = validatePassword('')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should reject DROP injection', () => {
      const result = validatePassword('; DROP TABLE users')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should reject DELETE injection', () => {
      const result = validatePassword('; DELETE FROM users')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should reject UNION SELECT', () => {
      const result = validatePassword('UNION SELECT * FROM')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })

    it('should reject SQL comment at end', () => {
      const result = validatePassword('password-- ')
      expect(result.isValid).toBe(false)
      expect(result.severity).toBe('danger')
    })
  })
})

describe('getWarningMessage', () => {
  it('should return null for no severity', () => {
    const result = { isValid: true, warnings: [], severity: 'none' as const }
    expect(getWarningMessage(result)).toBeNull()
  })

  it('should return danger message for danger severity', () => {
    const result = { isValid: false, warnings: ['test'], severity: 'danger' as const }
    const message = getWarningMessage(result)
    expect(message).toContain('Security Warning')
    expect(message).toContain('SQL injection')
  })

  it('should return warning message for warning severity', () => {
    const result = { isValid: true, warnings: ['test'], severity: 'warning' as const }
    const message = getWarningMessage(result)
    expect(message).toContain('special characters')
  })
})
