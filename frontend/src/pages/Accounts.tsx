import { useState, useEffect, useMemo, useCallback } from 'react'
import {
  Typography,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  Tag,
  Popconfirm,
  message,
  Card,
  Row,
  Col,
  Alert,
  Select,
  Checkbox,
  Dropdown,
  Result,
} from 'antd'
import type { MenuProps } from 'antd'
import {
  PlusOutlined,
  ReloadOutlined,
  LockOutlined,
  UnlockOutlined,
  DeleteOutlined,
  SyncOutlined,
  WarningOutlined,
  CopyOutlined,
  DownloadOutlined,
  DownOutlined,
  UserOutlined,
  TeamOutlined,
  ClockCircleOutlined,
} from '@ant-design/icons'
import { accountsApi } from '../api/accounts'
import { permissionsApi } from '../api/permissions'
import { formatToLocalTime } from '../utils/dateUtils'
import { useAuthStore } from '../store/authStore'
import type { Account, CreateAccountRequest, ExpiringAccount, CloneAccountRequest, BatchOperationResult, PaginationInfo, AccountStats } from '../types'

// Oracle system privileges for account creation
const ORACLE_SYSTEM_PRIVILEGES = [
  { value: 'CREATE SESSION', label: 'CREATE SESSION (Login)', description: 'Required to connect to database' },
  { value: 'CREATE TABLE', label: 'CREATE TABLE', description: 'Create tables in own schema' },
  { value: 'CREATE VIEW', label: 'CREATE VIEW', description: 'Create views in own schema' },
  { value: 'CREATE PROCEDURE', label: 'CREATE PROCEDURE', description: 'Create stored procedures' },
  { value: 'CREATE SEQUENCE', label: 'CREATE SEQUENCE', description: 'Create sequences' },
  { value: 'UNLIMITED TABLESPACE', label: 'UNLIMITED TABLESPACE', description: 'Use unlimited storage' },
]

// MySQL global privileges for account creation
const MYSQL_GLOBAL_PRIVILEGES = [
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
const POSTGRESQL_PRIVILEGES = [
  { value: 'CONNECT', label: 'CONNECT', description: 'Connect to database' },
  { value: 'CREATE', label: 'CREATE', description: 'Create new schemas/objects' },
  { value: 'TEMPORARY', label: 'TEMPORARY', description: 'Create temporary tables' },
]

// Get privileges based on database type
const getPrivilegesForDbType = (dbType: string | undefined) => {
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

const { Title, Text } = Typography

// Modernize-style stat card styles (white background with colored icons)
const statCardStyles = {
  total: {
    color: '#5d87ff',
    bgColor: 'rgba(93, 135, 255, 0.1)',
    icon: <TeamOutlined />,
  },
  locked: {
    color: '#fa896b',
    bgColor: 'rgba(250, 137, 107, 0.1)',
    icon: <LockOutlined />,
  },
  expiring: {
    color: '#ffae1f',
    bgColor: 'rgba(255, 174, 31, 0.1)',
    icon: <ClockCircleOutlined />,
  },
  active: {
    color: '#13deb9',
    bgColor: 'rgba(19, 222, 185, 0.1)',
    icon: <UserOutlined />,
  },
}

interface StatCardProps {
  title: string
  value: number
  style: { color: string; bgColor: string; icon: React.ReactNode }
}

function StatCard({ title, value, style }: StatCardProps) {
  return (
    <Card
      style={{
        background: '#fff',
        borderRadius: 12,
        border: 'none',
        height: '100%',
        boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
      }}
      styles={{
        body: {
          padding: '16px 20px',
          height: 90,
          display: 'flex',
          alignItems: 'center',
          gap: 14,
        }
      }}
    >
      <div style={{
        width: 48,
        height: 48,
        borderRadius: 10,
        background: style.bgColor,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 22,
        color: style.color,
        flexShrink: 0,
      }}>
        {style.icon}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 12,
          color: '#5a6a85',
          fontWeight: 500,
          marginBottom: 2,
        }}>
          {title}
        </div>
        <div style={{
          fontSize: 22,
          fontWeight: 600,
          color: '#2a3547',
          lineHeight: 1.2,
        }}>
          {value.toLocaleString()}
        </div>
      </div>
    </Card>
  )
}

