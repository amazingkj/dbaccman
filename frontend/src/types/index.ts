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
  token?: string  // Deprecated: token is now sent as httpOnly cookie
  sessionId: string
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
  profile: string | null  // Oracle only: user profile name
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

// Clone Account
export interface CloneAccountRequest {
  sourceUsername: string
  sourceHost: string
  newUsername: string
  newHost: string
  newPassword: string
  copyPermissions: boolean
  expireDays: number
}

// Batch Operations
export interface BatchCreateAccountRequest {
  accounts: CreateAccountRequest[]
}

export interface BatchDeleteRequest {
  accounts: AccountIdentifier[]
}

export interface BatchUnlockRequest {
  accounts: AccountIdentifier[]
}

export interface AccountIdentifier {
  username: string
  host: string
}

export interface BatchOperationResult {
  success: string[]
  failed: BatchOperationError[]
}

export interface BatchOperationError {
  account: string
  error: string
}

// Role Management (Oracle)
export interface Role {
  name: string
  isDefault: boolean
  isAdmin: boolean
}

export interface UserRole {
  username: string
  roleName: string
  isDefault: boolean
  isAdmin: boolean
}

export interface GrantRoleRequest {
  username: string
  host: string
  roles: string[]
  withAdminOption: boolean
}

export interface RevokeRoleRequest {
  username: string
  host: string
  roles: string[]
}

// PDB Management (Oracle)
export interface PdbInfo {
  name: string
  openMode: string
  restricted: boolean
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
  database?: string  // Optional for Oracle system privileges
  table?: string
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
export interface HealthScore {
  total: number
  accountScore: number
  sessionScore: number
  storageScore: number
  status: 'healthy' | 'warning' | 'critical'
  issues: string[]
}

export interface DashboardStats {
  totalAccounts: number
  activeSessions: number
  expiringSoon: number
  slowQueries: number
  lockedAccounts: number
  totalDatabases: number
  totalTables: number
  tablespaceUsage: number
  criticalTablespaces: number
  healthScore: HealthScore
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

// Pagination Types
export interface PaginationInfo {
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export interface AccountStats {
  totalAccounts: number
  lockedAccounts: number
  activeAccounts: number
}

export interface PaginatedAccountsResponse {
  data: Account[]
  pagination: PaginationInfo
  stats: AccountStats
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