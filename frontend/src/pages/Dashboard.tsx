import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Typography,
  Card,
  Row,
  Col,
  List,
  Tag,
  Space,
  Button,
  Spin,
  Alert,
  Progress,
  Tooltip,
  Divider,
} from 'antd'
import {
  UserOutlined,
  DatabaseOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  TableOutlined,
  ReloadOutlined,
  RightOutlined,
  ThunderboltOutlined,
  SafetyOutlined,
  DashboardOutlined,
  LockOutlined,
  HddOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../store/authStore'
import { dashboardApi } from '../api/dashboard'
import UserDashboard from './UserDashboard'
import type { DashboardStats } from '../types'

const { Title, Text } = Typography

// Modernize-style stat card styles (white background with colored icons)
const statCardStyles = {
  accounts: {
    color: '#5d87ff',
    bgColor: 'rgba(93, 135, 255, 0.1)',
    icon: <UserOutlined />,
  },
  sessions: {
    color: '#49beff',
    bgColor: 'rgba(73, 190, 255, 0.1)',
    icon: <ThunderboltOutlined />,
  },
  expiring: {
    color: '#ffae1f',
    bgColor: 'rgba(255, 174, 31, 0.1)',
    icon: <WarningOutlined />,
  },
  locked: {
    color: '#ff6b6b',
    bgColor: 'rgba(255, 107, 107, 0.1)',
    icon: <LockOutlined />,
  },
  slowQueries: {
    color: '#fa896b',
    bgColor: 'rgba(250, 137, 107, 0.1)',
    icon: <ClockCircleOutlined />,
  },
  storage: {
    color: '#845ef7',
    bgColor: 'rgba(132, 94, 247, 0.1)',
    icon: <HddOutlined />,
  },
  databases: {
    color: '#13deb9',
    bgColor: 'rgba(19, 222, 185, 0.1)',
    icon: <DatabaseOutlined />,
  },
  tables: {
    color: '#5d87ff',
    bgColor: 'rgba(93, 135, 255, 0.1)',
    icon: <TableOutlined />,
  },
}

interface StatCardProps {
  title: string
  value: number
  style: { color: string; bgColor: string; icon: React.ReactNode }
  onClick?: () => void
  suffix?: string
  description?: string
}

function StatCard({ title, value, style, onClick, suffix, description }: StatCardProps) {
  return (
    <Card
      hoverable
      onClick={onClick}
      style={{
        background: '#fff',
        borderRadius: 12,
        border: 'none',
        cursor: onClick ? 'pointer' : 'default',
        overflow: 'hidden',
        height: '100%',
        boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
      }}
      styles={{
        body: {
          padding: '20px',
          height: 110,
          display: 'flex',
          alignItems: 'center',
          gap: 16,
        }
      }}
    >
      {/* Icon Container */}
      <div style={{
        width: 56,
        height: 56,
        borderRadius: 12,
        background: style.bgColor,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 24,
        color: style.color,
        flexShrink: 0,
      }}>
        {style.icon}
      </div>

      {/* Content */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 13,
          color: '#5a6a85',
          fontWeight: 500,
          marginBottom: 4,
        }}>
          {title}
        </div>
        <div style={{
          fontSize: 24,
          fontWeight: 600,
          color: '#2a3547',
          lineHeight: 1.2,
        }}>
          {value.toLocaleString()}{suffix && <span style={{ fontSize: 14, marginLeft: 4, color: '#5a6a85' }}>{suffix}</span>}
        </div>
        {description && (
          <div style={{
            fontSize: 12,
            color: style.color,
            marginTop: 4,
            fontWeight: 500,
          }}>
            {description}
          </div>
        )}
      </div>
    </Card>
  )
}

