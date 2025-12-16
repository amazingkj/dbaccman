import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Space, Select, InputNumber, Divider } from 'antd'
import { UserOutlined, LockOutlined, DatabaseOutlined, GlobalOutlined, DeleteOutlined } from '@ant-design/icons'
import { useAuth } from '../hooks/useAuth'
import { useAuthStore } from '../store/authStore'
import { useConnectionStore } from '../store/connectionStore'
import type { LoginRequest, RecentConnection } from '../types'

const { Title, Text } = Typography

function Login() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
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
      form.setFieldsValue({
        host: connection.host,
        port: connection.port,
        username: connection.username,
        password: '',
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
          @font-face {
            font-family: 'Aggravo';
            src: url('https://cdn.jsdelivr.net/gh/projectnoonnu/noonfonts_2108@1.1/SBAggroL.woff') format('woff');
            font-weight: 700;
            font-display: swap;
          }
        `}
      </style>
      <Card style={{ width: 420, boxShadow: '0 8px 24px rgba(0,0,0,0.3)' }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <DatabaseOutlined style={{ fontSize: 48, color: '#1890ff' }} />
            <Title
              level={2}
              style={{
                margin: '16px 0 8px',
                fontFamily: 'Aggravo, sans-serif',
                fontWeight: 700,
              }}
            >
              DBAccMan
            </Title>
            <Text type="secondary">Click, Not Command</Text>
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
                  return (
                    <Select.Option key={key} value={key} label={key}>
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <span>{key}</span>
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
            initialValues={{ port: 3306 }}
          >
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
              name="username"
              label="Username"
              rules={[{ required: true, message: 'Please enter your username' }]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="MySQL username"
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
                placeholder="MySQL password"
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
            Enter your MySQL server credentials to connect
          </Text>
        </Space>
      </Card>
    </div>
  )
}

export default Login
