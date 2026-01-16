import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
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
  HddOutlined,
  OrderedListOutlined,
  SyncOutlined,
  BarChartOutlined,
} from '@ant-design/icons'
import { tablesApi, type TableDataResult, type ColumnInfo } from '../api/tables'
import type { DatabaseInfo, TableInfo, IndexInfo } from '../types'
import { StatCard, STAT_CARD_STYLES } from '../components/common/StatCard'

const { Title, Text } = Typography
const { Option } = Select

// Stat card style configurations - using shared STAT_CARD_STYLES constants
const statCardStyles = {
  schemas: { ...STAT_CARD_STYLES.primary, icon: <DatabaseOutlined /> },
  tables: { ...STAT_CARD_STYLES.info, icon: <TableOutlined /> },
  rows: { ...STAT_CARD_STYLES.success, icon: <OrderedListOutlined /> },
  size: { ...STAT_CARD_STYLES.warning, icon: <HddOutlined /> },
}

function Tables() {
  const [searchParams, setSearchParams] = useSearchParams()
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
  const [gatheringStats, setGatheringStats] = useState(false)
  const [currentPage, setCurrentPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)

  useEffect(() => {
    fetchDatabases()
  }, [])

  // Handle URL parameter for database selection
  useEffect(() => {
    const dbFromUrl = searchParams.get('db')
    if (dbFromUrl && databases.length > 0) {
      const dbExists = databases.some(db => db.name === dbFromUrl)
      if (dbExists && selectedDb !== dbFromUrl) {
        setSelectedDb(dbFromUrl)
        fetchTables(dbFromUrl)
        // Clear the URL parameter after applying
        setSearchParams({})
      }
    }
  }, [databases, searchParams])

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

  const handleDatabaseSelect = async (value: string) => {
    setSelectedDb(value)
    setSelectedTable(null)
    setCurrentPage(1) // Reset to first page

    // Load tables
    await fetchTables(value)
  }

  const handleGatherStats = () => {
    if (!selectedDb) return

    Modal.confirm({
      title: 'Gather Statistics',
      content: (
        <div>
          <p>This will gather statistics for schema <strong>{selectedDb}</strong>.</p>
          <p style={{ color: '#ff4d4f' }}>
            Warning: This operation may take several minutes and impact database performance.
          </p>
        </div>
      ),
      okText: 'Gather Stats',
      okButtonProps: { danger: true },
      onOk: async () => {
        setGatheringStats(true)
        try {
          await tablesApi.gatherStats(selectedDb)
          message.success('Statistics gathered successfully')
          // Refresh data after gathering stats
          await Promise.all([
            fetchDatabases(),
            fetchTables(selectedDb)
          ])
        } catch {
          message.error('Failed to gather statistics')
        } finally {
          setGatheringStats(false)
        }
      }
    })
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
      title: '#',
      key: 'index',
      width: 45,
      render: (_: unknown, __: TableInfo, index: number) => (
        <span style={{ color: '#8c8c8c' }}>
          {(currentPage - 1) * pageSize + index + 1}
        </span>
      ),
    },
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
      title: gatheringStats ? <span>Rows <SyncOutlined spin style={{ fontSize: 10, marginLeft: 4 }} /></span> : 'Rows',
      dataIndex: 'rows',
      key: 'rows',
      width: 100,
      render: (rows: number) => rows.toLocaleString(),
      sorter: (a: TableInfo, b: TableInfo) => a.rows - b.rows,
    },
    {
      title: gatheringStats ? <span>Size <SyncOutlined spin style={{ fontSize: 10, marginLeft: 4 }} /></span> : 'Size',
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
      sorter: (a: TableInfo, b: TableInfo) => {
        if (!a.createTime && !b.createTime) return 0
        if (!a.createTime) return 1
        if (!b.createTime) return -1
        return a.createTime.localeCompare(b.createTime)
      },
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
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      width: 150,
      ellipsis: true,
      sorter: (a: ColumnInfo, b: ColumnInfo) => a.name.localeCompare(b.name),
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      ellipsis: true,
      render: (type: string) => <Tag>{type}</Tag>,
      sorter: (a: ColumnInfo, b: ColumnInfo) => a.type.localeCompare(b.type),
    },
    {
      title: 'Nullable',
      dataIndex: 'nullable',
      key: 'nullable',
      width: 80,
      render: (nullable: boolean) =>
        nullable ? <Tag color="green">YES</Tag> : <Tag color="red">NO</Tag>,
      sorter: (a: ColumnInfo, b: ColumnInfo) => Number(a.nullable) - Number(b.nullable),
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
      sorter: (a: ColumnInfo, b: ColumnInfo) => (a.key || '').localeCompare(b.key || ''),
    },
    {
      title: 'Default',
      dataIndex: 'defaultValue',
      key: 'defaultValue',
      width: 100,
      ellipsis: true,
      render: (val: string | null) => val || '-',
      sorter: (a: ColumnInfo, b: ColumnInfo) => (a.defaultValue || '').localeCompare(b.defaultValue || ''),
    },
    {
      title: 'Extra',
      dataIndex: 'extra',
      key: 'extra',
      width: 120,
      ellipsis: true,
      render: (extra: string | null) =>
        extra ? <Tag color="cyan">{extra}</Tag> : '-',
      sorter: (a: ColumnInfo, b: ColumnInfo) => (a.extra || '').localeCompare(b.extra || ''),
    },
  ]

  const indexTableColumns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      sorter: (a: IndexInfo, b: IndexInfo) => a.name.localeCompare(b.name),
    },
    {
      title: 'Columns',
      dataIndex: 'columns',
      key: 'columns',
      render: (cols: string[]) => cols.join(', '),
      sorter: (a: IndexInfo, b: IndexInfo) => a.columns.join(',').localeCompare(b.columns.join(',')),
    },
    {
      title: 'Unique',
      dataIndex: 'unique',
      key: 'unique',
      render: (unique: boolean) =>
        unique ? <Tag color="purple">UNIQUE</Tag> : <Tag>NON-UNIQUE</Tag>,
      sorter: (a: IndexInfo, b: IndexInfo) => Number(a.unique) - Number(b.unique),
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      sorter: (a: IndexInfo, b: IndexInfo) => (a.type || '').localeCompare(b.type || ''),
    },
  ]

  const totalSize = databases.reduce((sum, db) => sum + db.size, 0)
  const totalTables = databases.reduce((sum, db) => sum + db.tableCount, 0)
  const totalRows = databases.reduce((sum, db) => sum + db.totalRows, 0)

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
            <TableOutlined style={{ marginRight: 12 }} />
            Tables
          </Title>
          <Text type="secondary">Database schema and table browser</Text>
        </div>
      </div>

      {/* Stats Cards - CoreUI Style */}
      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Schemas"
            value={databases.length}
            style={statCardStyles.schemas}
            compact
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Tables"
            value={totalTables}
            style={statCardStyles.tables}
            compact
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Rows"
            value={totalRows}
            style={statCardStyles.rows}
            compact
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatCard
            title="Total Size"
            value={formatSize(totalSize)}
            style={statCardStyles.size}
            compact
          />
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
          {/* Selected DB Stats */}
          {selectedDb && (() => {
            const selectedDbInfo = databases.find(db => db.name === selectedDb)
            return selectedDbInfo && (
              <Col span={8}>
                <div style={{
                  display: 'flex',
                  gap: 24,
                  padding: '8px 16px',
                  background: '#fafafa',
                  borderRadius: 8,
                  marginTop: 8,
                }}>
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 18, fontWeight: 600, color: '#3c4b64' }}>
                      {selectedDbInfo.tableCount}
                    </div>
                    <div style={{ fontSize: 11, color: '#8c8c8c' }}>Tables</div>
                  </div>
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 18, fontWeight: 600, color: '#13deb9' }}>
                      {selectedDbInfo.totalRows.toLocaleString()}
                    </div>
                    <div style={{ fontSize: 11, color: '#8c8c8c' }}>Rows</div>
                  </div>
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 18, fontWeight: 600, color: '#ffae1f' }}>
                      {formatSize(selectedDbInfo.size)}
                    </div>
                    <div style={{ fontSize: 11, color: '#8c8c8c' }}>Size</div>
                  </div>
                </div>
              </Col>
            )
          })()}
          <Col flex="auto">
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
                disabled={!selectedDb || loading}
              >
                Refresh
              </Button>
              <Button
                icon={<BarChartOutlined />}
                onClick={handleGatherStats}
                disabled={!selectedDb || gatheringStats}
                loading={gatheringStats}
                danger
              >
                {gatheringStats ? 'Gathering...' : 'Gather Stats'}
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
          pagination={{
            current: currentPage,
            pageSize: pageSize,
            total: filteredTables.length,
            showSizeChanger: true,
            pageSizeOptions: ['10', '15', '20', '50', '100'],
            showTotal: (total, range) => `${range[0]}-${range[1]} of ${total}`,
            onChange: (page, size) => {
              setCurrentPage(page)
              if (size !== pageSize) setPageSize(size)
            },
          }}
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
                        title: <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{col}</span>,
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
