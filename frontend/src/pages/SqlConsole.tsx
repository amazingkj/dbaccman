import { useState, useEffect, useCallback, useRef } from 'react'
import {
  Typography,
  Table,
  Button,
  Space,
  Select,
  message,
  Card,
  Row,
  Col,
  Statistic,
  Tabs,
  List,
  Tag,
  Alert,
  Tooltip,
  Input,
} from 'antd'
import {
  PlayCircleOutlined,
  ClearOutlined,
  ClockCircleOutlined,
  TableOutlined,
  HistoryOutlined,
  CopyOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import { queryApi, type QueryResult, type AuditLogEntry } from '../api/query'
import { tablesApi } from '../api/tables'
import type { DatabaseInfo } from '../types'

const { Title, Text } = Typography
const { TextArea } = Input

interface QueryHistory {
  query: string
  database?: string
  timestamp: string
  success: boolean
  rowCount?: number
  executionTimeMs?: number
}

function SqlConsole() {
  const [query, setQuery] = useState('')
  const [selectedDatabase, setSelectedDatabase] = useState<string | undefined>()
  const [databases, setDatabases] = useState<DatabaseInfo[]>([])
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<QueryResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [history, setHistory] = useState<QueryHistory[]>([])
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([])
  const [activeTab, setActiveTab] = useState('result')
  const textAreaRef = useRef<HTMLTextAreaElement>(null)

  const fetchDatabases = useCallback(async () => {
    try {
      const response = await tablesApi.getDatabases()
      setDatabases(response.data)
    } catch {
      // Ignore error - databases list is optional
    }
  }, [])

  const fetchAuditLogs = useCallback(async () => {
    try {
      const response = await queryApi.getAuditLogs({ limit: 100 })
      setAuditLogs(response.data)
    } catch {
      // Ignore error
    }
  }, [])

  useEffect(() => {
    fetchDatabases()
  }, [fetchDatabases])

  const executeQuery = async () => {
    if (!query.trim()) {
      message.warning('Please enter a query')
      return
    }

    setLoading(true)
    setError(null)

    const timestamp = new Date().toLocaleString()

    try {
      const response = await queryApi.execute(query, selectedDatabase)
      setResult(response.data)
      setActiveTab('result')

      // Add to history
      setHistory(prev => [{
        query,
        database: selectedDatabase,
        timestamp,
        success: true,
        rowCount: response.data.rowCount,
        executionTimeMs: response.data.executionTimeMs,
      }, ...prev.slice(0, 49)])

      if (response.data.isSelectQuery) {
        message.success(`Query executed: ${response.data.rowCount} rows in ${response.data.executionTimeMs}ms`)
      } else {
        message.success(`Query executed: ${response.data.affectedRows} rows affected in ${response.data.executionTimeMs}ms`)
      }
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { error?: string; executionTimeMs?: number } } }
      const errorMsg = errorResponse.response?.data?.error || 'Query execution failed'
      setError(errorMsg)
      setResult(null)

      // Add to history
      setHistory(prev => [{
        query,
        database: selectedDatabase,
        timestamp,
        success: false,
        executionTimeMs: errorResponse.response?.data?.executionTimeMs,
      }, ...prev.slice(0, 49)])
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    // Ctrl+Enter or Cmd+Enter to execute
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault()
      executeQuery()
    }
  }

  const loadFromHistory = (item: QueryHistory) => {
    setQuery(item.query)
    setSelectedDatabase(item.database)
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('Copied to clipboard')
  }

  const clearConsole = () => {
    setQuery('')
    setResult(null)
    setError(null)
    textAreaRef.current?.focus()
  }

  const columns = result?.columns.map((col, index) => ({
    title: col,
    dataIndex: index.toString(),
    key: col,
    ellipsis: true,
    width: 150,
    render: (value: string | null) => (
      <Tooltip title={value}>
        <span style={{ fontFamily: 'monospace', fontSize: 12 }}>
          {value === null ? <Text type="secondary">NULL</Text> : value}
        </span>
      </Tooltip>
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
      <Title level={2}>
        <DatabaseOutlined /> SQL Console
      </Title>

      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card size="small">
            <Space style={{ marginBottom: 12, width: '100%' }} wrap>
              <Select
                placeholder="Select database"
                style={{ width: 200 }}
                allowClear
                value={selectedDatabase}
                onChange={setSelectedDatabase}
                options={databases.map(db => ({ value: db.name, label: db.name }))}
              />
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
              <Button
                icon={<HistoryOutlined />}
                onClick={() => {
                  fetchAuditLogs()
                  setActiveTab('audit')
                }}
              >
                Audit Logs
              </Button>
            </Space>

            <TextArea
              ref={textAreaRef as React.RefObject<HTMLTextAreaElement & { resizableTextArea: unknown }>}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Enter your SQL query here...&#10;&#10;Shortcuts:&#10;  Ctrl+Enter - Execute query"
              autoSize={{ minRows: 6, maxRows: 15 }}
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
          <Card size="small">
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              items={[
                {
                  key: 'result',
                  label: (
                    <span>
                      <TableOutlined /> Results
                      {result && <Tag color="blue" style={{ marginLeft: 8 }}>{result.rowCount}</Tag>}
                    </span>
                  ),
                  children: (
                    <div>
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
                            {result.affectedRows !== undefined && (
                              <Col>
                                <Statistic
                                  title="Affected Rows"
                                  value={result.affectedRows}
                                />
                              </Col>
                            )}
                          </Row>

                          <Table
                            columns={columns}
                            dataSource={dataSource}
                            size="small"
                            scroll={{ x: 'max-content', y: 400 }}
                            pagination={{
                              pageSize: 50,
                              showSizeChanger: true,
                              showTotal: (total) => `Total ${total} rows`,
                            }}
                          />
                        </>
                      )}

                      {!result && !error && (
                        <div style={{ textAlign: 'center', padding: 40, color: '#888' }}>
                          <DatabaseOutlined style={{ fontSize: 48, marginBottom: 16 }} />
                          <div>Execute a query to see results</div>
                        </div>
                      )}
                    </div>
                  ),
                },
                {
                  key: 'history',
                  label: (
                    <span>
                      <HistoryOutlined /> History
                      {history.length > 0 && <Tag style={{ marginLeft: 8 }}>{history.length}</Tag>}
                    </span>
                  ),
                  children: (
                    <List
                      size="small"
                      dataSource={history}
                      locale={{ emptyText: 'No query history' }}
                      renderItem={(item) => (
                        <List.Item
                          actions={[
                            <Button
                              key="load"
                              type="link"
                              size="small"
                              onClick={() => loadFromHistory(item)}
                            >
                              Load
                            </Button>,
                            <Button
                              key="copy"
                              type="link"
                              size="small"
                              icon={<CopyOutlined />}
                              onClick={() => copyToClipboard(item.query)}
                            />,
                          ]}
                        >
                          <List.Item.Meta
                            title={
                              <Space>
                                <Tag color={item.success ? 'success' : 'error'}>
                                  {item.success ? 'OK' : 'ERR'}
                                </Tag>
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                  {item.timestamp}
                                </Text>
                                {item.database && (
                                  <Tag color="blue">{item.database}</Tag>
                                )}
                                {item.rowCount !== undefined && (
                                  <Text type="secondary" style={{ fontSize: 12 }}>
                                    {item.rowCount} rows
                                  </Text>
                                )}
                                {item.executionTimeMs !== undefined && (
                                  <Text type="secondary" style={{ fontSize: 12 }}>
                                    {item.executionTimeMs}ms
                                  </Text>
                                )}
                              </Space>
                            }
                            description={
                              <Text
                                code
                                style={{
                                  fontSize: 12,
                                  display: 'block',
                                  whiteSpace: 'nowrap',
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  maxWidth: '100%',
                                }}
                              >
                                {item.query.substring(0, 100)}
                                {item.query.length > 100 && '...'}
                              </Text>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  ),
                },
                {
                  key: 'audit',
                  label: (
                    <span>
                      <ClockCircleOutlined /> Audit Logs
                    </span>
                  ),
                  children: (
                    <List
                      size="small"
                      dataSource={auditLogs}
                      locale={{ emptyText: 'No audit logs' }}
                      renderItem={(item) => (
                        <List.Item>
                          <List.Item.Meta
                            title={
                              <Space>
                                <Tag color={
                                  item.action === 'LOGIN' ? 'green' :
                                  item.action === 'LOGIN_FAILED' ? 'red' :
                                  item.action === 'LOGOUT' ? 'orange' :
                                  item.action === 'QUERY_EXECUTE' ? 'blue' :
                                  'default'
                                }>
                                  {item.action}
                                </Tag>
                                <Text type="secondary" style={{ fontSize: 12 }}>
                                  {item.timestamp}
                                </Text>
                                {item.user && (
                                  <Text strong>{item.user}</Text>
                                )}
                                {item.ipAddress && (
                                  <Tag>{item.ipAddress}</Tag>
                                )}
                              </Space>
                            }
                            description={
                              <div>
                                <Text style={{ fontSize: 12 }}>{item.message}</Text>
                                {item.target && (
                                  <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                                    Target: {item.target}
                                  </Text>
                                )}
                                {item.details && (
                                  <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                                    - {item.details}
                                  </Text>
                                )}
                              </div>
                            }
                          />
                        </List.Item>
                      )}
                    />
                  ),
                },
              ]}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}

export default SqlConsole