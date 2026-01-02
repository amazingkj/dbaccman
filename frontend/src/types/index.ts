// Database Types
export type DatabaseType = 'MYSQL' | 'ORACLE' | 'POSTGRESQL'

export const DATABASE_TYPES: { value: DatabaseType; label: string; defaultPort: number }[] = [
  { value: 'MYSQL', label: 'MySQL', defaultPort: 3306 },
  { value: 'ORACLE', label: 'Oracle', defaultPort: 1521 },
  { value: 'POSTGRESQL', label: 'PostgreSQL', defaultPort: 5432 },
]

export const getDefaultPort = (dbType: DatabaseType): number => {
  const found = DATABASE_TYPES.find(t => t.value === dbType)
  return found?.defaultPort ?? 3306
}

// Auth Types
export interface LoginRequest {
  host: string
  port?: number
  username: string
  password: string
  dbType: DatabaseType
  database?: string
}

export interface LoginResponse {
  token: string
  username: string
  role: 'admin' | 'user'
  host: string
  port: number
  dbType: DatabaseType
  passwordExpiryDays: number | null
}

export interface User {
  username: string
  role: 'admin' | 'user'
  host: string
  port: number
  dbType: DatabaseType
}

export interface RecentConnection {
  host: string
  port: number
  username: string
  dbType: DatabaseType
  database?: string
  lastUsed: string
}

export interface PasswordExpiryInfo {
  username: string
  host: string
  daysUntilExpiry: number | null
  passwordLastChanged: string | null
  isExpired: boolean
}

// Account Types
export interface Account {
  username: string
  host: string
  created: string
  passwordLastChanged: string | null
  passwordLifetime: number | null
  accountLocked: boolean
}

export interface CreateAccountRequest {
  username: string
  host: string
  password: string
  expireDays: number
}

export interface ExpiringAccount {
  username: string
  host: string
  daysUntilExpiry: number
}

export interface SetTablespaceRequest {
  username: string
  host: string
  tablespace: string
  quota?: string  // e.g., "UNLIMITED", "100M", "1G"
}

// Permission Types
export interface Permission {
  grantee: string
  database: string
  table: string
  privilege: string
  isGrantable: boolean
}

export interface GrantPermissionRequest {
  username: string
  host: string
  database: string
  table: string
  privileges: string[]
}

// Session Types
export interface SessionInfo {
  pid: number
  serialNum?: number | null  // Oracle SERIAL# for kill session
  user: string
  host: string
  database: string | null
  command: string
  time: number
  state: string | null
  query: string | null
}

// Table Types
export interface DatabaseInfo {
  name: string
  tableCount: number
  totalRows: number
  size: number
}

export interface TableInfo {
  name: string
  engine: string
  rows: number
  size: number
  createTime: string
}

export interface IndexInfo {
  name: string
  columns: string[]
  unique: boolean
  type: string
}

// Dashboard Types
export interface DashboardStats {
  totalAccounts: number
  activeSessions: number
  expiringSoon: number
  slowQueries: number
  totalDatabases: number
  totalTables: number
  expiringAccounts: ExpiringAccount[]
  longRunningSessions: SessionInfo[]
  topDatabases: DatabaseInfo[]
}

// Tablespace Types
export interface TablespaceInfo {
  name: string
  spaceType: string
  fileSize: number
  allocatedSize: number
  state: string
  filePath?: string
}

export interface CreateTablespaceRequest {
  name: string
  dataFile?: string
  engine?: string
}

export interface TableLocationRequest {
  database: string
  tableName: string
  tablespaceName: string
}

// API Response Types
export interface ApiResponse<T> {
  data: T
  message?: string
}

export interface ApiError {
  error: string
  message: string
}