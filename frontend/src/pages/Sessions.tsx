import { useState, useEffect, useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Typography,
  Table,
  Button,
  Space,
  Tag,
  Popconfirm,
  message,
  Card,
  Row,
  Col,
  Switch,
  Tooltip,
  Modal,
  Tabs,
  Badge,
  Input,
  Select,
} from 'antd'
import {
  ReloadOutlined,
  StopOutlined,
  ClockCircleOutlined,
  UserOutlined,
  DatabaseOutlined,
  EyeOutlined,
  ThunderboltOutlined,
  PauseCircleOutlined,
  DashboardOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { sessionsApi } from '../api/sessions'
import type { SessionInfo } from '../types'

const { Title, Text, Paragraph } = Typography

// Modernize-style stat card styles (white background with colored icons)
const statCardStyles = {
  total: {
    color: '#5d87ff',
    bgColor: 'rgba(93, 135, 255, 0.1)',
    icon: <DashboardOutlined />,
  },
  active: {
    color: '#49beff',
    bgColor: 'rgba(73, 190, 255, 0.1)',
    icon: <ThunderboltOutlined />,
  },
  sleeping: {
    color: '#7c8fac',
    bgColor: 'rgba(124, 143, 172, 0.1)',
    icon: <PauseCircleOutlined />,
  },
  longRunning: {
    color: '#fa896b',
    bgColor: 'rgba(250, 137, 107, 0.1)',
    icon: <WarningOutlined />,
  },
}

interface StatCardProps {
  title: string
  value: number
  style: { color: string; bgColor: string; icon: React.ReactNode }
}

function StatCard({ title, value, style }: StatCardProps) {
  return (
    <Card
      style={{
        background: '#fff',
        borderRadius: 12,
        border: 'none',
        height: '100%',
        boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
      }}
      styles={{
        body: {
          padding: '16px 20px',
          height: 90,
          display: 'flex',
          alignItems: 'center',
          gap: 14,
        }
      }}
    >
      <div style={{
        width: 48,
        height: 48,
        borderRadius: 10,
        background: style.bgColor,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 22,
        color: style.color,
        flexShrink: 0,
      }}>
        {style.icon}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          fontSize: 12,
          color: '#5a6a85',
          fontWeight: 500,
          marginBottom: 2,
        }}>
          {title}
        </div>
        <div style={{
          fontSize: 22,
          fontWeight: 600,
          color: '#2a3547',
          lineHeight: 1.2,
        }}>
          {value.toLocaleString()}
        </div>
      </div>
    </Card>
  )
}

// Simple SQL formatter for pretty display
const formatSQL = (sql: string): string => {
  if (!sql) return sql

  const keywords = [
    'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'JOIN', 'LEFT JOIN', 'RIGHT JOIN',
    'INNER JOIN', 'OUTER JOIN', 'ON', 'GROUP BY', 'ORDER BY', 'HAVING',
    'LIMIT', 'OFFSET', 'INSERT INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE FROM',
    'CREATE TABLE', 'ALTER TABLE', 'DROP TABLE', 'CREATE INDEX', 'UNION', 'UNION ALL'
  ]

  let formatted = sql.trim()

  // Add newlines before major keywords
  keywords.forEach(keyword => {
    const regex = new RegExp(`\\s+(${keyword})\\s+`, 'gi')
    formatted = formatted.replace(regex, `\n${keyword} `)
  })

  // Handle SELECT at the start
  if (formatted.toUpperCase().startsWith('SELECT')) {
    formatted = 'SELECT' + formatted.substring(6)
  }

  // Add indentation after SELECT for columns
  formatted = formatted.replace(/SELECT\s+/gi, 'SELECT\n    ')
  formatted = formatted.replace(/,\s*/g, ',\n    ')

  // Clean up FROM clause
  formatted = formatted.replace(/\n\s*FROM/gi, '\nFROM')
  formatted = formatted.replace(/\n\s*WHERE/gi, '\nWHERE')
  formatted = formatted.replace(/\n\s*AND/gi, '\n  AND')
  formatted = formatted.replace(/\n\s*OR/gi, '\n  OR')
  formatted = formatted.replace(/\n\s*ORDER BY/gi, '\nORDER BY')
  formatted = formatted.replace(/\n\s*GROUP BY/gi, '\nGROUP BY')

  return formatted
}

