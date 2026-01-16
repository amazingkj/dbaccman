import { useState, useEffect, useCallback } from 'react'
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
  Progress,
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
  CloseOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../store/authStore'
import { dashboardApi } from '../api/dashboard'
import { StatCard, STAT_CARD_STYLES } from '../components/common/StatCard'
import { PageLoading } from '../components/common/PageLoading'
import { handleApiError } from '../utils/errors'
import UserDashboard from './UserDashboard'
import type { DashboardStats } from '../types'

const { Title, Text } = Typography

// Stat card style configurations - using shared STAT_CARD_STYLES constants
const statCardStyles = {
  accounts: { ...STAT_CARD_STYLES.primary, icon: <UserOutlined /> },
  sessions: { ...STAT_CARD_STYLES.info, icon: <ThunderboltOutlined /> },
  expiring: { ...STAT_CARD_STYLES.warning, icon: <WarningOutlined /> },
  locked: { ...STAT_CARD_STYLES.red, icon: <LockOutlined /> },
  slowQueries: { ...STAT_CARD_STYLES.danger, icon: <ClockCircleOutlined /> },
  storage: { ...STAT_CARD_STYLES.purple, icon: <HddOutlined /> },
  databases: { ...STAT_CARD_STYLES.success, icon: <DatabaseOutlined /> },
  tables: { ...STAT_CARD_STYLES.primary, icon: <TableOutlined />,
  },
}

function AdminDashboard() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [alertsDismissed, setAlertsDismissed] = useState(() => {
    return sessionStorage.getItem('healthAlertsDismissed') === 'true'
  })

  const handleDismissAlerts = () => {
    setAlertsDismissed(true)
    sessionStorage.setItem('healthAlertsDismissed', 'true')
  }

  const fetchDashboardData = useCallback(async () => {
    setLoading(true)
    try {
      const response = await dashboardApi.getStats()
      setStats(response.data)
    } catch (error) {
      handleApiError(error, { defaultMessage: 'Failed to load dashboard data' })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchDashboardData()
  }, [])

  if (loading || !stats) {
    return <PageLoading message="Loading dashboard..." type="cards" />
  }

  // Use backend health score
  const { healthScore } = stats
  const healthColor = healthScore.status === 'healthy' ? '#52c41a'
    : healthScore.status === 'warning' ? '#faad14' : '#ff4d4f'

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
            onClick={() => navigate('/accounts?filter=expiring')}
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
          <Card
              style={{ borderRadius: 8 }}
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
        </Col>
      </Row>

      {/* System Health Notification Popup (우상단) */}
      {!alertsDismissed && healthScore.issues.length > 0 && (
        <div
          style={{
            position: 'fixed',
            top: 80,
            right: 24,
            width: 360,
            zIndex: 1000,
            background: '#fff',
            borderRadius: 12,
            boxShadow: '0 6px 16px rgba(0, 0, 0, 0.12)',
            border: `1px solid ${healthColor}20`,
            overflow: 'hidden',
          }}
        >
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
            <Button
              type="text"
              size="small"
              icon={<CloseOutlined />}
              onClick={handleDismissAlerts}
              style={{ color: '#8c8c8c' }}
            />
          </div>
          {/* Alert Items */}
          <div style={{ padding: '8px 0', maxHeight: 300, overflowY: 'auto' }}>
            {stats.expiringSoon > 0 && (
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
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{stats.expiringSoon} account(s) within 30 days</div>
                  </div>
                </Space>
                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
              </div>
            )}
            {stats.slowQueries > 0 && (
              <div
                style={{
                  padding: '10px 16px',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  borderBottom: '1px solid #f0f0f0',
                  cursor: 'pointer',
                }}
                onClick={() => navigate('/sessions?filter=slow')}
              >
                <Space>
                  <ClockCircleOutlined style={{ color: '#ff4d4f' }} />
                  <div>
                    <div style={{ fontWeight: 500, fontSize: 13 }}>Slow Queries</div>
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{stats.slowQueries} queries &gt; 60 seconds</div>
                  </div>
                </Space>
                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
              </div>
            )}
            {stats.lockedAccounts > 0 && (
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
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{stats.lockedAccounts} account(s) locked</div>
                  </div>
                </Space>
                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
              </div>
            )}
            {stats.criticalTablespaces > 0 && (
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
                    <div style={{ fontSize: 12, color: '#8c8c8c' }}>{stats.criticalTablespaces} tablespace(s) &gt; 90%</div>
                  </div>
                </Space>
                <RightOutlined style={{ color: '#8c8c8c', fontSize: 12 }} />
              </div>
            )}
          </div>
        </div>
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
                renderItem={(item) => {
                  const formatSize = (bytes: number) => {
                    if (bytes < 1024) return `${bytes} B`
                    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
                    if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
                    return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
                  }
                  return (
                    <List.Item
                      style={{ padding: '8px 0', cursor: 'pointer' }}
                      onClick={() => navigate(`/tables?db=${encodeURIComponent(item.name)}`)}
                    >
                      <Space>
                        <DatabaseOutlined style={{ color: '#1890ff' }} />
                        <Text ellipsis style={{ maxWidth: 100 }}>{item.name}</Text>
                      </Space>
                      <Space size={4}>
                        <Tag color="blue" style={{ margin: 0, borderRadius: 4, fontSize: 11 }}>
                          {item.tableCount} tbl
                        </Tag>
                        <Tag color="green" style={{ margin: 0, borderRadius: 4, fontSize: 11 }}>
                          {item.totalRows.toLocaleString()} rows
                        </Tag>
                        <Tag color="purple" style={{ margin: 0, borderRadius: 4, fontSize: 11 }}>
                          {formatSize(item.size)}
                        </Tag>
                      </Space>
                    </List.Item>
                  )
                }}
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
