import { describe, it, expect } from 'vitest'
import {
  getPrivilegesForDbType,
  getObjectPrivilegesForDbType,
  getSystemPrivilegesForDbType,
  ORACLE_SYSTEM_PRIVILEGES,
  MYSQL_GLOBAL_PRIVILEGES,
  MYSQL_ALL_GLOBAL_PRIVILEGES,
  POSTGRESQL_PRIVILEGES,
  ORACLE_OBJECT_PRIVILEGES,
  POSTGRESQL_TABLE_PRIVILEGES,
  MYSQL_TABLE_PRIVILEGES,
} from './privileges'

describe('getPrivilegesForDbType', () => {
  it('should return Oracle system privileges with CREATE SESSION as default', () => {
    const result = getPrivilegesForDbType('ORACLE')
    expect(result.privileges).toEqual(ORACLE_SYSTEM_PRIVILEGES)
    expect(result.defaults).toEqual(['CREATE SESSION'])
  })

  it('should return Oracle privileges for lowercase input', () => {
    const result = getPrivilegesForDbType('oracle')
    expect(result.privileges).toEqual(ORACLE_SYSTEM_PRIVILEGES)
    expect(result.defaults).toEqual(['CREATE SESSION'])
  })

  it('should return MySQL global privileges with no defaults', () => {
    const result = getPrivilegesForDbType('MYSQL')
    expect(result.privileges).toEqual(MYSQL_GLOBAL_PRIVILEGES)
    expect(result.defaults).toEqual([])
  })

  it('should return MySQL privileges for lowercase input', () => {
    const result = getPrivilegesForDbType('mysql')
    expect(result.privileges).toEqual(MYSQL_GLOBAL_PRIVILEGES)
    expect(result.defaults).toEqual([])
  })

  it('should return PostgreSQL privileges with CONNECT as default', () => {
    const result = getPrivilegesForDbType('POSTGRESQL')
    expect(result.privileges).toEqual(POSTGRESQL_PRIVILEGES)
    expect(result.defaults).toEqual(['CONNECT'])
  })

  it('should return PostgreSQL privileges for lowercase input', () => {
    const result = getPrivilegesForDbType('postgresql')
    expect(result.privileges).toEqual(POSTGRESQL_PRIVILEGES)
    expect(result.defaults).toEqual(['CONNECT'])
  })

  it('should return empty arrays for unknown database type', () => {
    const result = getPrivilegesForDbType('UNKNOWN')
    expect(result.privileges).toEqual([])
    expect(result.defaults).toEqual([])
  })

  it('should return empty arrays for undefined database type', () => {
    const result = getPrivilegesForDbType(undefined)
    expect(result.privileges).toEqual([])
    expect(result.defaults).toEqual([])
  })
})

describe('getObjectPrivilegesForDbType', () => {
  it('should return Oracle object privileges', () => {
    const result = getObjectPrivilegesForDbType('ORACLE')
    expect(result).toEqual(ORACLE_OBJECT_PRIVILEGES)
    expect(result).toContain('SELECT')
    expect(result).toContain('EXECUTE')
  })

  it('should return PostgreSQL table privileges', () => {
    const result = getObjectPrivilegesForDbType('POSTGRESQL')
    expect(result).toEqual(POSTGRESQL_TABLE_PRIVILEGES)
    expect(result).toContain('TRUNCATE')
    expect(result).toContain('TRIGGER')
  })

  it('should return MySQL table privileges as default', () => {
    const result = getObjectPrivilegesForDbType('MYSQL')
    expect(result).toEqual(MYSQL_TABLE_PRIVILEGES)
    expect(result).toContain('CREATE VIEW')
    expect(result).toContain('SHOW VIEW')
  })

  it('should return MySQL table privileges for unknown types', () => {
    const result = getObjectPrivilegesForDbType('UNKNOWN')
    expect(result).toEqual(MYSQL_TABLE_PRIVILEGES)
  })

  it('should return MySQL table privileges for undefined', () => {
    const result = getObjectPrivilegesForDbType(undefined)
    expect(result).toEqual(MYSQL_TABLE_PRIVILEGES)
  })
})

describe('getSystemPrivilegesForDbType', () => {
  it('should return Oracle system privilege values', () => {
    const result = getSystemPrivilegesForDbType('ORACLE')
    expect(result).toContain('CREATE SESSION')
    expect(result).toContain('CREATE TABLE')
    expect(result).toContain('UNLIMITED TABLESPACE')
    expect(result.length).toBe(ORACLE_SYSTEM_PRIVILEGES.length)
  })

  it('should return MySQL global privileges for MySQL', () => {
    const result = getSystemPrivilegesForDbType('MYSQL')
    expect(result).toEqual(MYSQL_ALL_GLOBAL_PRIVILEGES)
    expect(result).toContain('SELECT')
    expect(result).toContain('CREATE USER')
  })

  it('should return empty array for PostgreSQL', () => {
    const result = getSystemPrivilegesForDbType('POSTGRESQL')
    expect(result).toEqual([])
  })

  it('should return empty array for unknown types', () => {
    const result = getSystemPrivilegesForDbType('UNKNOWN')
    expect(result).toEqual([])
  })
})

describe('Privilege constants', () => {
  it('Oracle system privileges should have required fields', () => {
    ORACLE_SYSTEM_PRIVILEGES.forEach(priv => {
      expect(priv).toHaveProperty('value')
      expect(priv).toHaveProperty('label')
      expect(priv).toHaveProperty('description')
      expect(typeof priv.value).toBe('string')
      expect(typeof priv.label).toBe('string')
      expect(typeof priv.description).toBe('string')
    })
  })

  it('MySQL global privileges should have required fields', () => {
    MYSQL_GLOBAL_PRIVILEGES.forEach(priv => {
      expect(priv).toHaveProperty('value')
      expect(priv).toHaveProperty('label')
      expect(priv).toHaveProperty('description')
    })
  })

  it('PostgreSQL privileges should have required fields', () => {
    POSTGRESQL_PRIVILEGES.forEach(priv => {
      expect(priv).toHaveProperty('value')
      expect(priv).toHaveProperty('label')
      expect(priv).toHaveProperty('description')
    })
  })

  it('Oracle object privileges should be non-empty string array', () => {
    expect(ORACLE_OBJECT_PRIVILEGES.length).toBeGreaterThan(0)
    ORACLE_OBJECT_PRIVILEGES.forEach(priv => {
      expect(typeof priv).toBe('string')
      expect(priv.length).toBeGreaterThan(0)
    })
  })

  it('PostgreSQL table privileges should be non-empty string array', () => {
    expect(POSTGRESQL_TABLE_PRIVILEGES.length).toBeGreaterThan(0)
    POSTGRESQL_TABLE_PRIVILEGES.forEach(priv => {
      expect(typeof priv).toBe('string')
    })
  })

  it('MySQL table privileges should be non-empty string array', () => {
    expect(MYSQL_TABLE_PRIVILEGES.length).toBeGreaterThan(0)
    MYSQL_TABLE_PRIVILEGES.forEach(priv => {
      expect(typeof priv).toBe('string')
    })
  })
})