function Sessions() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [sessions, setSessions] = useState<SessionInfo[]>([])
  const [loading, setLoading] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [selectedQuery, setSelectedQuery] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<'query' | 'connection'>('query')
  const [searchText, setSearchText] = useState('')
  const [searchColumn, setSearchColumn] = useState<string>('all')
  const [showSlowOnly, setShowSlowOnly] = useState(searchParams.get('filter') === 'slow')

  const fetchSessions = useCallback(async () => {
    setLoading(true)
    try {
      const response = await sessionsApi.getActive()
      setSessions(response.data)
    } catch {
      message.error('Failed to fetch sessions')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchSessions()
  }, [fetchSessions])

  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | null = null
    if (autoRefresh) {
      interval = setInterval(fetchSessions, 5000)
    }
    return () => {
      if (interval) clearInterval(interval)
    }
  }, [autoRefresh, fetchSessions])

  const handleKillSession = async (pid: number, serialNum?: number | null) => {
    try {
      await sessionsApi.kill(pid, serialNum)
      message.success('Session killed successfully')
      fetchSessions()
    } catch (error: unknown) {
      const axiosError = error as { response?: { data?: { error?: string } } }
      const errorMessage = axiosError.response?.data?.error || 'Failed to kill session'
      message.error(errorMessage)
    }
  }

  const formatTime = (seconds: number): string => {
    if (seconds < 60) return `${seconds}s`
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
    if (seconds < 86400) {
      const hours = Math.floor(seconds / 3600)
      const mins = Math.floor((seconds % 3600) / 60)
      return `${hours}h ${mins}m`
    }
    const days = Math.floor(seconds / 86400)
    const hours = Math.floor((seconds % 86400) / 3600)
    return `${days}d ${hours}h`
  }

  const getCommandColor = (command: string): string => {
    switch (command) {
      case 'Query':
      case 'ACTIVE':
      case 'active':  // PostgreSQL
        return 'blue'
      case 'Sleep':
      case 'INACTIVE':
      case 'idle':  // PostgreSQL
        return 'default'
      case 'Connect':
        return 'green'
      case 'Killed':
        return 'red'
      case 'idle in transaction':  // PostgreSQL
        return 'orange'
      default:
        return 'orange'
    }
  }

  // Long running threshold: 30 minutes (1800 seconds)
  const LONG_RUNNING_THRESHOLD = 1800

  // Idle session states: MySQL uses 'Sleep', Oracle uses 'INACTIVE', PostgreSQL uses 'idle'
  const isIdleSession = (command: string) =>
    command === 'Sleep' || command === 'INACTIVE' || command === 'idle'

  // Slow query threshold: 60 seconds (matching Dashboard definition)
  const SLOW_QUERY_THRESHOLD = 60

  // Search filter function (memoized)
  const filterBySearch = useCallback((session: SessionInfo) => {
    if (!searchText) return true
    const search = searchText.toLowerCase()

    if (searchColumn === 'all') {
      return (
        session.pid.toString().includes(search) ||
        session.user?.toLowerCase().includes(search) ||
        session.host?.toLowerCase().includes(search) ||
        session.database?.toLowerCase().includes(search) ||
        session.command?.toLowerCase().includes(search) ||
        session.state?.toLowerCase().includes(search) ||
        session.query?.toLowerCase().includes(search)
      )
    }

    const value = session[searchColumn as keyof SessionInfo]
    if (value === null || value === undefined) return false
    return value.toString().toLowerCase().includes(search)
  }, [searchText, searchColumn])

  // Memoize filtered sessions
  const querySessions = useMemo(() => sessions.filter((s) => {
    const isQuery = !isIdleSession(s.command)
    if (!isQuery) return false
    if (!filterBySearch(s)) return false
    if (showSlowOnly && s.time <= SLOW_QUERY_THRESHOLD) return false
    return true
  }), [sessions, filterBySearch, showSlowOnly])

  const connectionSessions = useMemo(
    () => sessions.filter((s) => isIdleSession(s.command) && filterBySearch(s)),
    [sessions, filterBySearch]
  )

  // Memoize stats
  const stats = useMemo(() => ({
    total: sessions.length,
    active: querySessions.length,
    sleeping: connectionSessions.length,
    longRunning: sessions.filter((s) => s.time > LONG_RUNNING_THRESHOLD && !isIdleSession(s.command)).length,
  }), [sessions, querySessions.length, connectionSessions.length])

  // Memoize columns
  const columns = useMemo(() => [
    {
      title: 'PID',
      dataIndex: 'pid',
      key: 'pid',
      width: 70,
      sorter: (a: SessionInfo, b: SessionInfo) => a.pid - b.pid,
    },
    {
      title: 'User',
      dataIndex: 'user',
      key: 'user',
      width: 120,
      ellipsis: true,
      render: (user: string) => (
        <Space>
          <UserOutlined />
          {user}
        </Space>
      ),
    },
    {
      title: 'Host',
      dataIndex: 'host',
      key: 'host',
      width: 150,
      ellipsis: true,
    },
    {
      title: 'Database',
      dataIndex: 'database',
      key: 'database',
      width: 130,
      ellipsis: true,
      render: (db: string | null) =>
        db ? (
          <Tag icon={<DatabaseOutlined />} color="blue">
            {db}
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: 'Command',
      dataIndex: 'command',
      key: 'command',
      width: 100,
      render: (command: string) => (
        <Tag color={getCommandColor(command)}>{command}</Tag>
      ),
    },
    {
      title: 'Time',
      dataIndex: 'time',
      key: 'time',
      width: 100,
      render: (time: number) => {
        const isLong = time > LONG_RUNNING_THRESHOLD
        return (
          <Tooltip title={`${time} seconds`}>
            <Tag color={isLong ? 'red' : 'default'} icon={<ClockCircleOutlined />}>
              {formatTime(time)}
            </Tag>
          </Tooltip>
        )
      },
      sorter: (a: SessionInfo, b: SessionInfo) => a.time - b.time,
      defaultSortOrder: 'descend' as const,
    },
    {
      title: 'State',
      dataIndex: 'state',
      key: 'state',
      width: 120,
      ellipsis: true,
      render: (state: string | null) => state || '-',
    },
    {
      title: 'Query',
      dataIndex: 'query',
      key: 'query',
      width: 180,
      ellipsis: true,
      render: (query: string | null) =>
        query && query.trim() ? (
          <Tooltip title="Click to view full query">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={(e) => {
                e.stopPropagation()
                setSelectedQuery(query)
              }}
            >
              {query.length > 30 ? `${query.substring(0, 30)}...` : query}
            </Button>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 80,
      render: (_: unknown, record: SessionInfo) => (
        <Popconfirm
          title="Kill Session"
          description={`Are you sure you want to kill session ${record.pid}?`}
          onConfirm={() => handleKillSession(record.pid, record.serialNum)}
          okText="Yes"
          cancelText="No"
        >
          <Button type="link" danger icon={<StopOutlined />}>
            Kill
          </Button>
        </Popconfirm>
      ),
    },
  ], [])

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
            <ThunderboltOutlined style={{ marginRight: 12 }} />
            Sessions
          </Title>
          <Text type="secondary">Active database sessions and queries</Text>
        </div>
      </div>

      {/* Stats Cards - CoreUI Style */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Sessions"
            value={stats.total}
            style={statCardStyles.total}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Active Sessions"
            value={stats.active}
            style={statCardStyles.active}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Sleeping"
            value={stats.sleeping}
            style={statCardStyles.sleeping}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Long Running (>30m)"
            value={stats.longRunning}
            style={statCardStyles.longRunning}
          />
        </Col>
      </Row>

      <Row gutter={16} align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchSessions} loading={loading}>
              Refresh
            </Button>
            <Space>
              <Text>Auto-refresh (5s):</Text>
              <Switch checked={autoRefresh} onChange={setAutoRefresh} />
            </Space>
            <Space>
              <Text>Slow Only (&gt;60s):</Text>
              <Switch
                checked={showSlowOnly}
                onChange={(checked) => {
                  setShowSlowOnly(checked)
                  if (checked) {
                    setSearchParams({ filter: 'slow' })
                  } else {
                    setSearchParams({})
                  }
                }}
              />
            </Space>
          </Space>
        </Col>
        <Col flex="auto" style={{ textAlign: 'right' }}>
          <Space>
            <Select
              value={searchColumn}
              onChange={setSearchColumn}
              style={{ width: 120 }}
              size="middle"
            >
              <Select.Option value="all">All Columns</Select.Option>
              <Select.Option value="user">User</Select.Option>
              <Select.Option value="host">Host</Select.Option>
              <Select.Option value="database">Database</Select.Option>
              <Select.Option value="query">Query</Select.Option>
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

      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as 'query' | 'connection')}
        items={[
          {
            key: 'query',
            label: (
              <span>
                <ThunderboltOutlined />
                Query Sessions
                <Badge
                  count={querySessions.length}
                  overflowCount={99999}
                  style={{ marginLeft: 8, backgroundColor: '#1890ff' }}
                />
              </span>
            ),
            children: (
              <Table
                columns={columns}
                dataSource={querySessions}
                loading={loading}
                rowKey={(record) => `${record.pid}-${record.serialNum ?? 0}`}
                pagination={{
                  defaultPageSize: 15,
                  showSizeChanger: true,
                  pageSizeOptions: ['10', '15', '20', '50', '100'],
                }}
                scroll={{ x: 1200 }}
              />
            ),
          },
          {
            key: 'connection',
            label: (
              <span>
                <PauseCircleOutlined />
                Idle Connections
                <Badge
                  count={connectionSessions.length}
                  overflowCount={99999}
                  style={{ marginLeft: 8, backgroundColor: '#8c8c8c' }}
                />
              </span>
            ),
            children: (
              <Table
                columns={columns}
                dataSource={connectionSessions}
                loading={loading}
                rowKey={(record) => `${record.pid}-${record.serialNum ?? 0}`}
                pagination={{
                  defaultPageSize: 15,
                  showSizeChanger: true,
                  pageSizeOptions: ['10', '15', '20', '50', '100'],
                }}
                scroll={{ x: 1200 }}
              />
            ),
          },
        ]}
      />

      {/* Query Detail Modal */}
      <Modal
        title="Query Details"
        open={!!selectedQuery}
        onCancel={() => setSelectedQuery(null)}
        footer={[
          <Button key="close" onClick={() => setSelectedQuery(null)}>
            Close
          </Button>,
        ]}
        width={800}
      >
        <Card
          style={{ background: '#1e1e1e', borderRadius: 8 }}
          bodyStyle={{ padding: 16 }}
        >
          <Paragraph
            copyable={{ text: selectedQuery || '' }}
            style={{
              fontFamily: "'Fira Code', 'Consolas', 'Monaco', monospace",
              fontSize: 13,
              lineHeight: 1.6,
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              color: '#d4d4d4',
              margin: 0,
            }}
          >
            {selectedQuery ? formatSQL(selectedQuery) : ''}
          </Paragraph>
        </Card>
      </Modal>
    </div>
  )
}

export default Sessions
