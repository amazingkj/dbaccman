import { useState, useEffect } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { Layout, Tag, Space, Avatar, Dropdown, theme, Modal, Input, Form, message, Badge, Popover, Typography } from 'antd'
import { LogoutOutlined, UserOutlined, SwapOutlined, BellOutlined, WarningOutlined, ClockCircleOutlined, LockOutlined, RightOutlined, HddOutlined } from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { useAuthStore } from '../../store/authStore'
import { useConnectionStore } from '../../store/connectionStore'
import { useAuth } from '../../hooks/useAuth'
import { dashboardApi } from '../../api/dashboard'
import Sidebar from './Sidebar'
import SessionTimeout from '../common/SessionTimeout'
import SessionCountdown from '../common/SessionCountdown'
import type { RecentConnection, DashboardStats } from '../../types'

const { Text } = Typography

const { Header, Content, Footer } = Layout

function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const [switchModalOpen, setSwitchModalOpen] = useState(false)
  const [switchTarget, setSwitchTarget] = useState<RecentConnection | null>(null)
  const [switchLoading, setSwitchLoading] = useState(false)
  const [form] = Form.useForm()
  const [healthStats, setHealthStats] = useState<DashboardStats | null>(null)

  const { user } = useAuthStore()
  const { recentConnections } = useConnectionStore()
  const { logout, login } = useAuth()
  const navigate = useNavigate()
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken()

  const isAdmin = user?.role === 'admin'

  // Fetch health stats for admin users
  useEffect(() => {
    if (!isAdmin) return

    const fetchHealthStats = async () => {
      try {
        const response = await dashboardApi.getStats()
        setHealthStats(response.data)
      } catch {
        // Silently fail - not critical for layout
      }
    }

    fetchHealthStats()
    // Refresh every 2 minutes
    const interval = setInterval(fetchHealthStats, 120000)
    return () => clearInterval(interval)
  }, [isAdmin])

  // Calculate notification count and health color
  const notificationCount = healthStats
    ? (healthStats.expiringSoon || 0) + (healthStats.lockedAccounts || 0) + (healthStats.slowQueries || 0) + (healthStats.criticalTablespaces || 0)
    : 0

  const healthColor = healthStats?.healthScore?.status === 'healthy' ? '#52c41a'
    : healthStats?.healthScore?.status === 'warning' ? '#faad14' : '#ff4d4f'

  const getDbTypeTagProps = (dbType?: string) => {
    const type = dbType?.toUpperCase()
    switch (type) {
      case 'MYSQL':
        return { color: '#00758F' }
      case 'POSTGRESQL':
        return { color: '#336791' }
      case 'ORACLE':
        return {
          color: undefined,
          style: {
            color: '#F80000',
            borderColor: '#F80000',
            background: 'transparent'
          }
        }
      default:
        return { color: '#1890ff' }
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
            <Tag
              {...getDbTypeTagProps(conn.dbType)}
              style={{ fontSize: 10, padding: '0 4px', margin: 0, ...getDbTypeTagProps(conn.dbType).style }}
            >
              {conn.dbType}
            </Tag>
          </Space>
        ),
        onClick: () => handleSwitchClick(conn),
      })),
      { type: 'divider' as const },
    ] : []),
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
              {...getDbTypeTagProps(user?.dbType)}
              style={{ margin: 0, fontWeight: 500, ...getDbTypeTagProps(user?.dbType).style }}
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
              {/* Health Notifications Bell for Admin */}
              {isAdmin && (
                <Popover
                  placement="bottomRight"
                  trigger="click"
                  overlayInnerStyle={{ padding: 0 }}
                  content={
                    <div style={{ width: 340 }}>
                      {/* Header */}
                      <div
                        style={{
                          padding: '12px 16px',
                          background: `${healthColor}10`,
                          borderBottom: `1px solid ${healthColor}20`,
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <Space>
                          <WarningOutlined style={{ color: healthColor, fontSize: 16 }} />
                          <Text strong style={{ color: healthColor }}>System Health Alerts</Text>
                        </Space>
                      </div>
                      {/* Alert Items */}
                      <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                        {healthStats && notificationCount > 0 ? (
                          <>
                            {healthStats.expiringSoon > 0 && (
                              <div
                                style={{
                                  padding: '10px 16px',
                                  display: 'flex',
                                  justifyContent: 'space-between',
                                  alignItems: 'center',
                                  borderBottom: '1px solid #f0f0f0',
                                  cursor: 'pointer',
                                }}
                                onClick={() => navigate('/accounts?filter=expiring')}
                              >
                                <Space>
                                  <WarningOutlined style={{ color: '#faad14' }} />
                                  <div>
                                    <div style={{ fontWeight: 500, fontSize: 13 }}>Password Expiring</div>
                                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{healthStats.expiringSoon} account(s) within 30 days</div>
                                  </div>
                                </Space>
                                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                              </div>
                            )}
                            {healthStats.slowQueries > 0 && (
                              <div
                                style={{
                                  padding: '10px 16px',
                                  display: 'flex',
                                  justifyContent: 'space-between',
                                  alignItems: 'center',
                                  borderBottom: '1px solid #f0f0f0',
                                  cursor: 'pointer',
                                }}
                                onClick={() => navigate('/sessions')}
                              >
                                <Space>
                                  <ClockCircleOutlined style={{ color: '#ff4d4f' }} />
                                  <div>
                                    <div style={{ fontWeight: 500, fontSize: 13 }}>Slow Queries</div>
                                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{healthStats.slowQueries} queries &gt; 60 seconds</div>
                                  </div>
                                </Space>
                                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                              </div>
                            )}
                            {healthStats.lockedAccounts > 0 && (
                              <div
                                style={{
                                  padding: '10px 16px',
                                  display: 'flex',
                                  justifyContent: 'space-between',
                                  alignItems: 'center',
                                  borderBottom: '1px solid #f0f0f0',
                                  cursor: 'pointer',
                                }}
                                onClick={() => navigate('/accounts?filter=locked')}
                              >
                                <Space>
                                  <LockOutlined style={{ color: '#ff4d4f' }} />
                                  <div>
                                    <div style={{ fontWeight: 500, fontSize: 13 }}>Locked Accounts</div>
                                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{healthStats.lockedAccounts} account(s) locked</div>
                                  </div>
                                </Space>
                                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                              </div>
                            )}
                            {healthStats.criticalTablespaces > 0 && (
                              <div
                                style={{
                                  padding: '10px 16px',
                                  display: 'flex',
                                  justifyContent: 'space-between',
                                  alignItems: 'center',
                                  cursor: 'pointer',
                                }}
                                onClick={() => navigate('/tablespaces?sort=usage')}
                              >
                                <Space>
                                  <HddOutlined style={{ color: '#fa8c16' }} />
                                  <div>
                                    <div style={{ fontWeight: 500, fontSize: 13 }}>Storage Critical</div>
                                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{healthStats.criticalTablespaces} tablespace(s) &gt; 90%</div>
                                  </div>
                                </Space>
                                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
                              </div>
                            )}
                          </>
                        ) : (
                          <div style={{ padding: '24px 16px', textAlign: 'center' }}>
                            <Text type="secondary">No alerts - System is healthy</Text>
                          </div>
                        )}
                      </div>
                    </div>
                  }
                >
                  <Badge count={notificationCount} size="small" offset={[-2, 2]}>
                    <BellOutlined
                      style={{
                        fontSize: 18,
                        cursor: 'pointer',
                        color: notificationCount > 0 ? '#faad14' : '#8c8c8c',
                      }}
                    />
                  </Badge>
                </Popover>
              )}
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
                  <Tag
                    {...getDbTypeTagProps(switchTarget.dbType)}
                    style={{ margin: 0, ...getDbTypeTagProps(switchTarget.dbType).style }}
                  >
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
