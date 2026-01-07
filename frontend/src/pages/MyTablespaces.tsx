import { useState, useEffect } from 'react'
import {
  Typography,
  Table,
  Card,
  Button,
  Tag,
  message,
  Progress,
  Row,
  Col,
  Statistic,
} from 'antd'
import {
  HddOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { userApi, type UserTablespaceInfo, type UserTablespace } from '../api/user'
import { useAuthStore } from '../store/authStore'

const { Title, Text } = Typography

function MyTablespaces() {
  const { user } = useAuthStore()
  const [tablespaceInfo, setTablespaceInfo] = useState<UserTablespaceInfo | null>(null)
  const [loading, setLoading] = useState(false)

  const fetchTablespaces = async () => {
    setLoading(true)
    try {
      const response = await userApi.getTablespaces()
      setTablespaceInfo(response.data)
    } catch {
      message.error('Failed to fetch tablespaces')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchTablespaces()
  }, [])

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  const parseMaxBytes = (maxBytes: string): number | null => {
    if (maxBytes === 'UNLIMITED') return null
    return parseInt(maxBytes, 10)
  }

  const columns = [
    {
      title: 'Tablespace',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => (
        <Tag color="blue" icon={<HddOutlined />}>
          {name}
        </Tag>
      ),
    },
    {
      title: 'Quota',
      dataIndex: 'maxBytes',
      key: 'maxBytes',
      render: (max: string) => max === 'UNLIMITED' ? (
        <Tag color="green">UNLIMITED</Tag>
      ) : formatBytes(parseInt(max, 10)),
    },
    {
      title: 'Used',
      dataIndex: 'usedBytes',
      key: 'usedBytes',
      render: (used: number) => formatBytes(used),
    },
    {
      title: 'Usage',
      key: 'usage',
      width: 200,
      render: (_: unknown, record: UserTablespace) => {
        const max = parseMaxBytes(record.maxBytes)
        if (max === null) {
          return <Text type="secondary">Unlimited quota</Text>
        }
        const percent = Math.round((record.usedBytes / max) * 100)
        return (
          <Progress
            percent={percent}
            size="small"
            status={percent > 90 ? 'exception' : percent > 70 ? 'active' : 'normal'}
          />
        )
      },
    },
  ]

  const totalUsed = tablespaceInfo?.quotas.reduce((sum, ts) => sum + ts.usedBytes, 0) || 0

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, marginBottom: 4 }}>
            <HddOutlined style={{ marginRight: 12 }} />
            My Tablespaces
          </Title>
          <Text type="secondary">Tablespace quotas for {user?.username}</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={fetchTablespaces} loading={loading}>
          Refresh
        </Button>
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Default Tablespace"
              value={tablespaceInfo?.defaultTablespace || '-'}
              prefix={<HddOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Temporary Tablespace"
              value={tablespaceInfo?.temporaryTablespace || '-'}
              prefix={<HddOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card>
            <Statistic
              title="Total Used Space"
              value={formatBytes(totalUsed)}
              prefix={<HddOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card title="Tablespace Quotas">
        <Table
          columns={columns}
          dataSource={tablespaceInfo?.quotas || []}
          rowKey="name"
          loading={loading}
          pagination={false}
          locale={{ emptyText: 'No tablespace quotas assigned' }}
        />
      </Card>
    </div>
  )
}

export default MyTablespaces