// Oracle system users that should not be modified (moved outside component)
const ORACLE_SYSTEM_USERS = new Set([
  'SYS', 'SYSTEM', 'DBSNMP', 'OUTLN', 'DIP', 'ORACLE_OCM',
  'APPQOSSYS', 'WMSYS', 'XDB', 'ANONYMOUS', 'XS$NULL',
  'GSMCATUSER', 'GSMUSER', 'SYSBACKUP', 'SYSDG', 'SYSKM',
  'SYSRAC', 'SYS$UMF', 'AUDSYS', 'DGPDB_INT', 'DVF', 'DVSYS',
  'GGSYS', 'GSMADMIN_INTERNAL', 'LBACSYS', 'MDSYS', 'OJVMSYS',
  'OLAPSYS', 'ORDDATA', 'ORDSYS', 'REMOTE_SCHEDULER_AGENT', 'SI_INFORMTN_SCHEMA'
])

const isOracleSystemUser = (username: string): boolean => {
  return ORACLE_SYSTEM_USERS.has(username.toUpperCase()) || username.toUpperCase().startsWith('C##')
}

function Accounts() {
  const { user } = useAuthStore()
  const dbType = user?.dbType
  const { privileges: dbPrivileges, defaults: defaultPrivileges } = getPrivilegesForDbType(dbType)
  const hasPrivilegeOptions = dbPrivileges.length > 0

  const [accounts, setAccounts] = useState<Account[]>([])
  const [expiringAccounts, setExpiringAccounts] = useState<ExpiringAccount[]>([])
  const [loading, setLoading] = useState(false)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [passwordModalOpen, setPasswordModalOpen] = useState(false)
  const [cloneModalOpen, setCloneModalOpen] = useState(false)
  const [batchResultModalOpen, setBatchResultModalOpen] = useState(false)
  const [batchResult, setBatchResult] = useState<BatchOperationResult | null>(null)
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null)
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [form] = Form.useForm()
  const [passwordForm] = Form.useForm()
  const [cloneForm] = Form.useForm()
  const [searchText, setSearchText] = useState('')
  const [searchColumn, setSearchColumn] = useState<string>('all')

  // Pagination state
  const [pagination, setPagination] = useState<PaginationInfo>({
    page: 1,
    pageSize: 15,
    totalItems: 0,
    totalPages: 0,
  })
  const [stats, setStats] = useState<AccountStats>({
    totalAccounts: 0,
    lockedAccounts: 0,
    activeAccounts: 0,
  })

  const fetchAccounts = async (page = pagination.page, pageSize = pagination.pageSize) => {
    setLoading(true)
    try {
      const [paginatedRes, expiringRes] = await Promise.all([
        accountsApi.listPaginated(page, pageSize),
        accountsApi.getExpiring(30),
      ])
      setAccounts(paginatedRes.data.data)
      setPagination(paginatedRes.data.pagination)
      setStats(paginatedRes.data.stats)
      setExpiringAccounts(expiringRes.data)
    } catch (error) {
      message.error('Failed to fetch accounts')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchAccounts(1, pagination.pageSize)
  }, [])

  const handleCreate = async (values: CreateAccountRequest & { privileges?: string[] }) => {
    try {
      // First create the account
      await accountsApi.create({
        username: values.username,
        host: values.host,
        password: values.password,
        expireDays: values.expireDays,
      })

      // Then grant privileges if selected (for Oracle)
      if (values.privileges && values.privileges.length > 0) {
        try {
          await permissionsApi.grant({
            username: values.username,
            host: values.host || '%',
            database: '',  // Empty for system privileges
            table: '*',
            privileges: values.privileges,
          })
          message.success(`Account created with ${values.privileges.length} privilege(s)`)
        } catch {
          message.warning('Account created but failed to grant some privileges')
        }
      } else {
        message.success('Account created successfully')
      }

      setCreateModalOpen(false)
      form.resetFields()
      fetchAccounts(1, pagination.pageSize) // Go to first page after create
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to create account'
      message.error(errorMsg)
    }
  }

  const handleChangePassword = async (values: { password: string }) => {
    if (!selectedAccount) return
    try {
      await accountsApi.changePassword(
        selectedAccount.username,
        selectedAccount.host,
        values.password
      )
      message.success('Password changed successfully')
      setPasswordModalOpen(false)
      passwordForm.resetFields()
      setSelectedAccount(null)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to change password'
      message.error(errorMsg)
    }
  }

  const handleDelete = async (account: Account) => {
    try {
      await accountsApi.delete(account.username, account.host)
      message.success('Account deleted successfully')
      fetchAccounts(pagination.page, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to delete account'
      message.error(errorMsg)
    }
  }

  const handleUnlock = async (account: Account) => {
    try {
      await accountsApi.unlock(account.username, account.host)
      message.success('Account unlocked successfully')
      fetchAccounts(pagination.page, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to unlock account'
      message.error(errorMsg)
    }
  }

  // Clone account handler
  const handleClone = async (values: CloneAccountRequest) => {
    try {
      await accountsApi.clone(values)
      message.success('Account cloned successfully')
      setCloneModalOpen(false)
      cloneForm.resetFields()
      setSelectedAccount(null)
      fetchAccounts(1, pagination.pageSize) // Go to first page to see new account
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to clone account'
      message.error(errorMsg)
    }
  }

  // Batch delete handler
  const handleBatchDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('Please select accounts to delete')
      return
    }

    const accountsToDelete = selectedRowKeys.map(key => {
      const [username, host] = (key as string).split('@')
      return { username, host: host || '%' }
    })

    try {
      const result = await accountsApi.batchDelete({ accounts: accountsToDelete })
      setBatchResult(result.data)
      setBatchResultModalOpen(true)
      setSelectedRowKeys([])
      fetchAccounts(pagination.page, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to batch delete accounts'
      message.error(errorMsg)
    }
  }

  // Batch unlock handler
  const handleBatchUnlock = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('Please select accounts to unlock')
      return
    }

    const accountsToUnlock = selectedRowKeys.map(key => {
      const [username, host] = (key as string).split('@')
      return { username, host: host || '%' }
    })

    try {
      const result = await accountsApi.batchUnlock({ accounts: accountsToUnlock })
      setBatchResult(result.data)
      setBatchResultModalOpen(true)
      setSelectedRowKeys([])
      fetchAccounts(pagination.page, pagination.pageSize)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to batch unlock accounts'
      message.error(errorMsg)
    }
  }

  // Export handler
  const handleExport = async (includePermissions: boolean) => {
    try {
      // If accounts are selected, export only selected ones (client-side)
      if (selectedRowKeys.length > 0) {
        const selectedAccounts = accounts.filter(account =>
          selectedRowKeys.includes(`${account.username}@${account.host}`)
        )

        // Generate CSV
        const headers = includePermissions
          ? ['Username', 'Host', 'Status', 'Password Expiry', 'Last Password Change']
          : ['Username', 'Host', 'Status', 'Password Expiry', 'Last Password Change']

        const rows = selectedAccounts.map(account => [
          account.username,
          account.host,
          account.accountLocked ? 'Locked' : 'Active',
          account.passwordLifetime ? `${account.passwordLifetime} days` : 'Never',
          account.passwordLastChanged || '-'
        ])

        const csvContent = [
          headers.join(','),
          ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
        ].join('\n')

        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = includePermissions ? 'selected_accounts_with_permissions.csv' : 'selected_accounts.csv'
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        document.body.removeChild(a)
        message.success(`Exported ${selectedAccounts.length} selected account(s)`)
      } else {
        // No selection - export all from backend
        const response = await accountsApi.exportCsv(includePermissions)
        const blob = new Blob([response.data], { type: 'text/csv' })
        const url = window.URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = includePermissions ? 'accounts_with_permissions.csv' : 'accounts.csv'
        document.body.appendChild(a)
        a.click()
        window.URL.revokeObjectURL(url)
        document.body.removeChild(a)
        message.success('Export completed')
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      const errorMsg = err.response?.data?.error || 'Failed to export accounts'
      message.error(errorMsg)
    }
  }

  // Open clone modal
  const openCloneModal = (account: Account) => {
    setSelectedAccount(account)
    cloneForm.setFieldsValue({
      sourceUsername: account.username,
      sourceHost: account.host,
      newUsername: '',
      newHost: account.host,
      newPassword: '',
      copyPermissions: true,
      expireDays: 90,
    })
    setCloneModalOpen(true)
  }

  // Search filter function (memoized)
  const filterBySearch = useCallback((account: Account) => {
    if (!searchText) return true
    const search = searchText.toLowerCase()

    if (searchColumn === 'all') {
      const status = account.accountLocked ? 'locked' : 'active'
      return (
        account.username?.toLowerCase().includes(search) ||
        account.host?.toLowerCase().includes(search) ||
        account.passwordLastChanged?.toLowerCase().includes(search) ||
        status.includes(search)
      )
    }

    if (searchColumn === 'status') {
      const status = account.accountLocked ? 'locked' : 'active'
      return status.includes(search)
    }

    const value = account[searchColumn as keyof Account]
    if (value === null || value === undefined) return false
    return value.toString().toLowerCase().includes(search)
  }, [searchText, searchColumn])

  // Memoize filtered accounts
  const filteredAccounts = useMemo(() => accounts.filter(filterBySearch), [accounts, filterBySearch])

  // Memoize columns to prevent unnecessary re-renders
  const columns = useMemo(() => [
    {
      title: 'No.',
      key: 'no',
      width: 45,
      align: 'center' as const,
      render: (_: unknown, __: Account, index: number) => index + 1,
    },
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      width: 150,
      ellipsis: true,
      sorter: (a: Account, b: Account) => a.username.localeCompare(b.username),
    },
    {
      title: 'Host',
      dataIndex: 'host',
      key: 'host',
      width: 120,
      ellipsis: true,
    },
    {
      title: 'Password Expiry',
      dataIndex: 'passwordLifetime',
      key: 'passwordLifetime',
      width: 120,
      sorter: (a: Account, b: Account) => {
        // null (Never) should be at the end when sorting ascending
        if (a.passwordLifetime === null && b.passwordLifetime === null) return 0
        if (a.passwordLifetime === null) return 1
        if (b.passwordLifetime === null) return -1
        return (a.passwordLifetime || 0) - (b.passwordLifetime || 0)
      },
      render: (days: number | null) =>
        days ? `${days} days` : <Tag color="blue">Never</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'accountLocked',
      key: 'accountLocked',
      width: 100,
      render: (locked: boolean) =>
        locked ? (
          <Tag color="red" icon={<LockOutlined />}>
            Locked
          </Tag>
        ) : (
          <Tag color="green" icon={<UnlockOutlined />}>
            Active
          </Tag>
        ),
    },
    {
      title: 'Last Password Change',
      dataIndex: 'passwordLastChanged',
      key: 'passwordLastChanged',
      width: 160,
      ellipsis: true,
      sorter: (a: Account, b: Account) => {
        // null should be at the end
        if (!a.passwordLastChanged && !b.passwordLastChanged) return 0
        if (!a.passwordLastChanged) return 1
        if (!b.passwordLastChanged) return -1
        return new Date(a.passwordLastChanged).getTime() - new Date(b.passwordLastChanged).getTime()
      },
      defaultSortOrder: 'descend' as const,
      render: (date: string | null) => formatToLocalTime(date),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 280,
      render: (_: unknown, record: Account) => {
        const isSystemUser = isOracleSystemUser(record.username)

        if (isSystemUser) {
          return <Tag color="default">System User</Tag>
        }

        return (
          <Space>
            <Button
              type="link"
              icon={<CopyOutlined />}
              onClick={() => openCloneModal(record)}
            >
              Clone
            </Button>
            <Button
              type="link"
              icon={<SyncOutlined />}
              onClick={() => {
                setSelectedAccount(record)
                setPasswordModalOpen(true)
              }}
            >
              Password
            </Button>
            {record.accountLocked && (
              <Button
                type="link"
                icon={<UnlockOutlined />}
                onClick={() => handleUnlock(record)}
              >
                Unlock
              </Button>
            )}
            <Popconfirm
              title="Delete Account"
              description={`Are you sure you want to delete ${record.username}@${record.host}?`}
              onConfirm={() => handleDelete(record)}
              okText="Yes"
              cancelText="No"
            >
              <Button type="link" danger icon={<DeleteOutlined />}>
                Delete
              </Button>
            </Popconfirm>
          </Space>
        )
      },
    },
  ], [])

  return (
    <div>
      {/* Header */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 24
      }}>
        <div>
          <Title level={2} style={{ margin: 0, marginBottom: 4 }}>
            <TeamOutlined style={{ marginRight: 12 }} />
            Accounts
          </Title>
          <Text type="secondary">Database user account management</Text>
        </div>
      </div>

      {expiringAccounts.length > 0 && (
        <Alert
          message="Password Expiration Warning"
          description={`${expiringAccounts.length} account(s) will expire within 30 days`}
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      )}

      {/* Stats Cards - CoreUI Style */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Accounts"
            value={stats.totalAccounts}
            style={statCardStyles.total}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Locked Accounts"
            value={stats.lockedAccounts}
            style={statCardStyles.locked}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Expiring Soon"
            value={expiringAccounts.length}
            style={statCardStyles.expiring}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Active Accounts"
            value={stats.activeAccounts}
            style={statCardStyles.active}
          />
        </Col>
      </Row>

      <Row gutter={16} align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Space>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalOpen(true)}
            >
              Create Account
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => fetchAccounts(pagination.page, pagination.pageSize)}>
              Refresh
            </Button>
            {selectedRowKeys.length > 0 && (
              <>
                <Popconfirm
                  title="Batch Delete"
                  description={`Are you sure you want to delete ${selectedRowKeys.length} account(s)?`}
                  onConfirm={handleBatchDelete}
                  okText="Yes"
                  cancelText="No"
                >
                  <Button danger icon={<DeleteOutlined />}>
                    Delete ({selectedRowKeys.length})
                  </Button>
                </Popconfirm>
                <Button icon={<UnlockOutlined />} onClick={handleBatchUnlock}>
                  Unlock ({selectedRowKeys.length})
                </Button>
              </>
            )}
            <Dropdown
              menu={{
                items: [
                  { key: 'accounts', label: 'Accounts Only', onClick: () => handleExport(false) },
                  { key: 'permissions', label: 'With Permissions', onClick: () => handleExport(true) },
                ] as MenuProps['items'],
              }}
            >
              <Button icon={<DownloadOutlined />}>
                Export {selectedRowKeys.length > 0 ? `(${selectedRowKeys.length})` : ''} <DownOutlined />
              </Button>
            </Dropdown>
          </Space>
        </Col>
        <Col flex="auto" style={{ textAlign: 'right' }}>
          <Space>
            <Select
              value={searchColumn}
              onChange={setSearchColumn}
              style={{ width: 120 }}
              size="middle"
            >
              <Select.Option value="all">All Columns</Select.Option>
              <Select.Option value="username">Username</Select.Option>
              <Select.Option value="host">Host</Select.Option>
              <Select.Option value="status">Status</Select.Option>
            </Select>
            <Input.Search
              placeholder="Search..."
              allowClear
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              style={{ width: 200 }}
            />
          </Space>
        </Col>
      </Row>

      <Table
        columns={columns}
        dataSource={filteredAccounts}
        loading={loading}
        rowKey={(record) => `${record.username}@${record.host}`}
        pagination={{
          current: pagination.page,
          pageSize: pagination.pageSize,
          total: pagination.totalItems,
          showSizeChanger: true,
          pageSizeOptions: ['10', '15', '20', '50', '100'],
          showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} accounts`,
          onChange: (page, pageSize) => {
            fetchAccounts(page, pageSize)
          },
        }}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
          getCheckboxProps: (record) => ({
            disabled: isOracleSystemUser(record.username),
          }),
        }}
      />

      {/* Create Account Modal */}
      <Modal
        title="Create Account"
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false)
          form.resetFields()
        }}
        footer={null}
        width={hasPrivilegeOptions ? 550 : 420}
      >
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="username"
            label="Username"
            rules={[{ required: true, message: 'Please enter username' }]}
          >
            <Input placeholder="Enter username" />
          </Form.Item>
          <Form.Item name="host" label="Host" initialValue="%">
            <Input placeholder="Enter host (default: %)" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[
              { required: true, message: 'Please enter password' },
              { min: 8, message: 'Password must be at least 8 characters' },
            ]}
          >
            <Input.Password placeholder="Enter password" />
          </Form.Item>
          <Form.Item
            name="expireDays"
            label="Password Expiry (days)"
            initialValue={90}
          >
            <InputNumber min={0} max={365} style={{ width: '100%' }} />
          </Form.Item>

          {/* Database-specific Privileges */}
          {hasPrivilegeOptions && (
            <Form.Item
              name="privileges"
              label={`Grant Privileges (${dbType})`}
              tooltip="Select privileges to grant to the new account"
              initialValue={defaultPrivileges}
            >
              <Checkbox.Group style={{ width: '100%' }}>
                <Row>
                  {dbPrivileges.map((priv) => (
                    <Col span={24} key={priv.value} style={{ marginBottom: 8 }}>
                      <Checkbox value={priv.value}>
                        <span style={{ fontWeight: 500 }}>{priv.label}</span>
                        <Text type="secondary" style={{ display: 'block', fontSize: 12, marginLeft: 24 }}>
                          {priv.description}
                        </Text>
                      </Checkbox>
                    </Col>
                  ))}
                </Row>
              </Checkbox.Group>
            </Form.Item>
          )}

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Create
              </Button>
              <Button onClick={() => setCreateModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Change Password Modal */}
      <Modal
        title={`Change Password - ${selectedAccount?.username}@${selectedAccount?.host}`}
        open={passwordModalOpen}
        onCancel={() => {
          setPasswordModalOpen(false)
          passwordForm.resetFields()
          setSelectedAccount(null)
        }}
        footer={null}
      >
        <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
          <Form.Item
            name="password"
            label="New Password"
            rules={[
              { required: true, message: 'Please enter new password' },
              { min: 8, message: 'Password must be at least 8 characters' },
            ]}
          >
            <Input.Password placeholder="Enter new password" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="Confirm Password"
            dependencies={['password']}
            rules={[
              { required: true, message: 'Please confirm password' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('Passwords do not match'))
                },
              }),
            ]}
          >
            <Input.Password placeholder="Confirm new password" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Change Password
              </Button>
              <Button onClick={() => setPasswordModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Clone Account Modal */}
      <Modal
        title={`Clone Account - ${selectedAccount?.username}@${selectedAccount?.host}`}
        open={cloneModalOpen}
        onCancel={() => {
          setCloneModalOpen(false)
          cloneForm.resetFields()
          setSelectedAccount(null)
        }}
        footer={null}
        width={500}
      >
        <Form form={cloneForm} layout="vertical" onFinish={handleClone}>
          <Form.Item name="sourceUsername" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="sourceHost" hidden>
            <Input />
          </Form.Item>
          <Form.Item
            name="newUsername"
            label="New Username"
            rules={[{ required: true, message: 'Please enter new username' }]}
          >
            <Input placeholder="Enter new username" />
          </Form.Item>
          <Form.Item name="newHost" label="Host" initialValue="%">
            <Input placeholder="Enter host (default: %)" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="Password"
            rules={[
              { required: true, message: 'Please enter password' },
              { min: 8, message: 'Password must be at least 8 characters' },
            ]}
          >
            <Input.Password placeholder="Enter password" />
          </Form.Item>
          <Form.Item name="copyPermissions" valuePropName="checked" initialValue={true}>
            <Checkbox>Copy permissions from source account</Checkbox>
          </Form.Item>
          <Form.Item
            name="expireDays"
            label="Password Expiry (days)"
            initialValue={90}
          >
            <InputNumber min={0} max={365} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<CopyOutlined />}>
                Clone Account
              </Button>
              <Button onClick={() => setCloneModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Batch Operation Result Modal */}
      <Modal
        title="Batch Operation Result"
        open={batchResultModalOpen}
        onCancel={() => {
          setBatchResultModalOpen(false)
          setBatchResult(null)
        }}
        footer={[
          <Button key="ok" type="primary" onClick={() => setBatchResultModalOpen(false)}>
            OK
          </Button>,
        ]}
        width={500}
      >
        {batchResult && (
          <Result
            status={batchResult.failed.length === 0 ? 'success' : 'warning'}
            title={
              batchResult.failed.length === 0
                ? 'All operations completed successfully'
                : `${batchResult.success.length} succeeded, ${batchResult.failed.length} failed`
            }
            subTitle={
              <>
                {batchResult.success.length > 0 && (
                  <div style={{ marginBottom: 16 }}>
                    <strong>Succeeded:</strong>
                    <div style={{ color: '#52c41a' }}>
                      {batchResult.success.join(', ')}
                    </div>
                  </div>
                )}
                {batchResult.failed.length > 0 && (
                  <div>
                    <strong>Failed:</strong>
                    {batchResult.failed.map((f, i) => (
                      <div key={i} style={{ color: '#cf1322' }}>
                        {f.account}: {f.error}
                      </div>
                    ))}
                  </div>
                )}
              </>
            }
          />
        )}
      </Modal>
    </div>
  )
}

export default Accounts