function AdminDashboard() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState<DashboardStats | null>(null)

  const fetchDashboardData = async () => {
    setLoading(true)
    try {
      const response = await dashboardApi.getStats()
      setStats(response.data)
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDashboardData()
  }, [])

  if (loading || !stats) {
    return (
      <div style={{ textAlign: 'center', padding: '100px 0' }}>
        <Spin size="large" />
        <p style={{ marginTop: 16, color: '#8c8c8c' }}>Loading dashboard...</p>
      </div>
    )
  }

  // Use backend health score
  const { healthScore } = stats
  const healthColor = healthScore.status === 'healthy' ? '#52c41a'
    : healthScore.status === 'warning' ? '#faad14' : '#ff4d4f'

  // Health score tooltip content
  const healthTooltipContent = (
    <div style={{ minWidth: 250 }}>
      <div style={{ fontWeight: 600, marginBottom: 8 }}>Health Score Breakdown</div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
        <span><UserOutlined /> Account</span>
        <span style={{ fontWeight: 500 }}>{healthScore.accountScore}/40</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
        <span><ThunderboltOutlined /> Session</span>
        <span style={{ fontWeight: 500 }}>{healthScore.sessionScore}/30</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
        <span><HddOutlined /> Storage</span>
        <span style={{ fontWeight: 500 }}>{healthScore.storageScore}/30</span>
      </div>
      {healthScore.issues.length > 0 && (
        <>
          <Divider style={{ margin: '8px 0', borderColor: 'rgba(255,255,255,0.2)' }} />
          <div style={{ fontWeight: 600, marginBottom: 4 }}>Issues</div>
          {healthScore.issues.map((issue, idx) => (
            <div key={idx} style={{ fontSize: 12, color: 'rgba(255,255,255,0.85)' }}>
              • {issue}
            </div>
          ))}
        </>
      )}
    </div>
  )

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
            <DashboardOutlined style={{ marginRight: 12 }} />
            Dashboard
          </Title>
          <Text type="secondary">Database account management overview</Text>
        </div>
        <Button
          icon={<ReloadOutlined />}
          onClick={fetchDashboardData}
        >
          Refresh
        </Button>
      </div>

      {/* Stats Cards - CoreUI Style */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <StatCard
            title="Total Accounts"
            value={stats.totalAccounts}
            style={statCardStyles.accounts}
            onClick={() => navigate('/accounts')}
          />
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <StatCard
            title="Active Sessions"
            value={stats.activeSessions}
            style={statCardStyles.sessions}
            onClick={() => navigate('/sessions')}
          />
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <StatCard
            title="Expiring Soon"
            value={stats.expiringSoon}
            style={statCardStyles.expiring}
            onClick={() => navigate('/accounts')}
            description={stats.expiringSoon > 0 ? 'Needs attention' : 'All good'}
          />
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <StatCard
            title="Slow Queries"
            value={stats.slowQueries}
            style={statCardStyles.slowQueries}
            onClick={() => navigate('/sessions?filter=slow')}
            description={stats.slowQueries > 0 ? '> 60 seconds' : 'No issues'}
          />
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <StatCard
            title="Databases"
            value={stats.totalDatabases}
            style={statCardStyles.databases}
            onClick={() => navigate('/tables')}
          />
        </Col>
        <Col xs={24} sm={12} lg={8} xl={4}>
          <StatCard
            title="Tables"
            value={stats.totalTables}
            style={statCardStyles.tables}
            onClick={() => navigate('/tables')}
          />
        </Col>
      </Row>

      {/* Health Score Card */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={24}>
          <Tooltip title={healthTooltipContent} placement="bottom" overlayStyle={{ maxWidth: 320 }}>
            <Card
              style={{ borderRadius: 8, cursor: 'pointer' }}
              styles={{ body: { padding: '16px 24px' } }}
            >
              <Row align="middle" gutter={24}>
                <Col>
                  <SafetyOutlined style={{ fontSize: 32, color: healthColor }} />
                </Col>
                <Col flex="auto">
                  <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                    <div>
                      <Space>
                        <Text strong style={{ fontSize: 16 }}>System Health</Text>
                        <InfoCircleOutlined style={{ color: '#8c8c8c', fontSize: 14 }} />
                      </Space>
                      <div style={{ color: '#8c8c8c', fontSize: 12 }}>
                        Account ({healthScore.accountScore}/40) · Session ({healthScore.sessionScore}/30) · Storage ({healthScore.storageScore}/30)
                      </div>
                    </div>
                    <Progress
                      percent={healthScore.total}
                      strokeColor={healthColor}
                      style={{ width: 200, margin: 0 }}
                      format={(percent) => (
                        <span style={{ color: healthColor, fontWeight: 600 }}>{percent}%</span>
                      )}
                    />
                  </div>
                </Col>
                <Col>
                  <Space>
                    {healthScore.issues.length === 0 ? (
                      <Tag color="success" style={{ margin: 0 }}>All Systems Normal</Tag>
                    ) : (
                      <>
                        {stats.expiringSoon > 0 && (
                          <Tag color="warning" style={{ margin: 0 }}>
                            {stats.expiringSoon} expiring
                          </Tag>
                        )}
                        {stats.lockedAccounts > 0 && (
                          <Tag color="error" style={{ margin: 0 }}>
                            {stats.lockedAccounts} locked
                          </Tag>
                        )}
                        {stats.slowQueries > 0 && (
                          <Tag color="error" style={{ margin: 0 }}>
                            {stats.slowQueries} slow
                          </Tag>
                        )}
                        {stats.criticalTablespaces > 0 && (
                          <Tag color="volcano" style={{ margin: 0 }}>
                            {stats.criticalTablespaces} storage critical
                          </Tag>
                        )}
                      </>
                    )}
                  </Space>
                </Col>
              </Row>
            </Card>
          </Tooltip>
        </Col>
      </Row>

      {/* Alerts */}
      {stats.expiringSoon > 0 && (
        <Alert
          message="Password Expiration Warning"
          description={`${stats.expiringSoon} account(s) will expire within 30 days. Please take action.`}
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          action={
            <Button size="small" onClick={() => navigate('/accounts')}>
              View Accounts
            </Button>
          }
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      )}

      {stats.slowQueries > 0 && (
        <Alert
          message="Slow Query Alert"
          description={`${stats.slowQueries} queries running for more than 60 seconds.`}
          type="error"
          showIcon
          action={
            <Button size="small" danger onClick={() => navigate('/sessions?filter=slow')}>
              View Sessions
            </Button>
          }
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      )}

      {stats.lockedAccounts > 0 && (
        <Alert
          message="Locked Accounts"
          description={`${stats.lockedAccounts} account(s) are currently locked.`}
          type="error"
          showIcon
          icon={<LockOutlined />}
          action={
            <Button size="small" danger onClick={() => navigate('/accounts?filter=locked')}>
              View Accounts
            </Button>
          }
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      )}

      {stats.criticalTablespaces > 0 && (
        <Alert
          message="Storage Critical"
          description={`${stats.criticalTablespaces} tablespace(s) usage exceeded 90%.`}
          type="warning"
          showIcon
          icon={<HddOutlined />}
          action={
            <Button size="small" onClick={() => navigate('/tablespaces')}>
              View Tablespaces
            </Button>
          }
          style={{ marginBottom: 16, borderRadius: 8 }}
        />
      )}

      {/* Detail Cards */}
      <Row gutter={16}>
        <Col xs={24} lg={8}>
          <Card
            title={
              <Space>
                <WarningOutlined style={{ color: '#faad14' }} />
                <span>Expiring Accounts</span>
              </Space>
            }
            extra={
              <Button type="link" size="small" onClick={() => navigate('/accounts')}>
                View All <RightOutlined />
              </Button>
            }
            style={{ borderRadius: 8, height: '100%' }}
            styles={{ body: { padding: '12px 16px' } }}
          >
            {stats.expiringAccounts.length > 0 ? (
              <List
                size="small"
                dataSource={stats.expiringAccounts}
                renderItem={(item) => (
                  <List.Item style={{ padding: '8px 0' }}>
                    <Space>
                      <UserOutlined style={{ color: '#8c8c8c' }} />
                      <Text ellipsis style={{ maxWidth: 150 }}>
                        {item.username}@{item.host}
                      </Text>
                    </Space>
                    <Tag
                      color={item.daysUntilExpiry <= 7 ? 'error' : 'warning'}
                      style={{ margin: 0, borderRadius: 4 }}
                    >
                      {item.daysUntilExpiry}d
                    </Tag>
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: '24px 0', color: '#8c8c8c' }}>
                <SafetyOutlined style={{ fontSize: 32, marginBottom: 8, display: 'block' }} />
                No accounts expiring soon
              </div>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title={
              <Space>
                <ClockCircleOutlined style={{ color: '#ff4d4f' }} />
                <span>Slow Queries</span>
              </Space>
            }
            extra={
              <Button type="link" size="small" onClick={() => navigate('/sessions')}>
                View All <RightOutlined />
              </Button>
            }
            style={{ borderRadius: 8, height: '100%' }}
            styles={{ body: { padding: '12px 16px' } }}
          >
            {stats.longRunningSessions.length > 0 ? (
              <List
                size="small"
                dataSource={stats.longRunningSessions}
                renderItem={(item) => (
                  <List.Item style={{ padding: '8px 0' }}>
                    <Space>
                      <Text strong style={{ fontFamily: 'monospace' }}>
                        PID {item.pid}
                      </Text>
                      <Text type="secondary" ellipsis style={{ maxWidth: 80 }}>
                        {item.user}
                      </Text>
                    </Space>
                    <Tag color="error" style={{ margin: 0, borderRadius: 4 }}>
                      {Math.floor(item.time / 60)}m {item.time % 60}s
                    </Tag>
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: '24px 0', color: '#8c8c8c' }}>
                <ThunderboltOutlined style={{ fontSize: 32, marginBottom: 8, display: 'block' }} />
                No slow queries
              </div>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title={
              <Space>
                <DatabaseOutlined style={{ color: '#1890ff' }} />
                <span>Top Databases</span>
              </Space>
            }
            extra={
              <Button type="link" size="small" onClick={() => navigate('/tables')}>
                View All <RightOutlined />
              </Button>
            }
            style={{ borderRadius: 8, height: '100%' }}
            styles={{ body: { padding: '12px 16px' } }}
          >
            {stats.topDatabases.length > 0 ? (
              <List
                size="small"
                dataSource={stats.topDatabases}
                renderItem={(item) => (
                  <List.Item
                    style={{ padding: '8px 0', cursor: 'pointer' }}
                    onClick={() => navigate(`/tables?db=${encodeURIComponent(item.name)}`)}
                  >
                    <Space>
                      <DatabaseOutlined style={{ color: '#1890ff' }} />
                      <Text ellipsis style={{ maxWidth: 150 }}>{item.name}</Text>
                    </Space>
                    <Tag color="blue" style={{ margin: 0, borderRadius: 4 }}>
                      {item.tableCount} tables
                    </Tag>
                  </List.Item>
                )}
              />
            ) : (
              <div style={{ textAlign: 'center', padding: '24px 0', color: '#8c8c8c' }}>
                <DatabaseOutlined style={{ fontSize: 32, marginBottom: 8, display: 'block' }} />
                No databases found
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}

function Dashboard() {
  const { user } = useAuthStore()

  if (user?.role === 'admin') {
    return <AdminDashboard />
  }

  return <UserDashboard />
}

export default Dashboard
