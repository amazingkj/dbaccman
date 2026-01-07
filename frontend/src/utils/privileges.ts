// Oracle system privileges for account creation
export const ORACLE_SYSTEM_PRIVILEGES = [
  { value: 'CREATE SESSION', label: 'CREATE SESSION (Login)', description: 'Required to connect to database' },
  { value: 'CREATE TABLE', label: 'CREATE TABLE', description: 'Create tables in own schema' },
  { value: 'CREATE VIEW', label: 'CREATE VIEW', description: 'Create views in own schema' },
  { value: 'CREATE PROCEDURE', label: 'CREATE PROCEDURE', description: 'Create stored procedures' },
  { value: 'CREATE SEQUENCE', label: 'CREATE SEQUENCE', description: 'Create sequences' },
  { value: 'UNLIMITED TABLESPACE', label: 'UNLIMITED TABLESPACE', description: 'Use unlimited storage' },
]

// MySQL global privileges for account creation
export const MYSQL_GLOBAL_PRIVILEGES = [
  { value: 'SELECT', label: 'SELECT', description: 'Read data from tables' },
  { value: 'INSERT', label: 'INSERT', description: 'Insert data into tables' },
  { value: 'UPDATE', label: 'UPDATE', description: 'Modify existing data' },
  { value: 'DELETE', label: 'DELETE', description: 'Delete data from tables' },
  { value: 'CREATE', label: 'CREATE', description: 'Create databases and tables' },
  { value: 'DROP', label: 'DROP', description: 'Drop databases and tables' },
  { value: 'INDEX', label: 'INDEX', description: 'Create and drop indexes' },
  { value: 'ALTER', label: 'ALTER', description: 'Alter table structure' },
]

// PostgreSQL privileges for account creation
export const POSTGRESQL_PRIVILEGES = [
  { value: 'CONNECT', label: 'CONNECT', description: 'Connect to database' },
  { value: 'CREATE', label: 'CREATE', description: 'Create new schemas/objects' },
  { value: 'TEMPORARY', label: 'TEMPORARY', description: 'Create temporary tables' },
]

export interface PrivilegeOption {
  value: string
  label: string
  description: string
}

export interface PrivilegesConfig {
  privileges: PrivilegeOption[]
  defaults: string[]
}

// Get privileges based on database type
export function getPrivilegesForDbType(dbType: string | undefined): PrivilegesConfig {
  switch (dbType?.toUpperCase()) {
    case 'ORACLE':
      return { privileges: ORACLE_SYSTEM_PRIVILEGES, defaults: ['CREATE SESSION'] }
    case 'MYSQL':
      return { privileges: MYSQL_GLOBAL_PRIVILEGES, defaults: [] }
    case 'POSTGRESQL':
      return { privileges: POSTGRESQL_PRIVILEGES, defaults: ['CONNECT'] }
    default:
      return { privileges: [], defaults: [] }
  }
}

// Oracle object privileges (for Permissions page)
export const ORACLE_OBJECT_PRIVILEGES = [
  'SELECT',
  'INSERT',
  'UPDATE',
  'DELETE',
  'ALTER',
  'INDEX',
  'EXECUTE',
  'REFERENCES',
]

// PostgreSQL schema privileges (for Permissions page)
export const POSTGRESQL_SCHEMA_PRIVILEGES = [
  'USAGE',
  'CREATE',
]

// PostgreSQL table privileges (for Permissions page)
export const POSTGRESQL_TABLE_PRIVILEGES = [
  'SELECT',
  'INSERT',
  'UPDATE',
  'DELETE',
  'TRUNCATE',
  'REFERENCES',
  'TRIGGER',
]

// PostgreSQL all privileges (schema + table)
export const POSTGRESQL_ALL_PRIVILEGES = [
  ...POSTGRESQL_SCHEMA_PRIVILEGES,
  ...POSTGRESQL_TABLE_PRIVILEGES,
]

// MySQL table privileges (for Permissions page)
export const MYSQL_TABLE_PRIVILEGES = [
  'SELECT',
  'INSERT',
  'UPDATE',
  'DELETE',
  'CREATE',
  'DROP',
  'INDEX',
  'ALTER',
  'CREATE VIEW',
  'SHOW VIEW',
  'CREATE ROUTINE',
  'ALTER ROUTINE',
  'EXECUTE',
  'TRIGGER',
  'REFERENCES',
]

// Get object/table privileges based on database type
export function getObjectPrivilegesForDbType(dbType: string | undefined): string[] {
  switch (dbType?.toUpperCase()) {
    case 'ORACLE':
      return ORACLE_OBJECT_PRIVILEGES
    case 'POSTGRESQL':
      return POSTGRESQL_TABLE_PRIVILEGES
    default:
      return MYSQL_TABLE_PRIVILEGES
  }
}

// Get system privileges for grant modal (for Permissions page)
export function getSystemPrivilegesForDbType(dbType: string | undefined): string[] {
  switch (dbType?.toUpperCase()) {
    case 'ORACLE':
      return ORACLE_SYSTEM_PRIVILEGES.map(p => p.value)
    default:
      return []
  }
}
