import { useState, useRef } from 'react'
import type { TextAreaRef } from 'antd/es/input/TextArea'
import {
  Typography,
  Table,
  Button,
  Space,
  message,
  Card,
  Row,
  Col,
  Statistic,
  Alert,
  Input,
  Divider,
} from 'antd'
import {
  PlayCircleOutlined,
  ClearOutlined,
  ClockCircleOutlined,
  TableOutlined,
  CodeOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import { userApi, type UserQueryResult } from '../api/user'
import { useAuthStore } from '../store/authStore'

const { Title, Text } = Typography
const { TextArea } = Input

function MyConsole() {
  const { user } = useAuthStore()
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<UserQueryResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const textAreaRef = useRef<TextAreaRef>(null)

  const executeQuery = async () => {
    let queryToExecute = query
    const textarea = textAreaRef.current?.resizableTextArea?.textArea
    if (textarea) {
      const { selectionStart, selectionEnd } = textarea
      if (selectionStart !== selectionEnd) {
        queryToExecute = query.substring(selectionStart, selectionEnd)
      }
    }

    if (!queryToExecute.trim()) {
      message.warning('Please enter a query')
      return
    }

    setLoading(true)
    setError(null)

    try {
      const response = await userApi.executeQuery(queryToExecute, 1000)
      setResult(response.data)
      message.success(`Query executed: ${response.data.rowCount} rows in ${response.data.executionTimeMs}ms`)
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { error?: string } } }
      const errorMsg = errorResponse.response?.data?.error || 'Query execution failed'
      setError(errorMsg)
      setResult(null)
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault()
      executeQuery()
    }
  }

  const clearConsole = () => {
    setQuery('')
    setResult(null)
    setError(null)
    textAreaRef.current?.focus()
  }

  const columns = result?.columns.map((col, index) => ({
    title: <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{col}</span>,
    dataIndex: index.toString(),
    key: col,
    ellipsis: true,
    width: 150,
    render: (value: string | null) => (
      <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
        {value === null ? <Text type="secondary">NULL</Text> : value}
      </span>
    ),
  })) || []

  const dataSource = result?.rows.map((row, rowIndex) => {
    const rowData: Record<string, string | null> = { key: rowIndex.toString() }
    row.forEach((cell, cellIndex) => {
      rowData[cellIndex.toString()] = cell
    })
    return rowData
  }) || []

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={2} style={{ margin: 0, marginBottom: 4 }}>
            <CodeOutlined style={{ marginRight: 12 }} />
            My SQL Console
          </Title>
          <Text type="secondary">Execute queries on your own schema ({user?.username})</Text>
        </div>
      </div>

      <Alert
        message="Schema Restrictions"
        description={
          <ul style={{ margin: '8px 0 0 0', paddingLeft: 20 }}>
            <li>You can execute queries based on your granted permissions</li>
            <li>Queries are restricted to your own schema only</li>
            <li>Use <code>USER_TABLES</code>, <code>USER_TAB_COLUMNS</code>, etc. for metadata</li>
            <li>Queries to <code>DBA_*</code>, <code>ALL_*</code>, <code>V$*</code> views are restricted</li>
          </ul>
        }
        type="info"
        icon={<InfoCircleOutlined />}
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Space style={{ marginBottom: 12 }} wrap>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                onClick={executeQuery}
                loading={loading}
              >
                Execute (Ctrl+Enter)
              </Button>
              <Button
                icon={<ClearOutlined />}
                onClick={clearConsole}
              >
                Clear
              </Button>
            </Space>

            <TextArea
              ref={textAreaRef}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={`Enter your SQL query here...\n\nExamples:\n  SELECT * FROM my_table\n  SELECT * FROM USER_TABLES\n  SELECT * FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'MY_TABLE'`}
              autoSize={{ minRows: 6, maxRows: 12 }}
              style={{
                fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
                fontSize: 14,
                backgroundColor: '#1e1e1e',
                color: '#d4d4d4',
              }}
            />
          </Card>
        </Col>

        <Col span={24}>
          <Card size="small" style={{ borderRadius: 8 }} title={
            <Space>
              <TableOutlined />
              Results
              {result && <span style={{ color: '#1890ff' }}>({result.rowCount} rows)</span>}
            </Space>
          }>
            {error && (
              <Alert
                message="Query Error"
                description={error}
                type="error"
                showIcon
                style={{ marginBottom: 16 }}
              />
            )}

            {result && (
              <>
                <Row gutter={16} style={{ marginBottom: 16 }}>
                  <Col>
                    <Statistic
                      title="Rows"
                      value={result.rowCount}
                      prefix={<TableOutlined />}
                    />
                  </Col>
                  <Col>
                    <Statistic
                      title="Execution Time"
                      value={result.executionTimeMs}
                      suffix="ms"
                      prefix={<ClockCircleOutlined />}
                    />
                  </Col>
                </Row>

                <Divider style={{ margin: '12px 0' }} />

                <Table
                  columns={columns}
                  dataSource={dataSource}
                  size="small"
                  scroll={{ x: 'max-content', y: 400 }}
                  pagination={{
                    pageSize: 50,
                    showSizeChanger: true,
                    pageSizeOptions: ['20', '50', '100'],
                    showTotal: (total) => `Total ${total} rows`,
                  }}
                  style={{
                    fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
                  }}
                />
              </>
            )}

            {!result && !error && (
              <div style={{ textAlign: 'center', padding: 40, color: '#888' }}>
                <CodeOutlined style={{ fontSize: 48, marginBottom: 16 }} />
                <div>Execute a query to see results</div>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default MyConsole
