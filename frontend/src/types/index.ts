// Auth Types
export interface LoginRequest {
  host: string
  port: number
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  role: 'admin' | 'user'
  host: string
  port: number
  passwordExpiryDays: number | null
}

export interface User {
  username: string
  role: 'admin' | 'user'
  host: string
  port: number
}

export interface RecentConnection {
  host: string
  port: number
  username: string
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

export interface CreateIndexRequest {
  database: string
  table: string
  indexName: string
  columns: string[]
  unique: boolean
}

// Dashboard Types
export interface DashboardStats {
  totalAccounts: number
  activeSessions: number
  expiringSoon: number
  slowQueries: number
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
