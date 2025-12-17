import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Space, Select, InputNumber, Divider } from 'antd'
import { UserOutlined, LockOutlined, DatabaseOutlined, GlobalOutlined, DeleteOutlined } from '@ant-design/icons'
import { useAuth } from '../hooks/useAuth'
import { useAuthStore } from '../store/authStore'
import { useConnectionStore } from '../store/connectionStore'
import type { LoginRequest, RecentConnection, DatabaseType } from '../types'
import { DATABASE_TYPES, getDefaultPort } from '../types'

const { Title, Text } = Typography

function Login() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [selectedDbType, setSelectedDbType] = useState<DatabaseType>('MYSQL')
  const { login } = useAuth()
  const { isAuthenticated } = useAuthStore()
  const { recentConnections, removeRecentConnection } = useConnectionStore()

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  const handleSubmit = async (values: LoginRequest) => {
    setLoading(true)
    try {
      await login(values)
    } catch {
      // Error is handled in useAuth hook
    } finally {
      setLoading(false)
    }
  }

  const handleSelectRecentConnection = (value: string) => {
    const connection = recentConnections.find(
      (c) => `${c.username}@${c.host}:${c.port}` === value
    )
    if (connection) {
      const dbType = connection.dbType || 'MYSQL'
      setSelectedDbType(dbType)
      form.setFieldsValue({
        dbType,
        host: connection.host,
        port: connection.port,
        username: connection.username,
        password: '',
        database: connection.database,
      })
    }
  }

  const handleRemoveRecentConnection = (
    e: React.MouseEvent,
    connection: RecentConnection
  ) => {
    e.stopPropagation()
    removeRecentConnection(connection.host, connection.port, connection.username)
  }

  const handleDbTypeChange = (dbType: DatabaseType) => {
    setSelectedDbType(dbType)
    const currentPort = form.getFieldValue('port')

    // Check if current port is one of the default ports
    const isDefaultPort = DATABASE_TYPES.some(t => t.defaultPort === currentPort)

    // Change port if it's empty or one of the default ports
    if (!currentPort || isDefaultPort) {
      form.setFieldsValue({ port: getDefaultPort(dbType) })
    }

    // Clear database field when switching DB type
    form.setFieldsValue({ database: undefined })
  }

  const getDatabaseFieldConfig = () => {
    switch (selectedDbType) {
      case 'ORACLE':
        return {
          label: 'Service Name',
          placeholder: 'e.g., ORCL, XEPDB1, FREEPDB1',
          required: true,
          tooltip: 'Oracle Service Name or SID',
        }
      case 'POSTGRESQL':
        return {
          label: 'Database',
          placeholder: 'postgres',
          required: false,
          tooltip: 'Database name (default: postgres)',
        }
      default:
        return {
          label: 'Database',
          placeholder: 'Optional',
          required: false,
          tooltip: 'Database/Schema name (optional)',
        }
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)',
      }}
    >
      <style>
        {`
          @import url('https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/pretendard.css');

          .login-card {
            font-family: 'Pretendard', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          }

          .login-card .ant-form-item {
            margin-bottom: 12px;
          }
        `}
      </style>
      <Card className="login-card" style={{ width: 420, boxShadow: '0 8px 24px rgba(0,0,0,0.3)' }}>
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <DatabaseOutlined style={{ fontSize: 48, color: '#1890ff' }} />
            <Title
              level={2}
              style={{
                margin: '12px 0 4px',
                fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
                fontWeight: 800,
              }}
            >
              DBAccMan
            </Title>
            <Text type="secondary">Database Account Manager</Text>
          </div>

          {recentConnections.length > 0 && (
            <>
              <Select
                placeholder="Select recent connection"
                style={{ width: '100%' }}
                onChange={handleSelectRecentConnection}
                allowClear
                optionLabelProp="label"
              >
                {recentConnections.map((conn) => {
                  const key = `${conn.username}@${conn.host}:${conn.port}`
                  const dbTypeLabel = DATABASE_TYPES.find(t => t.value === conn.dbType)?.label || 'MySQL'
                  return (
                    <Select.Option key={key} value={key} label={`${key} (${dbTypeLabel})`}>
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <span>{key} <Text type="secondary">({dbTypeLabel})</Text></span>
                        <DeleteOutlined
                          onClick={(e) => handleRemoveRecentConnection(e, conn)}
                          style={{ color: '#ff4d4f' }}
                        />
                      </div>
                    </Select.Option>
                  )
                })}
              </Select>
              <Divider style={{ margin: '8px 0' }}>or enter manually</Divider>
            </>
          )}

          <Form
            form={form}
            name="login"
            onFinish={handleSubmit}
            autoComplete="off"
            layout="vertical"
            initialValues={{ dbType: 'MYSQL', port: 3306 }}
          >
            <Form.Item
              name="dbType"
              label="Database Type"
              rules={[{ required: true, message: 'Please select a database type' }]}
            >
              <Select
                size="large"
                onChange={handleDbTypeChange}
                options={DATABASE_TYPES.map(t => ({
                  value: t.value,
                  label: t.label,
                }))}
              />
            </Form.Item>

            <Form.Item
              name="host"
              label="Host"
              rules={[{ required: true, message: 'Please enter the host address' }]}
            >
              <Input
                prefix={<GlobalOutlined />}
                placeholder="localhost or IP address"
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="port"
              label="Port"
              rules={[{ required: true, message: 'Please enter the port' }]}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={1}
                max={65535}
                placeholder="3306"
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="database"
              label={getDatabaseFieldConfig().label}
              tooltip={getDatabaseFieldConfig().tooltip}
              rules={getDatabaseFieldConfig().required ? [{ required: true, message: `Please enter the ${getDatabaseFieldConfig().label.toLowerCase()}` }] : []}
            >
              <Input
                prefix={<DatabaseOutlined />}
                placeholder={getDatabaseFieldConfig().placeholder}
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="username"
              label="Username"
              rules={[{ required: true, message: 'Please enter your username' }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="Database username"
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="password"
              label="Password"
              rules={[{ required: true, message: 'Please enter your password' }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="Database password"
                size="large"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                size="large"
              >
                Connect
              </Button>
            </Form.Item>
          </Form>

          <Text
            type="secondary"
            style={{ textAlign: 'center', display: 'block', fontSize: 12 }}
          >
            Enter your database credentials to connect
          </Text>
        </Space>
      </Card>
    </div>
  )
}

export default Login