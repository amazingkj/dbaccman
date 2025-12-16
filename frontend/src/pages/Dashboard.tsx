import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Typography,
  Card,
  Row,
  Col,
  Statistic,
  List,
  Tag,
  Space,
  Button,
  Spin,
  Alert,
} from 'antd'
import {
  UserOutlined,
  DatabaseOutlined,
  ClockCircleOutlined,
  WarningOutlined,
  TableOutlined,
  ReloadOutlined,
  RightOutlined,
} from '@ant-design/icons'
import { accountsApi } from '../api/accounts'
import { sessionsApi } from '../api/sessions'
import { tablesApi } from '../api/tables'
import type { ExpiringAccount, SessionInfo, DatabaseInfo } from '../types'

const { Title, Text } = Typography

interface DashboardStats {
  totalAccounts: number
  activeSessions: number
  expiringSoon: number
  slowQueries: number
  totalDatabases: number
  totalTables: number
}

function Dashboard() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState<DashboardStats>({
    totalAccounts: 0,
    activeSessions: 0,
    expiringSoon: 0,
    slowQueries: 0,
    totalDatabases: 0,
    totalTables: 0,
  })
  const [expiringAccounts, setExpiringAccounts] = useState<ExpiringAccount[]>([])
  const [longRunningSessions, setLongRunningSessions] = useState<SessionInfo[]>([])
  const [databases, setDatabases] = useState<DatabaseInfo[]>([])

  const fetchDashboardData = async () => {
    setLoading(true)
    try {
      const [accountsRes, expiringRes, sessionsRes, dbRes] = await Promise.all([
        accountsApi.list(),
        accountsApi.getExpiring(30),
        sessionsApi.getActive(),
        tablesApi.getDatabases(),
      ])

      const sessions = sessionsRes.data
      const activeSessions = sessions.filter((s) => s.command !== 'Sleep')
      const slowQueries = sessions.filter((s) => s.time > 60 && s.command !== 'Sleep')

      setStats({
        totalAccounts: accountsRes.data.length,
        activeSessions: activeSessions.length,
        expiringSoon: expiringRes.data.length,
        slowQueries: slowQueries.length,
        totalDatabases: dbRes.data.length,
        totalTables: dbRes.data.reduce((sum, db) => sum + db.tableCount, 0),
      })

      setExpiringAccounts(expiringRes.data.slice(0, 5))
      setLongRunningSessions(slowQueries.slice(0, 5))
      setDatabases(dbRes.data.slice(0, 5))
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchDashboardData()
    const interval = setInterval(fetchDashboardData, 30000) // Refresh every 30 seconds
    return () => clearInterval(interval)
  }, [])

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px 0' }}>
        <Spin size="large" />
        <p style={{ marginTop: 16 }}>Loading dashboard...</p>
      </div>
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={2} style={{ margin: 0 }}>Dashboard</Title>
        <Button icon={<ReloadOutlined />} onClick={fetchDashboardData}>
          Refresh
        </Button>
      </div>

      {/* Stats Cards */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={4}>
          <Card hoverable onClick={() => navigate('/accounts')}>
            <Statistic
              title="Total Accounts"
              value={stats.totalAccounts}
              prefix={<UserOutlined />}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card hoverable onClick={() => navigate('/sessions')}>
            <Statistic
              title="Active Sessions"
              value={stats.activeSessions}
              valueStyle={{ color: '#1890ff' }}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card hoverable onClick={() => navigate('/accounts')}>
            <Statistic
              title="Expiring Soon"
              value={stats.expiringSoon}
              valueStyle={{ color: stats.expiringSoon > 0 ? '#faad14' : '#52c41a' }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card hoverable onClick={() => navigate('/sessions')}>
            <Statistic
              title="Slow Queries"
              value={stats.slowQueries}
              valueStyle={{ color: stats.slowQueries > 0 ? '#cf1322' : '#52c41a' }}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card hoverable onClick={() => navigate('/tables')}>
            <Statistic
              title="Databases"
              value={stats.totalDatabases}
              prefix={<DatabaseOutlined />}
            />
          </Card>
        </Col>
        <Col span={4}>
          <Card hoverable onClick={() => navigate('/tables')}>
            <Statistic
              title="Tables"
              value={stats.totalTables}
              prefix={<TableOutlined />}
            />
          </Card>
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
          style={{ marginBottom: 16 }}
        />
      )}

      {stats.slowQueries > 0 && (
        <Alert
          message="Slow Query Alert"
          description={`${stats.slowQueries} queries running for more than 60 seconds.`}
          type="error"
          showIcon
          action={
            <Button size="small" danger onClick={() => navigate('/sessions')}>
              View Sessions
            </Button>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      {/* Detail Cards */}
      <Row gutter={16}>
        <Col span={8}>
          <Card
            title={
              <Space>
                <WarningOutlined style={{ color: '#faad14' }} />
                Expiring Accounts
              </Space>
            }
            extra={
              <Button type="link" onClick={() => navigate('/accounts')}>
                View All <RightOutlined />
              </Button>
            }
          >
            {expiringAccounts.length > 0 ? (
              <List
                size="small"
                dataSource={expiringAccounts}
                renderItem={(item) => (
                  <List.Item>
                    <Space>
                      <UserOutlined />
                      <Text>{item.username}@{item.host}</Text>
                    </Space>
                    <Tag color={item.daysUntilExpiry <= 7 ? 'red' : 'orange'}>
                      {item.daysUntilExpiry} days
                    </Tag>
                  </List.Item>
                )}
              />
            ) : (
              <Text type="secondary">No accounts expiring soon</Text>
            )}
          </Card>
        </Col>

        <Col span={8}>
          <Card
            title={
              <Space>
                <ClockCircleOutlined style={{ color: '#cf1322' }} />
                Long Running Queries
              </Space>
            }
            extra={
              <Button type="link" onClick={() => navigate('/sessions')}>
                View All <RightOutlined />
              </Button>
            }
          >
            {longRunningSessions.length > 0 ? (
              <List
                size="small"
                dataSource={longRunningSessions}
                renderItem={(item) => (
                  <List.Item>
                    <Space>
                      <Text strong>PID {item.pid}</Text>
                      <Text type="secondary">{item.user}</Text>
                    </Space>
                    <Tag color="red">{Math.floor(item.time / 60)}m {item.time % 60}s</Tag>
                  </List.Item>
                )}
              />
            ) : (
              <Text type="secondary">No long running queries</Text>
            )}
          </Card>
        </Col>

        <Col span={8}>
          <Card
            title={
              <Space>
                <DatabaseOutlined style={{ color: '#1890ff' }} />
                Databases
              </Space>
            }
            extra={
              <Button type="link" onClick={() => navigate('/tables')}>
                View All <RightOutlined />
              </Button>
            }
          >
            {databases.length > 0 ? (
              <List
                size="small"
                dataSource={databases}
                renderItem={(item) => (
                  <List.Item>
                    <Space>
                      <DatabaseOutlined />
                      <Text>{item.name}</Text>
                    </Space>
                    <Tag color="blue">{item.tableCount} tables</Tag>
                  </List.Item>
                )}
              />
            ) : (
              <Text type="secondary">No databases found</Text>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default Dashboard
