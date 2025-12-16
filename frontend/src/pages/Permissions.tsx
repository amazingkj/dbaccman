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
} from '@ant-design/icons'
import { permissionsApi } from '../api/permissions'
import { accountsApi } from '../api/accounts'
import type { Permission, GrantPermissionRequest, Account } from '../types'

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
  const [form] = Form.useForm()

  useEffect(() => {
    fetchAccounts()
    fetchDatabases()
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

  const columns = [
    {
      title: 'Database',
      dataIndex: 'database',
      key: 'database',
      render: (db: string) => <Tag color="blue">{db}</Tag>,
    },
    {
      title: 'Table',
      dataIndex: 'table',
      key: 'table',
      render: (table: string) =>
        table === '*' ? <Tag>All Tables</Tag> : <Tag color="green">{table}</Tag>,
    },
    {
      title: 'Privilege',
      dataIndex: 'privilege',
      key: 'privilege',
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
      render: (isGrantable: boolean) =>
        isGrantable ? <Tag color="purple">WITH GRANT</Tag> : '-',
    },
    {
      title: 'Actions',
      key: 'actions',
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

  // Group permissions by database
  const groupedPermissions = permissions.reduce(
    (acc, perm) => {
      const key = `${perm.database}.${perm.table}`
      if (!acc[key]) {
        acc[key] = []
      }
      acc[key].push(perm)
      return acc
    },
    {} as Record<string, Permission[]>
  )

  return (
    <div>
      <Title level={2}>Permission Management</Title>

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
            <Text>Total: {permissions.length} permission(s)</Text>
          </Card>

          <Table
            columns={columns}
            dataSource={permissions}
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
    </div>
  )
}

export default Permissions
