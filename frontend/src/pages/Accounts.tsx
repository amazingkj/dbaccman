import { useState, useEffect } from 'react'
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
  Statistic,
  Alert,
} from 'antd'
import {
  PlusOutlined,
  ReloadOutlined,
  LockOutlined,
  UnlockOutlined,
  DeleteOutlined,
  KeyOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { accountsApi } from '../api/accounts'
import type { Account, CreateAccountRequest, ExpiringAccount } from '../types'

const { Title } = Typography

function Accounts() {
  const [accounts, setAccounts] = useState<Account[]>([])
  const [expiringAccounts, setExpiringAccounts] = useState<ExpiringAccount[]>([])
  const [loading, setLoading] = useState(false)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [passwordModalOpen, setPasswordModalOpen] = useState(false)
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null)
  const [form] = Form.useForm()
  const [passwordForm] = Form.useForm()

  const fetchAccounts = async () => {
    setLoading(true)
    try {
      const [accountsRes, expiringRes] = await Promise.all([
        accountsApi.list(),
        accountsApi.getExpiring(30),
      ])
      setAccounts(accountsRes.data)
      setExpiringAccounts(expiringRes.data)
    } catch (error) {
      message.error('Failed to fetch accounts')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchAccounts()
  }, [])

  const handleCreate = async (values: CreateAccountRequest) => {
    try {
      await accountsApi.create(values)
      message.success('Account created successfully')
      setCreateModalOpen(false)
      form.resetFields()
      fetchAccounts()
    } catch (error) {
      message.error('Failed to create account')
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
    } catch (error) {
      message.error('Failed to change password')
    }
  }

  const handleDelete = async (account: Account) => {
    try {
      await accountsApi.delete(account.username, account.host)
      message.success('Account deleted successfully')
      fetchAccounts()
    } catch (error) {
      message.error('Failed to delete account')
    }
  }

  const handleUnlock = async (account: Account) => {
    try {
      await accountsApi.unlock(account.username, account.host)
      message.success('Account unlocked successfully')
      fetchAccounts()
    } catch (error) {
      message.error('Failed to unlock account')
    }
  }

  const columns = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      sorter: (a: Account, b: Account) => a.username.localeCompare(b.username),
    },
    {
      title: 'Host',
      dataIndex: 'host',
      key: 'host',
    },
    {
      title: 'Password Expiry',
      dataIndex: 'passwordLifetime',
      key: 'passwordLifetime',
      render: (days: number | null) =>
        days ? `${days} days` : <Tag color="blue">Never</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'accountLocked',
      key: 'accountLocked',
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
      render: (date: string | null) => date || '-',
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: Account) => (
        <Space>
          <Button
            type="link"
            icon={<KeyOutlined />}
            onClick={() => {
              setSelectedAccount(record)
              setPasswordModalOpen(true)
            }}
          >
            Change Password
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
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>Account Management</Title>

      {expiringAccounts.length > 0 && (
        <Alert
          message="Password Expiration Warning"
          description={
            <span>
              {expiringAccounts.length} account(s) will expire within 30 days
            </span>
          }
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          style={{ marginBottom: 16 }}
        />
      )}

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic title="Total Accounts" value={accounts.length} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Locked Accounts"
              value={accounts.filter((a) => a.accountLocked).length}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Expiring Soon"
              value={expiringAccounts.length}
              valueStyle={{ color: '#faad14' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Active Accounts"
              value={accounts.filter((a) => !a.accountLocked).length}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
      </Row>

      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreateModalOpen(true)}
        >
          Create Account
        </Button>
        <Button icon={<ReloadOutlined />} onClick={fetchAccounts}>
          Refresh
        </Button>
      </Space>

      <Table
        columns={columns}
        dataSource={accounts}
        loading={loading}
        rowKey={(record) => `${record.username}@${record.host}`}
        pagination={{ pageSize: 10 }}
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
    </div>
  )
}

export default Accounts
