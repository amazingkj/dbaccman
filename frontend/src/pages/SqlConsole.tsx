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
  Divider,
} from 'antd'
import {
  PlayCircleOutlined,
  ClearOutlined,
  ClockCircleOutlined,
  TableOutlined,
  HistoryOutlined,
  CopyOutlined,
  UserOutlined,
  CodeOutlined,
  DatabaseOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import { queryApi, type QueryResult, type AuditLogEntry, type SchemaInfo } from '../api/query'
import { accountsApi } from '../api/accounts'
import type { Account } from '../types'
import { useAuthStore } from '../store/authStore'

const { Title, Text } = Typography
const { TextArea } = Input

interface QueryHistory {
  query: string
  account?: string
  timestamp: string
  success: boolean
  rowCount?: number
  executionTimeMs?: number
}

function SqlConsole() {
  const { user } = useAuthStore()
  const [query, setQuery] = useState('')
  const [selectedAccount, setSelectedAccount] = useState<string | undefined>()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<QueryResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [history, setHistory] = useState<QueryHistory[]>([])
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([])
  const [activeTab, setActiveTab] = useState('result')
  const [schemaInfo, setSchemaInfo] = useState<SchemaInfo | null>(null)
  const [schemaLoading, setSchemaLoading] = useState(false)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const textAreaRef = useRef<any>(null)

  const getDbTypeColor = (dbType?: string) => {
    switch (dbType?.toUpperCase()) {
      case 'MYSQL': return 'blue'
      case 'POSTGRESQL': return 'cyan'
      case 'ORACLE': return 'orange'
      default: return 'default'
    }
  }

  const fetchAccounts = useCallback(async () => {
    try {
      const response = await accountsApi.list()
      setAccounts(response.data)
    } catch {
      // Ignore error - accounts list is optional
    }
  }, [])

  const fetchSchemas = useCallback(async () => {
    setSchemaLoading(true)
    try {
      const response = await queryApi.getSchemas()
      setSchemaInfo(response.data)
    } catch {
      // Ignore error
    } finally {
      setSchemaLoading(false)
    }
  }, [])

  const handleSwitchSchema = async (schema: string) => {
    try {
      await queryApi.switchSchema(schema)
      message.success(`Switched to schema: ${schema}`)
      fetchSchemas() // Refresh current schema
    } catch (err: unknown) {
      const errorResponse = err as { response?: { data?: { error?: string } } }
      const errorMsg = errorResponse.response?.data?.error || 'Failed to switch schema'
      message.error(errorMsg)
    }
  }

  const fetchAuditLogs = useCallback(async () => {
    try {
      const response = await queryApi.getAuditLogs({ limit: 100 })
      setAuditLogs(response.data)
    } catch {
      // Ignore error
    }
  }, [])

  useEffect(() => {
    fetchAccounts()
    fetchSchemas()
  }, [fetchAccounts, fetchSchemas])

  const executeQuery = async () => {
    if (!query.trim()) {
      message.warning('Please enter a query')
      return
    }

    setLoading(true)
    setError(null)

    const timestamp = new Date().toLocaleString()

    try {
      const response = await queryApi.execute(query, selectedAccount)
      setResult(response.data)
      setActiveTab('result')

      // Add to history
      setHistory(prev => [{
        query,
        account: selectedAccount,
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
        account: selectedAccount,
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
    setSelectedAccount(item.account)
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
      {/* Header */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 24
      }}>
        <div>
          <Title level={2} style={{ margin: 0, marginBottom: 4 }}>
            <CodeOutlined style={{ marginRight: 12 }} />
            SQL Console
            {user?.dbType && (
              <Tag color={getDbTypeColor(user.dbType)} style={{ marginLeft: 12, fontSize: 14, verticalAlign: 'middle' }}>
                {user.dbType.toUpperCase()}
              </Tag>
            )}
          </Title>
          <Text type="secondary">Execute SQL queries and view results</Text>
        </div>
      </div>

      <Row gutter={[16, 16]}>
        <Col span={24}>
          <Card size="small" style={{ borderRadius: 8 }}>
            <Row gutter={16} align="middle" style={{ marginBottom: 12 }}>
              <Col>
                <Space>
                  <DatabaseOutlined />
                  <Text strong>Current Schema:</Text>
                  <Tag color="green" style={{ fontSize: 14 }}>
                    {schemaLoading ? 'Loading...' : (schemaInfo?.currentSchema || 'N/A')}
                  </Tag>
                </Space>
              </Col>
              <Col>
                <Space>
                  <SwapOutlined />
                  <Select
                    placeholder="Switch schema..."
                    style={{ width: 200 }}
                    showSearch
                    loading={schemaLoading}
                    value={undefined}
                    onChange={handleSwitchSchema}
                    optionFilterProp="label"
                    options={schemaInfo?.availableSchemas.map(schema => ({
                      value: schema,
                      label: schema,
                    })) || []}
                  />
                </Space>
              </Col>
              <Col flex="auto" />
              <Col>
                <Space>
                  <UserOutlined />
                  <Select
                    placeholder="Run as account"
                    style={{ width: 180 }}
                    allowClear
                    showSearch
                    value={selectedAccount}
                    onChange={setSelectedAccount}
                    optionFilterProp="label"
                    options={accounts.map(acc => ({
                      value: acc.username,
                      label: acc.username,
                    }))}
                  />
                </Space>
              </Col>
            </Row>
            <Divider style={{ margin: '8px 0' }} />
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
              ref={textAreaRef}
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
          <Card size="small" style={{ borderRadius: 8 }}>
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
                          <CodeOutlined style={{ fontSize: 48, marginBottom: 16 }} />
                          <div>Execute a query to see results</div>
                          {selectedAccount && (
                            <div style={{ marginTop: 8 }}>
                              <Tag color="purple" icon={<UserOutlined />}>
                                Running as: {selectedAccount}
                              </Tag>
                            </div>
                          )}
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
                                {item.account && (
                                  <Tag color="purple" icon={<UserOutlined />}>{item.account}</Tag>
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