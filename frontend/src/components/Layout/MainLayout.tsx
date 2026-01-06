import { useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { Layout, Tag, Space, Avatar, Dropdown, theme, Modal, Input, Form, message } from 'antd'
import { LogoutOutlined, UserOutlined, SwapOutlined, PlusOutlined } from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { useAuthStore } from '../../store/authStore'
import { useConnectionStore } from '../../store/connectionStore'
import { useAuth } from '../../hooks/useAuth'
import Sidebar from './Sidebar'
import SessionTimeout from '../common/SessionTimeout'
import SessionCountdown from '../common/SessionCountdown'
import type { RecentConnection } from '../../types'

const { Header, Content, Footer } = Layout

function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [switchModalOpen, setSwitchModalOpen] = useState(false)
  const [switchTarget, setSwitchTarget] = useState<RecentConnection | null>(null)
  const [switchLoading, setSwitchLoading] = useState(false)
  const [form] = Form.useForm()

  const { user } = useAuthStore()
  const { recentConnections } = useConnectionStore()
  const { logout, login } = useAuth()
  const navigate = useNavigate()
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken()

  const getDbTypeColor = (dbType?: string) => {
    switch (dbType?.toUpperCase()) {
      case 'MYSQL': return '#00758F'
      case 'POSTGRESQL': return '#336791'
      case 'ORACLE': return '#F80000'
      default: return '#1890ff'
    }
  }

  // Filter out current connection from recent connections
  const otherConnections = recentConnections.filter(
    (c) => !(c.host === user?.host && c.port === user?.port && c.username === user?.username)
  ).slice(0, 5)

  const handleSwitchClick = (connection: RecentConnection) => {
    setSwitchTarget(connection)
    setSwitchModalOpen(true)
    form.resetFields()
  }

  const handleSwitch = async (values: { password: string }) => {
    if (!switchTarget) return
    setSwitchLoading(true)
    try {
      await login({
        host: switchTarget.host,
        port: switchTarget.port,
        username: switchTarget.username,
        password: values.password,
        dbType: switchTarget.dbType,
        database: switchTarget.database,
      })
      message.success(`Switched to ${switchTarget.username}@${switchTarget.host}`)
      setSwitchModalOpen(false)
      navigate('/dashboard')
    } catch {
      message.error('Authentication failed. Please check your password.')
    } finally {
      setSwitchLoading(false)
    }
  }

  const userMenuItems: MenuProps['items'] = [
    {
      type: 'group',
      label: (
        <div style={{ padding: '4px 0' }}>
          <div style={{ fontWeight: 600, marginBottom: 2, color: '#2a3547' }}>{user?.username}</div>
          <div style={{ fontSize: 11, color: '#8c8c8c' }}>{user?.host}:{user?.port}</div>
        </div>
      ),
    },
    { type: 'divider' },
    ...(otherConnections.length > 0 ? [
      {
        type: 'group' as const,
        label: <span style={{ fontSize: 11, color: '#8c8c8c' }}>Switch Account</span>,
      },
      ...otherConnections.map((conn) => ({
        key: `switch-${conn.host}-${conn.port}-${conn.username}`,
        label: (
          <Space>
            <SwapOutlined style={{ color: '#5d87ff' }} />
            <span>{conn.username}@{conn.host}</span>
            <Tag style={{ fontSize: 10, padding: '0 4px', margin: 0 }} color={getDbTypeColor(conn.dbType)}>
              {conn.dbType}
            </Tag>
          </Space>
        ),
        onClick: () => handleSwitchClick(conn),
      })),
      { type: 'divider' as const },
    ] : []),
    {
      key: 'new-connection',
      label: (
        <Space>
          <PlusOutlined />
          <span>New Connection</span>
        </Space>
      ),
      onClick: () => {
        logout()
        navigate('/login')
      },
    },
    {
      key: 'logout',
      label: (
        <Space>
          <LogoutOutlined />
          <span>Logout</span>
        </Space>
      ),
      danger: true,
      onClick: logout,
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <SessionTimeout />
      <Sidebar collapsed={collapsed} onCollapse={setCollapsed} />
      <Layout style={{ marginLeft: collapsed ? 80 : 200, transition: 'margin-left 0.2s' }}>
        <Header
          style={{
            padding: '0 24px',
            background: '#fff',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            borderBottom: '1px solid #f0f0f0',
            boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
          }}
        >
          {/* Connection Info */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <Tag
              color={getDbTypeColor(user?.dbType)}
              style={{ margin: 0, fontWeight: 500 }}
            >
              {user?.dbType?.toUpperCase()}
            </Tag>
            <span style={{ fontSize: 13, color: '#8c8c8c' }}>
              {user?.host}:{user?.port}
            </span>
          </div>
          {user && (
            <Space size="middle">
              <SessionCountdown />
              <Space>
                <Tag
                  color={user.role === 'admin' ? 'purple' : 'default'}
                  style={{ margin: 0 }}
                >
                  {user.role.toUpperCase()}
                </Tag>
              </Space>
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight" trigger={['click']}>
                <Space style={{ cursor: 'pointer' }}>
                  <Avatar
                    size="small"
                    icon={<UserOutlined />}
                    style={{
                      background: '#3c4b64',
                    }}
                  />
                  <span style={{ fontWeight: 500 }}>{user.username}</span>
                </Space>
              </Dropdown>
            </Space>
          )}
        </Header>
        <Content style={{ margin: '24px 16px 0' }}>
          <div
            style={{
              padding: 24,
              minHeight: 360,
              background: colorBgContainer,
              borderRadius: borderRadiusLG,
            }}
          >
            <Outlet />
          </div>
        </Content>
        <Footer style={{ textAlign: 'center', padding: '16px 50px', background: 'transparent' }}>
          <span style={{ fontSize: 12, color: '#8c8c8c' }}>
            DONUT ©{new Date().getFullYear()} - Click, Not Command
            {' · '}
            <a
              href="https://github.com/amazingkj/dbaccman/issues"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: '#8c8c8c', textDecoration: 'underline' }}
            >
              Report an issue
            </a>
          </span>
        </Footer>
      </Layout>

      {/* Quick Switch Modal */}
      <Modal
        title={
          <Space>
            <SwapOutlined style={{ color: '#5d87ff' }} />
            <span>Switch Account</span>
          </Space>
        }
        open={switchModalOpen}
        onCancel={() => {
          setSwitchModalOpen(false)
          setSwitchTarget(null)
        }}
        footer={null}
        width={400}
      >
        {switchTarget && (
          <Form form={form} layout="vertical" onFinish={handleSwitch}>
            <div style={{
              padding: '16px',
              background: '#f5f7fa',
              borderRadius: 8,
              marginBottom: 16,
            }}>
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <UserOutlined style={{ color: '#5d87ff' }} />
                  <span style={{ fontWeight: 600 }}>{switchTarget.username}</span>
                  <Tag color={getDbTypeColor(switchTarget.dbType)} style={{ margin: 0 }}>
                    {switchTarget.dbType}
                  </Tag>
                </div>
                <div style={{ fontSize: 12, color: '#8c8c8c', marginLeft: 22 }}>
                  {switchTarget.host}:{switchTarget.port}
                  {switchTarget.database && ` / ${switchTarget.database}`}
                </div>
              </Space>
            </div>
            <Form.Item
              name="password"
              label="Password"
              rules={[{ required: true, message: 'Please enter your password' }]}
            >
              <Input.Password
                placeholder="Enter password to switch"
                autoFocus
                size="large"
              />
            </Form.Item>
            <Form.Item style={{ marginBottom: 0, marginTop: 16 }}>
              <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
                <button
                  type="button"
                  onClick={() => setSwitchModalOpen(false)}
                  style={{
                    padding: '8px 16px',
                    border: '1px solid #d9d9d9',
                    borderRadius: 6,
                    background: '#fff',
                    cursor: 'pointer',
                  }}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={switchLoading}
                  style={{
                    padding: '8px 24px',
                    border: 'none',
                    borderRadius: 6,
                    background: '#5d87ff',
                    color: '#fff',
                    cursor: 'pointer',
                    fontWeight: 500,
                  }}
                >
                  {switchLoading ? 'Switching...' : 'Switch'}
                </button>
              </Space>
            </Form.Item>
          </Form>
        )}
      </Modal>
    </Layout>
  )
}

export default MainLayout
