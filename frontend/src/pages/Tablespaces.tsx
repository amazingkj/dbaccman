import { useState, useEffect } from 'react'
import {
  Typography,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Tag,
  Popconfirm,
  message,
  Card,
  Row,
  Col,
  Select,
  Progress,
  Tooltip,
} from 'antd'
import {
  PlusOutlined,
  ReloadOutlined,
  DeleteOutlined,
  DatabaseOutlined,
  SwapOutlined,
  RightOutlined,
  HddOutlined,
  PieChartOutlined,
} from '@ant-design/icons'
import { tablespacesApi } from '../api/tablespaces'
import { tablesApi } from '../api/tables'
import type { TablespaceInfo, CreateTablespaceRequest, TableInfo, DatabaseInfo } from '../types'

const { Title, Text } = Typography
const { Option } = Select

// Modernize-style stat card styles (white background with colored icons)
const statCardStyles = {
  total: {
    color: '#5d87ff',
    bgColor: 'rgba(93, 135, 255, 0.1)',
    icon: <DatabaseOutlined />,
  },
  general: {
    color: '#49beff',
    bgColor: 'rgba(73, 190, 255, 0.1)',
    icon: <HddOutlined />,
  },
  size: {
    color: '#13deb9',
    bgColor: 'rgba(19, 222, 185, 0.1)',
    icon: <PieChartOutlined />,
  },
  allocated: {
    color: '#ffae1f',
    bgColor: 'rgba(255, 174, 31, 0.1)',
    icon: <HddOutlined />,
  },
}

