import { useState, useEffect } from 'react'
import {
  Typography,
  Table,
  Button,
  Space,
  Select,
  Tag,
  Card,
  Row,
  Col,
  Statistic,
  Modal,
  Form,
  Input,
  Checkbox,
  Popconfirm,
  message,
  Tabs,
  Empty,
} from 'antd'
import {
  DatabaseOutlined,
  TableOutlined,
  ReloadOutlined,
  PlusOutlined,
  DeleteOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import { tablesApi } from '../api/tables'
import type { DatabaseInfo, TableInfo, IndexInfo, CreateIndexRequest } from '../types'

const { Title, Text } = Typography
const { Option } = Select

interface ColumnInfo {
  name: string
  type: string
  nullable: boolean
  key: string | null
  defaultValue: string | null
  extra: string | null
}

function Tables() {
  const [databases, setDatabases] = useState<DatabaseInfo[]>([])
  const [selectedDb, setSelectedDb] = useState<string | null>(null)
  const [tables, setTables] = useState<TableInfo[]>([])
  const [selectedTable, setSelectedTable] = useState<TableInfo | null>(null)
  const [columns, setColumns] = useState<ColumnInfo[]>([])
  const [indexes, setIndexes] = useState<IndexInfo[]>([])
  const [loading, setLoading] = useState(false)
  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [indexModalOpen, setIndexModalOpen] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => {
    fetchDatabases()
  }, [])

  const fetchDatabases = async () => {
    try {
      const response = await tablesApi.getDatabases()
      setDatabases(response.data)
    } catch {
      message.error('Failed to fetch databases')
    }
  }

  const fetchTables = async (database: string) => {
    setLoading(true)
    try {
      const response = await tablesApi.getTables(database)
      setTables(response.data)
    } catch {
      message.error('Failed to fetch tables')
    } finally {
      setLoading(false)
    }
  }

  const fetchTableDetails = async (table: TableInfo) => {
    if (!selectedDb) return
    setSelectedTable(table)
    setDetailModalOpen(true)

    try {
      const [columnsRes, indexesRes] = await Promise.all([
        fetch(`/api/tables/${selectedDb}/${table.name}/columns`, {
          headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
        }).then((r) => r.json()),
        tablesApi.getIndexes(selectedDb, table.name),
      ])
      setColumns(columnsRes)
      setIndexes(indexesRes.data)
    } catch {
      message.error('Failed to fetch table details')
    }
  }

  const handleDatabaseSelect = (value: string) => {
    setSelectedDb(value)
    setSelectedTable(null)
    fetchTables(value)
  }

  const handleCreateIndex = async (values: Omit<CreateIndexRequest, 'database' | 'table'>) => {
    if (!selectedDb || !selectedTable) return

    try {
      await tablesApi.createIndex({
        ...values,
        database: selectedDb,
        table: selectedTable.name,
      })
      message.success('Index created successfully')
      setIndexModalOpen(false)
      form.resetFields()
      // Refresh indexes
      const indexesRes = await tablesApi.getIndexes(selectedDb, selectedTable.name)
      setIndexes(indexesRes.data)
    } catch {
      message.error('Failed to create index')
    }
  }

  const handleDropIndex = async (indexName: string) => {
    if (!selectedDb || !selectedTable) return

    try {
      await tablesApi.dropIndex(selectedDb, selectedTable.name, indexName)
      message.success('Index dropped successfully')
      // Refresh indexes
      const indexesRes = await tablesApi.getIndexes(selectedDb, selectedTable.name)
      setIndexes(indexesRes.data)
    } catch {
      message.error('Failed to drop index')
    }
  }

  const formatSize = (bytes: number): string => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  const tableColumns = [
    {
      title: 'Table Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => (
        <Space>
          <TableOutlined />
          {name}
        </Space>
      ),
      sorter: (a: TableInfo, b: TableInfo) => a.name.localeCompare(b.name),
    },
    {
      title: 'Engine',
      dataIndex: 'engine',
      key: 'engine',
      render: (engine: string | null) =>
        engine ? <Tag color="blue">{engine}</Tag> : '-',
    },
    {
      title: 'Rows',
      dataIndex: 'rows',
      key: 'rows',
      render: (rows: number) => rows.toLocaleString(),
      sorter: (a: TableInfo, b: TableInfo) => a.rows - b.rows,
    },
    {
      title: 'Size',
      dataIndex: 'size',
      key: 'size',
      render: (size: number) => formatSize(size),
      sorter: (a: TableInfo, b: TableInfo) => a.size - b.size,
    },
    {
      title: 'Created',
      dataIndex: 'createTime',
      key: 'createTime',
      render: (time: string | null) => time || '-',
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: TableInfo) => (
        <Button
          type="link"
          icon={<InfoCircleOutlined />}
          onClick={() => fetchTableDetails(record)}
        >
          Details
        </Button>
      ),
    },
  ]

  const columnTableColumns = [
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => <Tag>{type}</Tag>,
    },
    {
      title: 'Nullable',
      dataIndex: 'nullable',
      key: 'nullable',
      render: (nullable: boolean) =>
        nullable ? <Tag color="green">YES</Tag> : <Tag color="red">NO</Tag>,
    },
    {
      title: 'Key',
      dataIndex: 'key',
      key: 'key',
      render: (key: string | null) => {
        if (!key) return '-'
        const color = key === 'PRI' ? 'gold' : key === 'UNI' ? 'purple' : 'default'
        return <Tag color={color}>{key}</Tag>
      },
    },
    {
      title: 'Default',
      dataIndex: 'defaultValue',
      key: 'defaultValue',
      render: (val: string | null) => val || '-',
    },
    {
      title: 'Extra',
      dataIndex: 'extra',
      key: 'extra',
      render: (extra: string | null) =>
        extra ? <Tag color="cyan">{extra}</Tag> : '-',
    },
  ]

  const indexTableColumns = [
    { title: 'Name', dataIndex: 'name', key: 'name' },
    {
      title: 'Columns',
      dataIndex: 'columns',
      key: 'columns',
      render: (cols: string[]) => cols.join(', '),
    },
    {
      title: 'Unique',
      dataIndex: 'unique',
      key: 'unique',
      render: (unique: boolean) =>
        unique ? <Tag color="purple">UNIQUE</Tag> : <Tag>NON-UNIQUE</Tag>,
    },
    { title: 'Type', dataIndex: 'type', key: 'type' },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: IndexInfo) =>
        record.name !== 'PRIMARY' && (
          <Popconfirm
            title="Drop Index"
            description={`Are you sure you want to drop index ${record.name}?`}
            onConfirm={() => handleDropIndex(record.name)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              Drop
            </Button>
          </Popconfirm>
        ),
    },
  ]

  const totalSize = databases.reduce((sum, db) => sum + db.size, 0)
  const totalTables = databases.reduce((sum, db) => sum + db.tableCount, 0)

  return (
    <div>
      <Title level={2}>Table Management</Title>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={8}>
          <Card>
            <Statistic
              title="Databases"
              value={databases.length}
              prefix={<DatabaseOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="Total Tables"
              value={totalTables}
              prefix={<TableOutlined />}
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="Total Size" value={formatSize(totalSize)} />
          </Card>
        </Col>
      </Row>

      <Card style={{ marginBottom: 24 }}>
        <Row gutter={16} align="middle">
          <Col span={8}>
            <Text strong>Select Database:</Text>
            <Select
              style={{ width: '100%', marginTop: 8 }}
              placeholder="Select a database"
              onChange={handleDatabaseSelect}
              value={selectedDb}
            >
              {databases.map((db) => (
                <Option key={db.name} value={db.name}>
                  <Space>
                    <DatabaseOutlined />
                    {db.name}
                    <Text type="secondary">({db.tableCount} tables)</Text>
                  </Space>
                </Option>
              ))}
            </Select>
          </Col>
          <Col span={16}>
            <Space style={{ float: 'right' }}>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => selectedDb && fetchTables(selectedDb)}
                disabled={!selectedDb}
              >
                Refresh
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      {selectedDb ? (
        <Table
          columns={tableColumns}
          dataSource={tables}
          loading={loading}
          rowKey="name"
          pagination={{ pageSize: 10 }}
        />
      ) : (
        <Empty description="Select a database to view tables" />
      )}

      {/* Table Detail Modal */}
      <Modal
        title={
          <Space>
            <TableOutlined />
            {selectedTable?.name}
          </Space>
        }
        open={detailModalOpen}
        onCancel={() => {
          setDetailModalOpen(false)
          setSelectedTable(null)
        }}
        footer={null}
        width={900}
      >
        <Tabs
          items={[
            {
              key: 'columns',
              label: 'Columns',
              children: (
                <Table
                  columns={columnTableColumns}
                  dataSource={columns}
                  rowKey="name"
                  size="small"
                  pagination={false}
                />
              ),
            },
            {
              key: 'indexes',
              label: 'Indexes',
              children: (
                <>
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    style={{ marginBottom: 16 }}
                    onClick={() => setIndexModalOpen(true)}
                  >
                    Create Index
                  </Button>
                  <Table
                    columns={indexTableColumns}
                    dataSource={indexes}
                    rowKey="name"
                    size="small"
                    pagination={false}
                  />
                </>
              ),
            },
          ]}
        />
      </Modal>

      {/* Create Index Modal */}
      <Modal
        title="Create Index"
        open={indexModalOpen}
        onCancel={() => {
          setIndexModalOpen(false)
          form.resetFields()
        }}
        footer={null}
      >
        <Form form={form} layout="vertical" onFinish={handleCreateIndex}>
          <Form.Item
            name="indexName"
            label="Index Name"
            rules={[{ required: true, message: 'Please enter index name' }]}
          >
            <Input placeholder="idx_column_name" />
          </Form.Item>
          <Form.Item
            name="columns"
            label="Columns"
            rules={[{ required: true, message: 'Please enter columns' }]}
            extra="Enter column names separated by commas"
          >
            <Select
              mode="tags"
              placeholder="Select or enter columns"
              tokenSeparators={[',']}
            >
              {columns.map((col) => (
                <Option key={col.name} value={col.name}>
                  {col.name}
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="unique" valuePropName="checked">
            <Checkbox>Unique Index</Checkbox>
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit">
                Create
              </Button>
              <Button onClick={() => setIndexModalOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default Tables
