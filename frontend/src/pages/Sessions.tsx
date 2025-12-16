import { useState, useEffect, useCallback } from 'react'
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
  Statistic,
  Switch,
  Tooltip,
  Modal,
} from 'antd'
import {
  ReloadOutlined,
  StopOutlined,
  ClockCircleOutlined,
  UserOutlined,
  DatabaseOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import { sessionsApi } from '../api/sessions'
import type { SessionInfo } from '../types'

const { Title, Text, Paragraph } = Typography

function Sessions() {
  const [sessions, setSessions] = useState<SessionInfo[]>([])
  const [loading, setLoading] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [selectedQuery, setSelectedQuery] = useState<string | null>(null)

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

  const handleKillSession = async (pid: number) => {
    try {
      await sessionsApi.kill(pid)
      message.success('Session killed successfully')
      fetchSessions()
    } catch {
      message.error('Failed to kill session')
    }
  }

  const formatTime = (seconds: number): string => {
    if (seconds < 60) return `${seconds}s`
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
    const hours = Math.floor(seconds / 3600)
    const mins = Math.floor((seconds % 3600) / 60)
    return `${hours}h ${mins}m`
  }

  const getCommandColor = (command: string): string => {
    switch (command) {
      case 'Query':
        return 'blue'
      case 'Sleep':
        return 'default'
      case 'Connect':
        return 'green'
      case 'Killed':
        return 'red'
      default:
        return 'orange'
    }
  }

  const stats = {
    total: sessions.length,
    active: sessions.filter((s) => s.command !== 'Sleep').length,
    sleeping: sessions.filter((s) => s.command === 'Sleep').length,
    longRunning: sessions.filter((s) => s.time > 60 && s.command !== 'Sleep').length,
  }

  const columns = [
    {
      title: 'PID',
      dataIndex: 'pid',
      key: 'pid',
      width: 80,
      sorter: (a: SessionInfo, b: SessionInfo) => a.pid - b.pid,
    },
    {
      title: 'User',
      dataIndex: 'user',
      key: 'user',
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
      ellipsis: true,
    },
    {
      title: 'Database',
      dataIndex: 'database',
      key: 'database',
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
      render: (command: string) => (
        <Tag color={getCommandColor(command)}>{command}</Tag>
      ),
      filters: [
        { text: 'Query', value: 'Query' },
        { text: 'Sleep', value: 'Sleep' },
        { text: 'Connect', value: 'Connect' },
      ],
      onFilter: (value: unknown, record: SessionInfo) => record.command === value,
    },
    {
      title: 'Time',
      dataIndex: 'time',
      key: 'time',
      render: (time: number) => {
        const isLong = time > 60
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
      ellipsis: true,
      render: (state: string | null) => state || '-',
    },
    {
      title: 'Query',
      dataIndex: 'query',
      key: 'query',
      width: 200,
      ellipsis: true,
      render: (query: string | null) =>
        query ? (
          <Tooltip title="Click to view full query">
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => setSelectedQuery(query)}
            >
              {query.substring(0, 30)}...
            </Button>
          </Tooltip>
        ) : (
          '-'
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: SessionInfo) => (
        <Popconfirm
          title="Kill Session"
          description={`Are you sure you want to kill session ${record.pid}?`}
          onConfirm={() => handleKillSession(record.pid)}
          okText="Yes"
          cancelText="No"
        >
          <Button type="link" danger icon={<StopOutlined />}>
            Kill
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      <Title level={2}>Session Monitoring</Title>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic title="Total Sessions" value={stats.total} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Active Sessions"
              value={stats.active}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Sleeping"
              value={stats.sleeping}
              valueStyle={{ color: '#8c8c8c' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Long Running (>60s)"
              value={stats.longRunning}
              valueStyle={{ color: stats.longRunning > 0 ? '#cf1322' : '#3f8600' }}
            />
          </Card>
        </Col>
      </Row>

      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={fetchSessions} loading={loading}>
          Refresh
        </Button>
        <Space>
          <Text>Auto-refresh (5s):</Text>
          <Switch checked={autoRefresh} onChange={setAutoRefresh} />
        </Space>
      </Space>

      <Table
        columns={columns}
        dataSource={sessions}
        loading={loading}
        rowKey="pid"
        pagination={{ pageSize: 15 }}
        rowClassName={(record) =>
          record.time > 60 && record.command !== 'Sleep' ? 'row-warning' : ''
        }
        scroll={{ x: 1200 }}
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
        <Card>
          <Paragraph
            copyable
            style={{
              fontFamily: 'monospace',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
            }}
          >
            {selectedQuery}
          </Paragraph>
        </Card>
      </Modal>

      <style>{`
        .row-warning {
          background-color: #fff7e6;
        }
        .row-warning:hover > td {
          background-color: #ffe7ba !important;
        }
      `}</style>
    </div>
  )
}

export default Sessions