interface StatCardProps {
  title: string
  value: string | number
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
          {typeof value === 'number' ? value.toLocaleString() : value}
        </div>
      </div>
    </Card>
  )
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function Tablespaces() {
  const [tablespaces, setTablespaces] = useState<TablespaceInfo[]>([])
  const [selectedTablespace, setSelectedTablespace] = useState<TablespaceInfo | null>(null)
  const [tablesInTablespace, setTablesInTablespace] = useState<TableInfo[]>([])
  const [databases, setDatabases] = useState<DatabaseInfo[]>([])
  const [allTables, setAllTables] = useState<TableInfo[]>([])
  const [loading, setLoading] = useState(false)
  const [tablesLoading, setTablesLoading] = useState(false)
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [moveModalOpen, setMoveModalOpen] = useState(false)
  const [selectedDatabase, setSelectedDatabase] = useState<string>('')
  const [searchText, setSearchText] = useState('')
  const [searchColumn, setSearchColumn] = useState<string>('all')
  const [form] = Form.useForm()
  const [moveForm] = Form.useForm()

  const fetchTablespaces = async () => {
    setLoading(true)
    try {
      const res = await tablespacesApi.list()
      setTablespaces(res.data)
    } catch {
      message.error('Failed to fetch tablespaces')
    } finally {
      setLoading(false)
    }
  }

  const fetchDatabases = async () => {
    try {
      const res = await tablesApi.getDatabases()
      setDatabases(res.data)
    } catch {
      // ignore
    }
  }

  const fetchTablesInTablespace = async (name: string) => {
    setTablesLoading(true)
    try {
      const res = await tablespacesApi.getTables(name)
      setTablesInTablespace(res.data)
    } catch {
      message.error('Failed to fetch tables in tablespace')
      setTablesInTablespace([])
    } finally {
      setTablesLoading(false)
    }
  }

  const fetchTablesInDatabase = async (database: string) => {
    try {
      const res = await tablesApi.getTables(database)
      setAllTables(res.data)
    } catch {
      setAllTables([])
    }
  }

  useEffect(() => {
    fetchTablespaces()
    fetchDatabases()
  }, [])

  useEffect(() => {
    if (selectedTablespace) {
      fetchTablesInTablespace(selectedTablespace.name)
    }
  }, [selectedTablespace])

  const handleCreate = async (values: CreateTablespaceRequest) => {
    try {
      await tablespacesApi.create(values)
      message.success('Tablespace created successfully')
      setCreateModalOpen(false)
      form.resetFields()
      fetchTablespaces()
    } catch {
      message.error('Failed to create tablespace')
    }
  }

  const handleDelete = async (tablespace: TablespaceInfo) => {
    try {
      await tablespacesApi.delete(tablespace.name)
      message.success('Tablespace deleted successfully')
      if (selectedTablespace?.name === tablespace.name) {
        setSelectedTablespace(null)
        setTablesInTablespace([])
      }
      fetchTablespaces()
    } catch {
      message.error('Failed to delete tablespace')
    }
  }

  const handleMoveTable = async (values: { database: string; tableName: string }) => {
    if (!selectedTablespace) return
    try {
      await tablespacesApi.moveTable({
        database: values.database,
        tableName: values.tableName,
        tablespaceName: selectedTablespace.name,
      })
      message.success('Table moved successfully')
      setMoveModalOpen(false)
      moveForm.resetFields()
      setSelectedDatabase('')
      setAllTables([])
      fetchTablesInTablespace(selectedTablespace.name)
    } catch {
      message.error('Failed to move table')
    }
  }

  const totalSize = tablespaces.reduce((sum, ts) => sum + ts.fileSize, 0)
  const totalAllocated = tablespaces.reduce((sum, ts) => sum + ts.allocatedSize, 0)
  const generalCount = tablespaces.filter(ts => ts.spaceType === 'General').length

  // Search filter function
  const filterBySearch = (ts: TablespaceInfo) => {
    if (!searchText) return true
    const search = searchText.toLowerCase()

    if (searchColumn === 'all') {
      return (
        ts.name?.toLowerCase().includes(search) ||
        ts.spaceType?.toLowerCase().includes(search) ||
        ts.state?.toLowerCase().includes(search)
      )
    }

    const value = ts[searchColumn as keyof TablespaceInfo]
    if (value === null || value === undefined) return false
    return value.toString().toLowerCase().includes(search)
  }

  const filteredTablespaces = tablespaces.filter(filterBySearch)

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      width: 180,
      ellipsis: true,
      render: (name: string, record: TablespaceInfo) => (
        <Tooltip title="Click to view tables in this tablespace">
          <Button
            type="link"
            onClick={() => setSelectedTablespace(record)}
            style={{ padding: 0 }}
          >
            <DatabaseOutlined /> {name} <RightOutlined style={{ fontSize: 10, marginLeft: 4 }} />
          </Button>
        </Tooltip>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'spaceType',
      key: 'spaceType',
      width: 100,
      render: (type: string) => (
        <Tag color={type === 'General' ? 'blue' : type === 'Single' ? 'green' : 'default'}>
          {type}
        </Tag>
      ),
    },
    {
      title: 'File Size',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 100,
      render: (size: number) => formatBytes(size),
      sorter: (a: TablespaceInfo, b: TablespaceInfo) => a.fileSize - b.fileSize,
    },
    {
      title: 'Allocated',
      dataIndex: 'allocatedSize',
      key: 'allocatedSize',
      width: 100,
      render: (size: number) => formatBytes(size),
    },
    {
      title: 'Usage',
      key: 'usage',
      width: 120,
      render: (_: unknown, record: TablespaceInfo) => {
        const percent = record.fileSize > 0
          ? Math.round((record.allocatedSize / record.fileSize) * 100)
          : 0
        return <Progress percent={percent} size="small" />
      },
    },
    {
      title: 'State',
      dataIndex: 'state',
      key: 'state',
      width: 80,
      render: (state: string) => (
        <Tag color={state === 'active' ? 'green' : 'red'}>{state}</Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: TablespaceInfo) => {
        // System tablespaces that should not be deleted
        const systemTablespaces = [
          'mysql', 'innodb_system', 'innodb_temporary', 'sys',
          'SYSTEM', 'SYSAUX', 'UNDOTBS1', 'TEMP', 'USERS',
          'pg_default', 'pg_global'
        ]
        const isSystem = systemTablespaces.includes(record.name) ||
                         record.spaceType?.toLowerCase() === 'system' ||
                         record.spaceType?.toLowerCase() === 'undo'

        return (
          <Space>
            {!isSystem && (
              <Popconfirm
                title="Delete Tablespace"
                description={`Are you sure you want to delete ${record.name}?`}
                onConfirm={() => handleDelete(record)}
                okText="Yes"
                cancelText="No"
              >
                <Button type="link" danger icon={<DeleteOutlined />}>
                  Delete
                </Button>
              </Popconfirm>
            )}
          </Space>
        )
      },
    },
  ]

  const tableColumns = [
    {
      title: 'Table Name',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      ellipsis: true,
    },
    {
      title: 'Rows',
      dataIndex: 'rows',
      key: 'rows',
      width: 100,
      render: (rows: number) => rows.toLocaleString(),
    },
    {
      title: 'Size',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (size: number) => formatBytes(size),
    },
  ]

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
            <HddOutlined style={{ marginRight: 12 }} />
            Tablespaces
          </Title>
          <Text type="secondary">Database storage management</Text>
        </div>
      </div>

      {/* Stats Cards - CoreUI Style */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Tablespaces"
            value={tablespaces.length}
            style={statCardStyles.total}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="General Tablespaces"
            value={generalCount}
            style={statCardStyles.general}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Size"
            value={formatBytes(totalSize)}
            style={statCardStyles.size}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Allocated"
            value={formatBytes(totalAllocated)}
            style={statCardStyles.allocated}
          />
        </Col>
      </Row>

      <Row gutter={16} align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Space>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalOpen(true)}
            >
              Create Tablespace
            </Button>
            <Button icon={<ReloadOutlined />} onClick={fetchTablespaces}>
              Refresh
            </Button>
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
              <Option value="all">All Columns</Option>
              <Option value="name">Name</Option>
              <Option value="spaceType">Type</Option>
              <Option value="state">State</Option>
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

      <Table
        columns={columns}
        dataSource={filteredTablespaces}
        loading={loading}
        rowKey="name"
        pagination={{ pageSize: 10 }}
        style={{ marginBottom: 24 }}
        rowClassName={(record) =>
          selectedTablespace?.name === record.name ? 'ant-table-row-selected' : ''
        }
        onRow={(record) => ({
          onClick: () => setSelectedTablespace(record),
          style: { cursor: 'pointer' },
        })}
      />

      {selectedTablespace && (
        <Card
          title={
            <Space>
              <DatabaseOutlined />
              Tables in "{selectedTablespace.name}"
            </Space>
          }
          extra={
            <Button
              type="primary"
              icon={<SwapOutlined />}
              onClick={() => setMoveModalOpen(true)}
            >
              Move Table Here
            </Button>
          }
        >
          <Table
            columns={tableColumns}
            dataSource={tablesInTablespace}
            loading={tablesLoading}
            rowKey="name"
            pagination={{ pageSize: 5 }}
            locale={{ emptyText: 'No tables in this tablespace' }}
          />
        </Card>
      )}

      {/* Create Tablespace Modal */}
      <Modal
        title="Create Tablespace"
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false)
          form.resetFields()
        }}
        footer={null}
      >
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item
            name="name"
            label="Tablespace Name"
            rules={[
              { required: true, message: 'Please enter tablespace name' },
              { pattern: /^[a-zA-Z_][a-zA-Z0-9_]*$/, message: 'Invalid tablespace name' },
            ]}
          >
            <Input placeholder="Enter tablespace name" />
          </Form.Item>
          <Form.Item
            name="dataFile"
            label="Data File (optional)"
            help="Leave empty to use default: {name}.ibd"
          >
            <Input placeholder="e.g., ts_data.ibd" />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Create
              </Button>
              <Button onClick={() => setCreateModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Move Table Modal */}
      <Modal
        title={`Move Table to "${selectedTablespace?.name}"`}
        open={moveModalOpen}
        onCancel={() => {
          setMoveModalOpen(false)
          moveForm.resetFields()
          setSelectedDatabase('')
          setAllTables([])
        }}
        footer={null}
      >
        <Form form={moveForm} layout="vertical" onFinish={handleMoveTable}>
          <Form.Item
            name="database"
            label="Database"
            rules={[{ required: true, message: 'Please select a database' }]}
          >
            <Select
              placeholder="Select database"
              onChange={(value) => {
                setSelectedDatabase(value)
                fetchTablesInDatabase(value)
                moveForm.setFieldValue('tableName', undefined)
              }}
            >
              {databases.map(db => (
                <Option key={db.name} value={db.name}>{db.name}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item
            name="tableName"
            label="Table"
            rules={[{ required: true, message: 'Please select a table' }]}
          >
            <Select
              placeholder="Select table"
              disabled={!selectedDatabase}
            >
              {allTables.map(table => (
                <Option key={table.name} value={table.name}>{table.name}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Move
              </Button>
              <Button onClick={() => setMoveModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default Tablespaces
