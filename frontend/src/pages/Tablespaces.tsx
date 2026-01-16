import { useState, useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'
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
  Alert,
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
import { useAuthStore } from '../store/authStore'
import { StatCard, STAT_CARD_STYLES } from '../components/common/StatCard'
import type { TablespaceInfo, CreateTablespaceRequest, TableInfo, DatabaseInfo } from '../types'

const { Title, Text } = Typography
const { Option } = Select

// Stat card style configurations - using shared STAT_CARD_STYLES constants
const statCardStyles = {
  total: { ...STAT_CARD_STYLES.primary, icon: <DatabaseOutlined /> },
  general: { ...STAT_CARD_STYLES.info, icon: <HddOutlined /> },
  size: { ...STAT_CARD_STYLES.success, icon: <PieChartOutlined /> },
  allocated: { ...STAT_CARD_STYLES.warning, icon: <HddOutlined /> },
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function Tablespaces() {
  const [searchParams] = useSearchParams()
  const sortByUsage = searchParams.get('sort') === 'usage'
  const { user } = useAuthStore()
  const dbType = user?.dbType?.toUpperCase()

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
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [bulkDeleting, setBulkDeleting] = useState(false)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
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

  // System tablespaces that should not be deleted
  const systemTablespaces = [
    'mysql', 'innodb_system', 'innodb_temporary', 'sys',
    'SYSTEM', 'SYSAUX', 'UNDOTBS1', 'TEMP', 'USERS',
    'pg_default', 'pg_global'
  ]

  const isSystemTablespace = (ts: TablespaceInfo) => {
    return systemTablespaces.includes(ts.name) ||
           ts.spaceType?.toLowerCase() === 'system' ||
           ts.spaceType?.toLowerCase() === 'undo'
  }

  const handleBulkDelete = async () => {
    if (selectedRowKeys.length === 0) {
      message.warning('No tablespaces selected')
      return
    }

    setBulkDeleting(true)
    let success = 0
    let failed = 0
    const errors: string[] = []

    for (const key of selectedRowKeys) {
      const name = key.toString()
      const ts = tablespaces.find(t => t.name === name)
      if (!ts || isSystemTablespace(ts)) {
        failed++
        errors.push(`${name}: System tablespace cannot be deleted`)
        continue
      }

      try {
        await tablespacesApi.delete(name)
        success++
        if (selectedTablespace?.name === name) {
          setSelectedTablespace(null)
          setTablesInTablespace([])
        }
      } catch (error: unknown) {
        failed++
        const err = error as { response?: { data?: { error?: string } } }
        errors.push(`${name}: ${err.response?.data?.error || 'Failed to delete'}`)
      }
    }

    if (failed > 0) {
      message.warning(`Deleted ${success} tablespace(s), ${failed} failed`)
    } else {
      message.success(`Successfully deleted ${success} tablespace(s)`)
    }

    setSelectedRowKeys([])
    setBulkDeleting(false)
    fetchTablespaces()
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

  // Sort by usage if URL has ?sort=usage
  const sortedTablespaces = useMemo(() => {
    if (sortByUsage) {
      return [...tablespaces].sort((a, b) => {
        const usageA = a.fileSize > 0 ? (a.allocatedSize / a.fileSize) : 0
        const usageB = b.fileSize > 0 ? (b.allocatedSize / b.fileSize) : 0
        return usageB - usageA // Descending order
      })
    }
    return tablespaces
  }, [tablespaces, sortByUsage])

  const filteredTablespaces = sortedTablespaces.filter(filterBySearch)

  const columns = [
    {
      title: '#',
      key: 'index',
      width: 50,
      render: (_: unknown, __: TablespaceInfo, index: number) => (
        <span style={{ color: '#8c8c8c' }}>
          {(currentPage - 1) * pageSize + index + 1}
        </span>
      ),
    },
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
      sorter: (a: TablespaceInfo, b: TablespaceInfo) => a.name.localeCompare(b.name),
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
      sorter: (a: TablespaceInfo, b: TablespaceInfo) => a.spaceType.localeCompare(b.spaceType),
    },
    {
      title: 'Capacity',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 100,
      render: (size: number) => size > 0 ? formatBytes(size) : <Tag>Unlimited</Tag>,
      sorter: (a: TablespaceInfo, b: TablespaceInfo) => a.fileSize - b.fileSize,
    },
    {
      title: 'Used',
      dataIndex: 'allocatedSize',
      key: 'allocatedSize',
      width: 100,
      render: (size: number) => formatBytes(size),
      sorter: (a: TablespaceInfo, b: TablespaceInfo) => a.allocatedSize - b.allocatedSize,
    },
    {
      title: 'Usage',
      key: 'usage',
      width: 150,
      render: (_: unknown, record: TablespaceInfo) => {
        // PostgreSQL: fileSize=0 means unlimited (disk-based)
        if (record.fileSize === 0) {
          return <Tag color="blue">N/A</Tag>
        }
        const percent = parseFloat(((record.allocatedSize / record.fileSize) * 100).toFixed(2))
        // 사용량에 따른 색상: 90%+ 빨강, 70%+ 주황, 그 외 파랑
        const strokeColor = percent >= 90 ? '#ff4d4f' : percent >= 70 ? '#faad14' : '#5d87ff'
        return (
          <Progress
            percent={percent}
            size="small"
            strokeColor={strokeColor}
            format={(p) => `${p?.toFixed(2)}%`}
          />
        )
      },
      sorter: (a: TablespaceInfo, b: TablespaceInfo) => {
        const aPercent = a.fileSize > 0 ? (a.allocatedSize / a.fileSize) : 0
        const bPercent = b.fileSize > 0 ? (b.allocatedSize / b.fileSize) : 0
        return aPercent - bPercent
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
      sorter: (a: TablespaceInfo, b: TablespaceInfo) => a.state.localeCompare(b.state),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: TablespaceInfo) => {
        const isSystem = isSystemTablespace(record)

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
            compact
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="General Tablespaces"
            value={generalCount}
            style={statCardStyles.general}
            compact
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Size"
            value={formatBytes(totalSize)}
            style={statCardStyles.size}
            compact
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Allocated"
            value={formatBytes(totalAllocated)}
            style={statCardStyles.allocated}
            compact
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
            {selectedRowKeys.length > 0 && (
              <Popconfirm
                title="Delete Selected Tablespaces"
                description={`Are you sure you want to delete ${selectedRowKeys.length} tablespace(s)?`}
                onConfirm={handleBulkDelete}
                okText="Yes"
                cancelText="No"
              >
                <Button
                  danger
                  icon={<DeleteOutlined />}
                  loading={bulkDeleting}
                >
                  Delete ({selectedRowKeys.length})
                </Button>
              </Popconfirm>
            )}
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
              <Option value="all">All</Option>
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
        pagination={{
          current: currentPage,
          pageSize: pageSize,
          showSizeChanger: true,
          pageSizeOptions: ['10', '15', '20', '50', '100'],
          onChange: (page, size) => {
            setCurrentPage(page)
            if (size !== pageSize) setPageSize(size)
          },
        }}
        style={{ marginBottom: 24 }}
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys,
          getCheckboxProps: (record) => ({
            disabled: isSystemTablespace(record),
          }),
        }}
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
        {dbType === 'POSTGRESQL' && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="PostgreSQL Requirement"
            description={
              <div>
                <p style={{ margin: 0 }}>
                  PostgreSQL requires the directory to <strong>exist on the server</strong> before creating a tablespace.
                </p>
                <p style={{ margin: '8px 0 0 0', fontSize: 12, color: '#666' }}>
                  Run on server: <code>sudo mkdir -p /path/to/dir && sudo chown postgres:postgres /path/to/dir</code>
                </p>
              </div>
            }
          />
        )}
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
            label={dbType === 'POSTGRESQL' ? 'Directory Location' : 'Data File (optional)'}
            help={dbType === 'POSTGRESQL'
              ? 'Full path to an existing directory on the server (e.g., /var/lib/postgresql/tablespaces/myts)'
              : 'Leave empty to use default: {name}.ibd'
            }
            rules={dbType === 'POSTGRESQL' ? [{ required: true, message: 'Directory path is required for PostgreSQL' }] : []}
          >
            <Input placeholder={dbType === 'POSTGRESQL' ? '/var/lib/postgresql/tablespaces/myts' : 'e.g., ts_data.ibd'} />
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
