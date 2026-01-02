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
  Input,
  message,
  Tabs,
  Empty,
} from 'antd'
import {
  DatabaseOutlined,
  TableOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import { tablesApi, type TableDataResult, type ColumnInfo } from '../api/tables'
import type { DatabaseInfo, TableInfo, IndexInfo } from '../types'

const { Title, Text } = Typography
const { Option } = Select

function Tables() {
  const [databases, setDatabases] = useState<DatabaseInfo[]>([])
  const [selectedDb, setSelectedDb] = useState<string | null>(null)
  const [tables, setTables] = useState<TableInfo[]>([])
  const [selectedTable, setSelectedTable] = useState<TableInfo | null>(null)
  const [columns, setColumns] = useState<ColumnInfo[]>([])
  const [indexes, setIndexes] = useState<IndexInfo[]>([])
  const [tableData, setTableData] = useState<TableDataResult | null>(null)
  const [dataLoading, setDataLoading] = useState(false)
  const [loading, setLoading] = useState(false)
  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [searchText, setSearchText] = useState('')

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
    setTableData(null)

    try {
      const [columnsRes, indexesRes] = await Promise.all([
        tablesApi.getColumns(selectedDb, table.name),
        tablesApi.getIndexes(selectedDb, table.name),
      ])
      setColumns(columnsRes.data)
      setIndexes(indexesRes.data)
    } catch {
      message.error('Failed to fetch table details')
    }
  }

  const fetchTableData = async () => {
    if (!selectedDb || !selectedTable) return
    setDataLoading(true)
    try {
      const response = await tablesApi.getTableData(selectedDb, selectedTable.name, 100)
      setTableData(response.data)
    } catch {
      message.error('Failed to fetch table data')
    } finally {
      setDataLoading(false)
    }
  }

  const handleDatabaseSelect = (value: string) => {
    setSelectedDb(value)
    setSelectedTable(null)
    fetchTables(value)
  }

  const formatSize = (bytes: number): string => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  // Search filter function
  const filterBySearch = (table: TableInfo) => {
    if (!searchText) return true
    const search = searchText.toLowerCase()
    return table.name?.toLowerCase().includes(search) || false
  }

  const filteredTables = tables.filter(filterBySearch)

  const tableColumns = [
    {
      title: 'Table Name',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      ellipsis: true,
      render: (name: string) => (
        <Space>
          <TableOutlined />
          {name}
        </Space>
      ),
      sorter: (a: TableInfo, b: TableInfo) => a.name.localeCompare(b.name),
    },
    {
      title: 'Rows',
      dataIndex: 'rows',
      key: 'rows',
      width: 100,
      render: (rows: number) => rows.toLocaleString(),
      sorter: (a: TableInfo, b: TableInfo) => a.rows - b.rows,
    },
    {
      title: 'Size',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (size: number) => formatSize(size),
      sorter: (a: TableInfo, b: TableInfo) => a.size - b.size,
    },
    {
      title: 'Created',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 160,
      ellipsis: true,
      render: (time: string | null) => time || '-',
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
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
    { title: 'Name', dataIndex: 'name', key: 'name', width: 150, ellipsis: true },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      ellipsis: true,
      render: (type: string) => <Tag>{type}</Tag>,
    },
    {
      title: 'Nullable',
      dataIndex: 'nullable',
      key: 'nullable',
      width: 80,
      render: (nullable: boolean) =>
        nullable ? <Tag color="green">YES</Tag> : <Tag color="red">NO</Tag>,
    },
    {
      title: 'Key',
      dataIndex: 'key',
      key: 'key',
      width: 80,
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
      width: 100,
      ellipsis: true,
      render: (val: string | null) => val || '-',
    },
    {
      title: 'Extra',
      dataIndex: 'extra',
      key: 'extra',
      width: 120,
      ellipsis: true,
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
  ]

  const totalSize = databases.reduce((sum, db) => sum + db.size, 0)
  const totalTables = databases.reduce((sum, db) => sum + db.tableCount, 0)
  const totalRows = databases.reduce((sum, db) => sum + db.totalRows, 0)

  return (
    <div>
      <Title level={2}>Tables</Title>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="Schemas"
              value={databases.length}
              prefix={<DatabaseOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="Total Tables"
              value={totalTables}
              prefix={<TableOutlined />}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="Total Rows" value={totalRows.toLocaleString()} />
          </Card>
        </Col>
        <Col span={6}>
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
              <Input.Search
                placeholder="Search..."
                allowClear
                value={searchText}
                onChange={(e) => setSearchText(e.target.value)}
                style={{ width: 200 }}
              />
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
          dataSource={filteredTables}
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
                <Table
                  columns={indexTableColumns}
                  dataSource={indexes}
                  rowKey="name"
                  size="small"
                  pagination={false}
                />
              ),
            },
            {
              key: 'data',
              label: (
                <span>
                  <EyeOutlined /> Data Preview
                </span>
              ),
              children: (
                <div>
                  <Button
                    type="primary"
                    icon={<ReloadOutlined />}
                    onClick={fetchTableData}
                    loading={dataLoading}
                    style={{ marginBottom: 16 }}
                  >
                    Load Data (Top 100)
                  </Button>
                  {tableData ? (
                    <Table
                      columns={tableData.columns.map((col, idx) => ({
                        title: col,
                        dataIndex: idx.toString(),
                        key: col,
                        ellipsis: true,
                        width: 150,
                        render: (value: string | null) => (
                          <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
                            {value === null ? <Text type="secondary">NULL</Text> : value}
                          </span>
                        ),
                      }))}
                      dataSource={tableData.rows.map((row, rowIdx) => {
                        const rowData: Record<string, string | null> = { key: rowIdx.toString() }
                        row.forEach((cell, cellIdx) => {
                          rowData[cellIdx.toString()] = cell
                        })
                        return rowData
                      })}
                      size="small"
                      scroll={{ x: 'max-content', y: 400 }}
                      pagination={{
                        pageSize: 50,
                        showTotal: (total) => `${total} rows`,
                      }}
                    />
                  ) : (
                    <Empty description="Click 'Load Data' to preview table data" />
                  )}
                </div>
              ),
            },
          ]}
        />
      </Modal>
    </div>
  )
}

export default Tables
