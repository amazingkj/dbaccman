import { useState, useEffect } from 'react'
import {
  Typography,
  Table,
  Card,
  Button,
  Space,
  message,
  Modal,
  Descriptions,
  Tag,
  Spin,
} from 'antd'
import {
  TableOutlined,
  ReloadOutlined,
  EyeOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import { userApi, type UserTable, type ColumnInfo, type IndexInfo } from '../api/user'
import { useAuthStore } from '../store/authStore'

const { Title, Text } = Typography

function MyTables() {
  const { user } = useAuthStore()
  const [tables, setTables] = useState<UserTable[]>([])
  const [loading, setLoading] = useState(false)
  const [selectedTable, setSelectedTable] = useState<UserTable | null>(null)
  const [columns, setColumns] = useState<ColumnInfo[]>([])
  const [indexes, setIndexes] = useState<IndexInfo[]>([])
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [tableData, setTableData] = useState<{ columns: string[]; rows: (string | null)[][] } | null>(null)
  const [dataModalOpen, setDataModalOpen] = useState(false)
  const [dataLoading, setDataLoading] = useState(false)

  const fetchTables = async () => {
    setLoading(true)
    try {
      const response = await userApi.getTables()
      setTables(response.data)
    } catch {
      message.error('Failed to fetch tables')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchTables()
  }, [])

  const handleViewDetails = async (table: UserTable) => {
    setSelectedTable(table)
    setDetailLoading(true)
    setDetailModalOpen(true)

    try {
      const [columnsRes, indexesRes] = await Promise.all([
        userApi.getTableColumns(table.tableName),
        userApi.getTableIndexes(table.tableName),
      ])
      setColumns(columnsRes.data)
      setIndexes(indexesRes.data)
    } catch {
      message.error('Failed to fetch table details')
    } finally {
      setDetailLoading(false)
    }
  }

  const handleViewData = async (table: UserTable) => {
    setSelectedTable(table)
    setDataLoading(true)
    setDataModalOpen(true)

    try {
      const response = await userApi.getTableData(table.tableName, 100)
      setTableData(response.data)
    } catch {
      message.error('Failed to fetch table data')
    } finally {
      setDataLoading(false)
    }
  }

  const tableColumns = [
    {
      title: '#',
      key: 'index',
      width: 50,
      render: (_: unknown, __: unknown, index: number) => index + 1,
    },
    {
      title: 'Table Name',
      dataIndex: 'tableName',
      key: 'tableName',
      render: (name: string) => (
        <Space>
          <TableOutlined style={{ color: '#1890ff' }} />
          <Text strong style={{ fontFamily: 'monospace' }}>{name}</Text>
        </Space>
      ),
    },
    {
      title: 'Rows',
      dataIndex: 'rowCount',
      key: 'rowCount',
      width: 120,
      align: 'right' as const,
      render: (count: number | null) => count?.toLocaleString() ?? '-',
    },
    {
      title: 'Tablespace',
      dataIndex: 'tablespaceName',
      key: 'tablespaceName',
      width: 150,
      render: (name: string | null) => name ? <Tag>{name}</Tag> : '-',
    },
    {
      title: 'Last Analyzed',
      dataIndex: 'lastAnalyzed',
      key: 'lastAnalyzed',
      width: 180,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 150,
      render: (_: unknown, record: UserTable) => (
        <Space>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetails(record)}
          >
            Structure
          </Button>
          <Button
            size="small"
            icon={<DatabaseOutlined />}
            onClick={() => handleViewData(record)}
          >
            Data
          </Button>
        </Space>
      ),
    },
  ]

  const columnTableColumns = [
    { title: 'Column', dataIndex: 'name', key: 'name' },
    { title: 'Type', dataIndex: 'type', key: 'type' },
    {
      title: 'Nullable',
      dataIndex: 'nullable',
      key: 'nullable',
      render: (v: boolean) => v ? 'YES' : 'NO',
    },
    { title: 'Default', dataIndex: 'defaultValue', key: 'defaultValue' },
  ]

  const indexTableColumns = [
    { title: 'Index Name', dataIndex: 'name', key: 'name' },
    { title: 'Type', dataIndex: 'type', key: 'type' },
    {
      title: 'Unique',
      dataIndex: 'unique',
      key: 'unique',
      render: (v: boolean) => v ? <Tag color="blue">UNIQUE</Tag> : 'NO',
    },
    {
      title: 'Columns',
      dataIndex: 'columns',
      key: 'columns',
      render: (cols: string[]) => cols.join(', '),
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, marginBottom: 4 }}>
            <TableOutlined style={{ marginRight: 12 }} />
            My Tables
          </Title>
          <Text type="secondary">Tables owned by {user?.username}</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={fetchTables} loading={loading}>
          Refresh
        </Button>
      </div>

      <Card>
        <Table
          columns={tableColumns}
          dataSource={tables}
          rowKey="tableName"
          loading={loading}
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `Total ${total} tables`,
          }}
        />
      </Card>

      {/* Table Structure Modal */}
      <Modal
        title={`Table Structure: ${selectedTable?.tableName}`}
        open={detailModalOpen}
        onCancel={() => setDetailModalOpen(false)}
        footer={null}
        width={800}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
          </div>
        ) : (
          <>
            <Descriptions bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="Table Name">{selectedTable?.tableName}</Descriptions.Item>
              <Descriptions.Item label="Rows">{selectedTable?.rowCount?.toLocaleString() ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="Tablespace">{selectedTable?.tablespaceName ?? '-'}</Descriptions.Item>
            </Descriptions>

            <Title level={5}>Columns</Title>
            <Table
              columns={columnTableColumns}
              dataSource={columns}
              rowKey="name"
              size="small"
              pagination={false}
              style={{ marginBottom: 16 }}
            />

            <Title level={5}>Indexes</Title>
            <Table
              columns={indexTableColumns}
              dataSource={indexes}
              rowKey="name"
              size="small"
              pagination={false}
            />
          </>
        )}
      </Modal>

      {/* Table Data Modal */}
      <Modal
        title={`Table Data: ${selectedTable?.tableName}`}
        open={dataModalOpen}
        onCancel={() => setDataModalOpen(false)}
        footer={null}
        width={1000}
      >
        {dataLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
          </div>
        ) : tableData ? (
          <Table
            columns={tableData.columns.map((col) => ({
              title: col,
              dataIndex: col,
              key: col,
              ellipsis: true,
            }))}
            dataSource={tableData.rows.map((row, i) => {
              const rowObj: Record<string, string | null> = { key: i.toString() }
              tableData.columns.forEach((col, j) => {
                rowObj[col] = row[j]
              })
              return rowObj
            })}
            size="small"
            scroll={{ x: 'max-content', y: 400 }}
            pagination={{ pageSize: 50 }}
          />
        ) : null}
      </Modal>
    </div>
  )
}

export default MyTables
