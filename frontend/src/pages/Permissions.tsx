import { useState, useEffect } from 'react'
import {
  Typography,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Checkbox,
  Tag,
  message,
  Card,
  Row,
  Col,
  Empty,
} from 'antd'
import {
  SearchOutlined,
  PlusOutlined,
  DeleteOutlined,
  ReloadOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import { permissionsApi } from '../api/permissions'
import { accountsApi } from '../api/accounts'
import { tablespacesApi } from '../api/tablespaces'
import type { Permission, GrantPermissionRequest, Account, TablespaceInfo } from '../types'

const { Title, Text } = Typography
const { Option } = Select

const AVAILABLE_PRIVILEGES = [
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

const PRIVILEGE_PRESETS = {
  readOnly: ['SELECT'],
  readWrite: ['SELECT', 'INSERT', 'UPDATE', 'DELETE'],
  ddl: ['CREATE', 'DROP', 'INDEX', 'ALTER'],
  all: AVAILABLE_PRIVILEGES,
}

function Permissions() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [selectedAccount, setSelectedAccount] = useState<string | null>(null)
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [databases, setDatabases] = useState<string[]>([])
  const [loading, setLoading] = useState(false)
  const [grantModalOpen, setGrantModalOpen] = useState(false)
  const [tablespaceModalOpen, setTablespaceModalOpen] = useState(false)
  const [tablespaces, setTablespaces] = useState<TablespaceInfo[]>([])
  const [searchText, setSearchText] = useState('')
  const [searchColumn, setSearchColumn] = useState<string>('all')
  const [form] = Form.useForm()
  const [tablespaceForm] = Form.useForm()

  useEffect(() => {
    fetchAccounts()
    fetchDatabases()
    fetchTablespaces()
  }, [])

  const fetchAccounts = async () => {
    try {
      const response = await accountsApi.list()
      setAccounts(response.data)
    } catch {
      message.error('Failed to fetch accounts')
    }
  }

  const fetchDatabases = async () => {
    try {
      const response = await permissionsApi.getUserPermissions('root', '%')
      // Extract unique databases from permissions
      const uniqueDbs = Array.from(new Set(response.data.map((p) => p.database)))
      setDatabases(uniqueDbs.length > 0 ? uniqueDbs : ['mysql', 'test'])
    } catch {
      setDatabases(['mysql', 'test'])
    }
  }

  const fetchTablespaces = async () => {
    try {
      const response = await tablespacesApi.list()
      setTablespaces(response.data)
    } catch {
      // Tablespaces might not be available on all database types
      setTablespaces([])
    }
  }

  const fetchPermissions = async (userAtHost: string) => {
    setLoading(true)
    try {
      const response = await permissionsApi.getUserPermissions(
        userAtHost.split('@')[0],
        userAtHost.split('@')[1] || '%'
      )
      setPermissions(response.data)
    } catch {
      message.error('Failed to fetch permissions')
    } finally {
      setLoading(false)
    }
  }

  const handleAccountSelect = (value: string) => {
    setSelectedAccount(value)
    fetchPermissions(value)
  }

  const handleGrant = async (values: GrantPermissionRequest) => {
    try {
      await permissionsApi.grant(values)
      message.success('Permissions granted successfully')
      setGrantModalOpen(false)
      form.resetFields()
      if (selectedAccount) {
        fetchPermissions(selectedAccount)
      }
    } catch {
      message.error('Failed to grant permissions')
    }
  }

  const handleRevoke = async (permission: Permission) => {
    if (!selectedAccount) return

    const [username, host] = selectedAccount.split('@')
    try {
      await permissionsApi.revoke({
        username,
        host: host || '%',
        database: permission.database,
        table: permission.table,
        privileges: [permission.privilege],
      })
      message.success('Permission revoked successfully')
      fetchPermissions(selectedAccount)
    } catch {
      message.error('Failed to revoke permission')
    }
  }

  const handlePresetSelect = (preset: keyof typeof PRIVILEGE_PRESETS) => {
    form.setFieldsValue({
      privileges: PRIVILEGE_PRESETS[preset],
    })
  }

  // Search filter function
  const filterBySearch = (permission: Permission) => {
    if (!searchText) return true
    const search = searchText.toLowerCase()

    if (searchColumn === 'all') {
      return (
        permission.database?.toLowerCase().includes(search) ||
        permission.table?.toLowerCase().includes(search) ||
        permission.privilege?.toLowerCase().includes(search)
      )
    }

    const value = permission[searchColumn as keyof Permission]
    if (value === null || value === undefined) return false
    return value.toString().toLowerCase().includes(search)
  }

  const filteredPermissions = permissions.filter(filterBySearch)

  const handleSetTablespace = async (values: { tablespace: string; quota?: string }) => {
    if (!selectedAccount) return

    const [username, host] = selectedAccount.split('@')
    try {
      await accountsApi.setTablespace({
        username,
        host: host || '%',
        tablespace: values.tablespace,
        quota: values.quota,
      })
      message.success('Default tablespace set successfully')
      setTablespaceModalOpen(false)
      tablespaceForm.resetFields()
    } catch (error: unknown) {
      const axiosError = error as { response?: { data?: { error?: string } } }
      const errorMessage = axiosError.response?.data?.error || 'Failed to set tablespace'
      if (errorMessage.includes('does not support')) {
        message.warning('This database type does not support user-level tablespace assignment')
      } else {
        message.error(errorMessage)
      }
    }
  }

  const columns = [
    {
      title: 'Database',
      dataIndex: 'database',
      key: 'database',
      width: 150,
      ellipsis: true,
      render: (db: string) => <Tag color="blue">{db}</Tag>,
    },
    {
      title: 'Table',
      dataIndex: 'table',
      key: 'table',
      width: 150,
      ellipsis: true,
      render: (table: string) =>
        table === '*' ? <Tag>All Tables</Tag> : <Tag color="green">{table}</Tag>,
    },
    {
      title: 'Privilege',
      dataIndex: 'privilege',
      key: 'privilege',
      width: 120,
      render: (privilege: string) => {
        const color =
          privilege === 'SELECT'
            ? 'green'
            : ['INSERT', 'UPDATE', 'DELETE'].includes(privilege)
              ? 'orange'
              : ['CREATE', 'DROP', 'ALTER'].includes(privilege)
                ? 'red'
                : 'default'
        return <Tag color={color}>{privilege}</Tag>
      },
    },
    {
      title: 'Grant Option',
      dataIndex: 'isGrantable',
      key: 'isGrantable',
      width: 120,
      render: (isGrantable: boolean) =>
        isGrantable ? <Tag color="purple">WITH GRANT</Tag> : '-',
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: Permission) => (
        <Button
          type="link"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleRevoke(record)}
        >
          Revoke
        </Button>
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>Permissions</Title>

      <Card style={{ marginBottom: 24 }}>
        <Row gutter={16} align="middle">
          <Col span={8}>
            <Text strong>Select Account:</Text>
            <Select
              style={{ width: '100%', marginTop: 8 }}
              placeholder="Select an account to view permissions"
              onChange={handleAccountSelect}
              value={selectedAccount}
              showSearch
              filterOption={(input, option) =>
                (option?.value as string)?.toLowerCase().includes(input.toLowerCase())
              }
            >
              {accounts.map((account) => (
                <Option
                  key={`${account.username}@${account.host}`}
                  value={`${account.username}@${account.host}`}
                >
                  {account.username}@{account.host}
                </Option>
              ))}
            </Select>
          </Col>
          <Col span={16}>
            <Space style={{ float: 'right' }}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => setGrantModalOpen(true)}
                disabled={!selectedAccount}
              >
                Grant Permission
              </Button>
              <Button
                icon={<DatabaseOutlined />}
                onClick={() => {
                  tablespaceForm.resetFields()
                  setTablespaceModalOpen(true)
                }}
                disabled={!selectedAccount || tablespaces.length === 0}
                title={tablespaces.length === 0 ? 'No tablespaces available' : 'Set default tablespace for this account'}
              >
                Set Tablespace
              </Button>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => selectedAccount && fetchPermissions(selectedAccount)}
                disabled={!selectedAccount}
              >
                Refresh
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {selectedAccount ? (
        <>
          <Card
            title={
              <Space>
                <SearchOutlined />
                <span>Permissions for {selectedAccount}</span>
              </Space>
            }
            style={{ marginBottom: 16 }}
          >
            <Row gutter={16} align="middle">
              <Col>
                <Text>Total: {filteredPermissions.length} permission(s)</Text>
              </Col>
              <Col flex="auto" style={{ textAlign: 'right' }}>
                <Space>
                  <Select
                    value={searchColumn}
                    onChange={setSearchColumn}
                    style={{ width: 120 }}
                    size="middle"
                  >
                    <Option value="all">All Columns</Option>
                    <Option value="database">Database</Option>
                    <Option value="table">Table</Option>
                    <Option value="privilege">Privilege</Option>
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
          </Card>

          <Table
            columns={columns}
            dataSource={filteredPermissions}
            loading={loading}
            rowKey={(record) =>
              `${record.database}-${record.table}-${record.privilege}`
            }
            pagination={{ pageSize: 10 }}
          />
        </>
      ) : (
        <Empty description="Select an account to view permissions" />
      )}

      {/* Grant Permission Modal */}
      <Modal
        title="Grant Permission"
        open={grantModalOpen}
        onCancel={() => {
          setGrantModalOpen(false)
          form.resetFields()
        }}
        footer={null}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleGrant}
          initialValues={{
            username: selectedAccount?.split('@')[0],
            host: selectedAccount?.split('@')[1] || '%',
            table: '*',
          }}
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="username"
                label="Username"
                rules={[{ required: true }]}
              >
                <Input disabled />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="host" label="Host" rules={[{ required: true }]}>
                <Input disabled />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="database"
                label="Database"
                rules={[{ required: true, message: 'Please select a database' }]}
              >
                <Select placeholder="Select database">
                  {databases.map((db) => (
                    <Option key={db} value={db}>
                      {db}
                    </Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="table" label="Table">
                <Input placeholder="* for all tables" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="Quick Presets">
            <Space>
              <Button size="small" onClick={() => handlePresetSelect('readOnly')}>
                Read Only
              </Button>
              <Button size="small" onClick={() => handlePresetSelect('readWrite')}>
                Read/Write
              </Button>
              <Button size="small" onClick={() => handlePresetSelect('ddl')}>
                DDL
              </Button>
              <Button size="small" onClick={() => handlePresetSelect('all')}>
                All
              </Button>
            </Space>
          </Form.Item>

          <Form.Item
            name="privileges"
            label="Privileges"
            rules={[
              { required: true, message: 'Please select at least one privilege' },
            ]}
          >
            <Checkbox.Group>
              <Row>
                {AVAILABLE_PRIVILEGES.map((priv) => (
                  <Col span={8} key={priv}>
                    <Checkbox value={priv}>{priv}</Checkbox>
                  </Col>
                ))}
              </Row>
            </Checkbox.Group>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Grant
              </Button>
              <Button onClick={() => setGrantModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Set Tablespace Modal */}
      <Modal
        title="Set Default Tablespace"
        open={tablespaceModalOpen}
        onCancel={() => {
          setTablespaceModalOpen(false)
          tablespaceForm.resetFields()
        }}
        footer={null}
        width={500}
      >
        <Form
          form={tablespaceForm}
          layout="vertical"
          onFinish={handleSetTablespace}
        >
          <Form.Item label="Account">
            <Input value={selectedAccount || ''} disabled />
          </Form.Item>

          <Form.Item
            name="tablespace"
            label="Tablespace"
            rules={[{ required: true, message: 'Please select a tablespace' }]}
          >
            <Select placeholder="Select tablespace">
              {tablespaces.map((ts) => (
                <Option key={ts.name} value={ts.name}>
                  {ts.name} ({(ts.fileSize / 1024 / 1024).toFixed(2)} MB)
                </Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="quota"
            label="Quota (optional)"
            tooltip="Examples: UNLIMITED, 100M, 1G, 500K"
          >
            <Select placeholder="Select or enter quota" allowClear>
              <Option value="UNLIMITED">UNLIMITED</Option>
              <Option value="100M">100 MB</Option>
              <Option value="500M">500 MB</Option>
              <Option value="1G">1 GB</Option>
              <Option value="5G">5 GB</Option>
              <Option value="10G">10 GB</Option>
            </Select>
          </Form.Item>

          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Set Tablespace
              </Button>
              <Button onClick={() => setTablespaceModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default Permissions
