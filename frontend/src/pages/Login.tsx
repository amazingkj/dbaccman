import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Space, Select, InputNumber, Divider, Alert, Row, Col } from 'antd'
import { UserOutlined, LockOutlined, DatabaseOutlined, GlobalOutlined, DeleteOutlined, NumberOutlined } from '@ant-design/icons'
import { AxiosError } from 'axios'
import { useAuth } from '../hooks/useAuth'
import { useAuthStore } from '../store/authStore'
import { useConnectionStore } from '../store/connectionStore'
import type { LoginRequest, RecentConnection, DatabaseType, ApiError } from '../types'
import { DATABASE_TYPES, getDefaultPort } from '../types'

const { Title, Text } = Typography

// DB Type Icons as SVG components
const MySQLIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="#00758F">
    <path d="M21 16.5c-.5-.5-1.5-1-2.5-1.2-.5-.1-1-.2-1.5-.2-1.5 0-3 .5-4.5 1.5-1.5-1-3-1.5-4.5-1.5-.5 0-1 .1-1.5.2-1 .2-2 .7-2.5 1.2-.3.3-.5.7-.5 1s.1.6.3.8c.4.4 1 .5 1.7.5.5 0 1-.1 1.5-.2 1-.3 2-.8 3-1.5 1 .7 2 1.2 3 1.5.5.1 1 .2 1.5.2.7 0 1.3-.1 1.7-.5.2-.2.3-.5.3-.8s-.2-.7-.5-1zM10 11c0-2.2 1.8-4 4-4 .7 0 1.4.2 2 .5-.4-.8-1-1.5-1.8-2-.6-.4-1.4-.5-2.2-.5-2.2 0-4 1.8-4 4 0 1.5.8 2.8 2 3.5-.6-.9-1-2-1-3.5zm2-7c-.5 0-1 .1-1.4.3.6.2 1.1.5 1.6.9.4-.3.9-.6 1.4-.8-.5-.3-1-.4-1.6-.4z"/>
  </svg>
)

const OracleIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="#F80000">
    <path d="M7.076 7.076C4.807 7.076 3 8.883 3 11.152v1.696C3 15.117 4.807 16.924 7.076 16.924h9.848c2.269 0 4.076-1.807 4.076-4.076v-1.696c0-2.269-1.807-4.076-4.076-4.076H7.076zm0 1.848h9.848c1.232 0 2.228.996 2.228 2.228v1.696c0 1.232-.996 2.228-2.228 2.228H7.076c-1.232 0-2.228-.996-2.228-2.228v-1.696c0-1.232.996-2.228 2.228-2.228z"/>
  </svg>
)

const PostgreSQLIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="#336791">
    <path d="M17.128 0a10.134 10.134 0 0 0-2.755.403l-.063.02a10.922 10.922 0 0 0-1.612-.143c-.47-.001-.95.017-1.428.052-.986-.467-2.148-.775-3.342-.782a7.747 7.747 0 0 0-.68.03C3.714-.05 1.267 2.56.543 6.17a22.104 22.104 0 0 0-.066 10.39c.604 2.564 2.596 6.164 5.254 5.37a.462.462 0 0 0 .09-.037c.33.206.706.341 1.086.395.738.104 1.476.093 2.19.018.376-.04.73-.118 1.065-.23.143.23.297.418.374.588.203.44.282.863.24 1.313v.015l-.004.128c0 .008.003.015.004.023.002.144.023.287.06.424.043.154.12.296.2.438.195.344.55.694 1.018.976.394.237.855.419 1.33.497.14.023.282.03.427.03.337 0 .695-.054 1.05-.155a4.04 4.04 0 0 0 1.59-.826 3.73 3.73 0 0 0 .453-.52l.052.003c.56.027 1.16-.04 1.66-.176.505-.137.932-.344 1.163-.556.157-.144.314-.402.402-.707.088-.303.107-.67.02-1.01a2.17 2.17 0 0 0-.256-.59c.1-.062.195-.128.288-.196.382-.284.66-.623.85-.967.256-.46.375-.96.387-1.358.013-.407-.073-.706-.082-.73l-.005-.013a3.79 3.79 0 0 0-.115-.278l.013-.152c.024-.277.05-.557.08-.837.107-1.015.233-2.074.234-3.107.002-1.158-.129-2.287-.627-3.2-.228-.418-.533-.8-.91-1.127a4.233 4.233 0 0 0-.398-.296c.152-.768.168-1.51.096-2.195-.106-1.004-.394-1.9-.883-2.52a3.082 3.082 0 0 0-.493-.5 2.47 2.47 0 0 0-.4-.27c-.48-.253-1.034-.355-1.66-.333zm-.126 1.007c.453-.017.845.046 1.152.207.13.068.253.16.362.274.11.113.21.257.273.373.322.595.505 1.406.553 2.273a6.77 6.77 0 0 1-.075 1.454 4.027 4.027 0 0 0-1.056-.263c-.74-.078-1.48-.03-2.258.103a8.97 8.97 0 0 0-.573-1.264 6.896 6.896 0 0 0-.86-1.212c.042-.188.093-.37.153-.545.21-.608.472-1.075.8-1.338.16-.128.32-.204.52-.24.1-.017.2-.032.31-.035.1-.003.19-.002.27-.002a.93.93 0 0 1 .072.002l.017.002.03-.002c.112-.004.22-.005.31-.007zM8.144 1.605c1.168.004 2.24.345 3.027.882a6.7 6.7 0 0 1 1.048 1.074 8.91 8.91 0 0 1 .712 1.143 9.57 9.57 0 0 1 .453 1.032c-1.26.34-2.516.96-3.674 1.965a9.5 9.5 0 0 0-.934.952c-.127-.025-.256-.048-.386-.068-.756-.116-1.514-.12-2.2.019-.687.138-1.313.405-1.835.857-.05.043-.1.09-.15.137a4.158 4.158 0 0 0-.076-.27c-.223-.702-.58-1.41-1.112-1.973-.32-.34-.7-.625-1.115-.808.333-1.618 1.172-2.94 2.266-3.798.95-.744 2.085-1.138 3.272-1.14a6.45 6.45 0 0 1 .704.004zm5.587 3.856c.17-.003.33.003.483.018.546.057 1.007.22 1.382.52.375.3.658.712.863 1.207.328.792.44 1.762.438 2.84-.002.978-.123 1.995-.228 2.992-.03.28-.057.56-.08.837-.014.165-.027.33-.033.495-.058.012-.116.02-.174.035-.573.145-1.126.36-1.5.635-.235.174-.42.356-.553.57a1.44 1.44 0 0 0-.175.427c-.034.15-.038.298-.02.437.037.28.16.51.334.668.15.136.33.22.515.284-.054.17-.1.343-.14.52-.147.64-.216 1.31-.123 1.87.007.04.007.064.015.1-.234.17-.547.313-.892.396-.366.087-.755.117-1.09.076a1.8 1.8 0 0 1-.266-.06c.047-.4.03-.814-.15-1.212-.12-.27-.315-.53-.55-.784.07-.173.12-.353.145-.535.11-.762-.14-1.496-.57-2.14a6.327 6.327 0 0 0-.838-.996c-.04-.04-.088-.074-.128-.113.25-.358.528-.693.828-1.002 1.163-1.204 2.562-2.023 3.907-2.302.658-.136 1.3-.14 1.84-.002.268.068.51.177.717.323.226.159.43.36.594.587.15.21.267.436.36.675a.4.4 0 0 0 .03.058c.204.418.303.912.372 1.427a.536.536 0 0 0 .018.087c.07.38.168.848.298 1.378.14.57.24 1.19.135 1.72-.053.263-.143.48-.282.658-.14.18-.32.326-.58.452-.097.047-.098.056-.186.092l-.013-.017c-.134-.18-.293-.358-.544-.49a1.494 1.494 0 0 0-.608-.186c-.228-.026-.457-.006-.673.074a1.213 1.213 0 0 0-.515.363c-.14.166-.237.377-.264.606-.027.227.014.47.135.68.113.194.29.35.5.45a1.45 1.45 0 0 0 .698.14c.094-.006.19-.02.278-.04-.037.122-.048.252-.04.386.026.434.167.812.384 1.13a2.867 2.867 0 0 0 .784.762c-.26.3-.585.53-.954.677-.396.16-.847.24-1.338.24a1.72 1.72 0 0 1-.27-.017 3.55 3.55 0 0 1-1.148-.38c-.345-.186-.624-.42-.764-.638a.834.834 0 0 1-.11-.22c-.018-.066-.033-.172-.028-.303l.01-.25c.002-.03.007-.06.005-.09.054-.574-.04-1.12-.295-1.648a3.39 3.39 0 0 0-.263-.436 4.093 4.093 0 0 0-.186-.26c.23-.173.433-.377.604-.602.343-.45.588-.995.658-1.624.073-.654-.044-1.357-.37-2.022-.323-.662-.81-1.267-1.37-1.716-1.108-.885-2.423-1.1-3.578-.64l-.028.012c.07-.17.147-.336.233-.496.254-.472.565-.894.91-1.248.347-.357.72-.638 1.095-.832.394-.203.794-.32 1.188-.345h.005c.07-.005.14-.006.21-.005zm-8.237.67c.238.087.456.234.646.434.38.398.67.984.856 1.55.034.102.06.206.085.31a5.103 5.103 0 0 0-1.036 1.04 6.367 6.367 0 0 0-.886 1.727 7.31 7.31 0 0 0-.313 1.642c-.057.816.017 1.622.272 2.298.273.722.714 1.3 1.332 1.66-.015.083-.034.167-.05.256-.053.3-.068.622-.024.917.105.695.485 1.28 1.024 1.68.093.069.195.13.298.188-.087.278-.155.562-.19.86-.096.81.014 1.665.298 2.4.038.097.083.19.128.286-.16.03-.326.05-.494.06-.363.023-.717-.013-1.047-.1-.33-.09-.642-.23-.908-.416l-.01-.007c-.1-.07-.19-.145-.27-.224.02-.15.043-.303.06-.458.112-.963.126-1.888-.186-2.78-.308-.88-1.016-1.624-1.77-2.1-.59-.372-.898-.937-1.058-1.64a13.4 13.4 0 0 1-.273-2.048c-.06-1.06-.04-2.18.123-3.17.238-1.45.75-2.634 1.552-3.314.4-.34.854-.554 1.348-.634.246-.04.503-.05.767-.03h.01c.21.02.413.055.606.108zm2.494 1.854a5.37 5.37 0 0 1 1.14.017c.45.058.89.187 1.26.393.365.205.67.494.876.863.22.397.33.885.334 1.428.003.547-.09 1.13-.245 1.714-.308 1.16-.837 2.31-1.377 3.168a9.6 9.6 0 0 1-.385.56l-.04-.05a4.363 4.363 0 0 0-.553-.608c-.515-.47-1.148-.84-1.78-1.047-.31-.103-.62-.167-.925-.186-.29-.018-.577.002-.846.072l.04-.1c.16-.397.35-.77.573-1.1.433-.646 1.008-1.12 1.702-1.318.26-.073.5-.175.726-.307.446-.26.795-.658.968-1.133.076-.21.102-.427.098-.63-.004-.226-.042-.44-.104-.636a2.87 2.87 0 0 0-.44-.8 3.09 3.09 0 0 0-.348-.385 4.5 4.5 0 0 0-.68-.52zm5.12.1c.32-.043.635-.058.924-.044a2.61 2.61 0 0 1 .53.074c.094.024.178.056.262.09a3.18 3.18 0 0 0-.176.6 8.195 8.195 0 0 0-.088 1.303c.01.535.078 1.085.206 1.635-.112.102-.234.188-.37.26-.172.088-.355.15-.58.13h-.007c-.33-.03-.55-.24-.69-.533-.143-.3-.2-.686-.198-1.1.003-.413.064-.85.154-1.256.07-.32.162-.625.255-.87.086-.176-.084-.178-.222-.29zm-3.99 2.213c-.528.135-.96.425-1.31.82-.286.326-.507.72-.68 1.15.007-.095.016-.19.028-.282.048-.37.13-.723.24-1.046.234-.676.59-1.2.96-1.412.127-.072.246-.118.375-.14.09-.017.2-.022.323-.006.057.007.116.02.176.037-.04.294-.072.59-.095.885zm4.26.61c.22-.003.435.04.624.137.19.097.355.244.478.44.124.195.185.42.17.665-.013.232-.1.446-.232.625-.163.22-.388.364-.625.444a3.08 3.08 0 0 1-.393.097c-.01-.035-.026-.068-.035-.104-.156-.61-.245-1.196-.216-1.698.013-.233.05-.438.104-.583.027-.07.054-.115.086-.147s.063-.054.1-.062c.05-.012.1-.018.154-.02.068-.002.17.005.275.02.15-.038.305-.056.453-.066a2.73 2.73 0 0 1-.193-.202c-.106-.126-.2-.257-.278-.393l-.058-.105c-.084.006-.168.016-.25.03-.054.01-.11.014-.164.02zm-7.56 2.12c.103.007.205.025.307.056.512.157 1.043.467 1.472.858.215.196.405.415.554.658.147.24.25.502.293.782.04.262.016.55-.068.828-.086.28-.23.535-.418.743a2.165 2.165 0 0 1-.656.492c-.195.1-.41.166-.635.2-.1-.08-.195-.165-.285-.255-.27-.273-.483-.592-.61-.926-.19-.5-.22-1.103-.176-1.724.033-.465.115-.938.252-1.393.02-.08.04-.158.063-.233a2.45 2.45 0 0 1-.153-.068zm.893.96c-.055.32-.086.645-.09.968-.01.645.074 1.302.33 1.817a2.06 2.06 0 0 0 .348.512 2.75 2.75 0 0 1-.302.017c-.193-.003-.394-.028-.58-.082a1.28 1.28 0 0 1-.482-.252 1.127 1.127 0 0 1-.322-.454 1.36 1.36 0 0 1-.095-.47c-.007-.146.015-.287.062-.43.086-.26.234-.525.43-.772.21-.264.46-.503.733-.704.09-.065.18-.114.273-.164zm8.19 2.044c.17-.006.33.035.46.126.14.092.248.23.316.398.067.167.09.36.06.55a.98.98 0 0 1-.222.48 1.09 1.09 0 0 1-.42.305c-.17.073-.36.106-.55.09a1.05 1.05 0 0 1-.5-.183 1.01 1.01 0 0 1-.337-.405.94.94 0 0 1-.07-.51c.03-.18.11-.345.235-.477a1.02 1.02 0 0 1 .45-.287c.13-.045.27-.065.41-.07.054-.003.11-.007.166-.016z"/>
  </svg>
)

function Login() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const [selectedDbType, setSelectedDbType] = useState<DatabaseType>('MYSQL')
  const [loginError, setLoginError] = useState<string | null>(null)
  const { login } = useAuth()
  const { isAuthenticated } = useAuthStore()
  const { recentConnections, removeRecentConnection } = useConnectionStore()

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  const handleSubmit = async (values: LoginRequest) => {
    setLoading(true)
    setLoginError(null)
    try {
      await login(values)
    } catch (error) {
      if (error instanceof AxiosError) {
        const errorMsg = (error.response?.data as ApiError)?.error || 'Connection failed. Please check your credentials.'
        setLoginError(errorMsg)
      } else {
        setLoginError('Connection failed. Please check your credentials.')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleSelectRecentConnection = (value: string) => {
    const connection = recentConnections.find(
      (c) => `${c.username}@${c.host}:${c.port}` === value
    )
    if (connection) {
      const dbType = connection.dbType || 'MYSQL'
      setSelectedDbType(dbType)
      form.setFieldsValue({
        dbType,
        host: connection.host,
        port: connection.port,
        username: connection.username,
        password: '',
        database: connection.database,
      })
    }
  }

  const handleRemoveRecentConnection = (
    e: React.MouseEvent,
    connection: RecentConnection
  ) => {
    e.stopPropagation()
    removeRecentConnection(connection.host, connection.port, connection.username)
  }

  const handleDbTypeChange = (dbType: DatabaseType) => {
    setSelectedDbType(dbType)
    const currentPort = form.getFieldValue('port')

    // Check if current port is one of the default ports
    const isDefaultPort = DATABASE_TYPES.some(t => t.defaultPort === currentPort)

    // Change port if it's empty or one of the default ports
    if (!currentPort || isDefaultPort) {
      form.setFieldsValue({ port: getDefaultPort(dbType) })
    }

    // Clear database field when switching DB type
    form.setFieldsValue({ database: undefined })
  }

  const getDatabaseFieldConfig = () => {
    switch (selectedDbType) {
      case 'ORACLE':
        return {
          label: 'Service Name',
          placeholder: 'e.g., ORCL, XEPDB1, FREEPDB1',
          required: true,
          tooltip: 'Oracle Service Name or SID',
        }
      case 'POSTGRESQL':
        return {
          label: 'Database',
          placeholder: 'postgres',
          required: false,
          tooltip: 'Database name (default: postgres)',
        }
      default:
        return {
          label: 'Database',
          placeholder: 'Optional',
          required: false,
          tooltip: 'Database/Schema name (optional)',
        }
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: 'linear-gradient(135deg, #eef2f6 0%, #e3e9f0 100%)',
      }}
    >
      <style>
        {`
          .login-card {
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
          }

          .login-card .ant-form-item {
            margin-bottom: 16px;
          }

          .login-card .ant-form-item-label > label {
            font-size: 13px;
            color: #5a6a85;
          }

          .login-card .ant-input,
          .login-card .ant-input-number,
          .login-card .ant-select-selector {
            font-size: 14px;
          }
        `}
      </style>
      <Card
        className="login-card"
        style={{
          width: 480,
          boxShadow: '0 9px 17.5px rgba(0,0,0,0.05)',
          borderRadius: 16,
          border: 'none',
        }}
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{
              width: 64,
              height: 64,
              borderRadius: 12,
              background: 'linear-gradient(135deg, #5d87ff 0%, #4570ea 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 16px',
            }}>
              <DatabaseOutlined style={{ fontSize: 32, color: '#fff' }} />
            </div>
            <Title
              level={2}
              style={{
                margin: '0 0 4px',
                fontFamily: "'Plus Jakarta Sans', sans-serif",
                fontWeight: 700,
                color: '#2a3547',
              }}
            >
              D-BAM
            </Title>
            <Text style={{ color: '#5a6a85' }}>Database Account Manager</Text>
          </div>

          {loginError && (
            <Alert
              message="Connection Failed"
              description={loginError}
              type="error"
              showIcon
              closable
              onClose={() => setLoginError(null)}
            />
          )}

          {recentConnections.length > 0 && (
            <>
              <Select
                placeholder="Select recent connection"
                style={{ width: '100%' }}
                onChange={handleSelectRecentConnection}
                allowClear
                optionLabelProp="label"
              >
                {recentConnections.map((conn) => {
                  const key = `${conn.username}@${conn.host}:${conn.port}`
                  const dbType = conn.dbType || 'MYSQL'
                  const DbIcon = dbType === 'ORACLE' ? OracleIcon : dbType === 'POSTGRESQL' ? PostgreSQLIcon : MySQLIcon
                  const dbTypeLabel = DATABASE_TYPES.find(t => t.value === dbType)?.label || 'MySQL'
                  return (
                    <Select.Option key={key} value={key} label={`${dbTypeLabel} - ${key}`}>
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                        }}
                      >
                        <Space>
                          <DbIcon />
                          <span>{dbTypeLabel}</span>
                          <Text type="secondary">{key}</Text>
                        </Space>
                        <DeleteOutlined
                          onClick={(e) => handleRemoveRecentConnection(e, conn)}
                          style={{ color: '#ff4d4f' }}
                        />
                      </div>
                    </Select.Option>
                  )
                })}
              </Select>
              <Divider style={{ margin: '8px 0' }}>or enter manually</Divider>
            </>
          )}

          <Form
            form={form}
            name="login"
            onFinish={handleSubmit}
            autoComplete="off"
            layout="vertical"
            initialValues={{ dbType: 'MYSQL', port: 3306 }}
          >
            <Form.Item
              name="dbType"
              label={<Space><DatabaseOutlined /> Database Type</Space>}
              rules={[{ required: true, message: 'Please select a database type' }]}
            >
              <Select
                onChange={handleDbTypeChange}
              >
                <Select.Option value="MYSQL">
                  <Space>
                    <MySQLIcon />
                    MySQL
                  </Space>
                </Select.Option>
                <Select.Option value="ORACLE">
                  <Space>
                    <OracleIcon />
                    Oracle
                  </Space>
                </Select.Option>
                <Select.Option value="POSTGRESQL">
                  <Space>
                    <PostgreSQLIcon />
                    PostgreSQL
                  </Space>
                </Select.Option>
              </Select>
            </Form.Item>

            <Row gutter={12}>
              <Col span={16}>
                <Form.Item
                  name="host"
                  label={<Space><GlobalOutlined /> Host</Space>}
                  rules={[{ required: true, message: 'Please enter host' }]}
                >
                  <Input
                    placeholder="localhost or IP"
                  />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  name="port"
                  label={<Space><NumberOutlined /> Port</Space>}
                  rules={[{ required: true, message: 'Required' }]}
                >
                  <InputNumber
                    style={{ width: '100%' }}
                    min={1}
                    max={65535}
                    placeholder="3306"
                  />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item
              name="database"
              label={<Space><DatabaseOutlined />{getDatabaseFieldConfig().label}</Space>}
              tooltip={getDatabaseFieldConfig().tooltip}
              rules={getDatabaseFieldConfig().required ? [{ required: true, message: `Please enter the ${getDatabaseFieldConfig().label.toLowerCase()}` }] : []}
            >
              <Input
                placeholder={getDatabaseFieldConfig().placeholder}
              />
            </Form.Item>

            <Row gutter={12}>
              <Col span={12}>
                <Form.Item
                  name="username"
                  label={<Space><UserOutlined /> Username</Space>}
                  rules={[{ required: true, message: 'Required' }]}
                >
                  <Input
                    placeholder="Username"
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="password"
                  label={<Space><LockOutlined /> Password</Space>}
                  rules={[{ required: true, message: 'Required' }]}
                >
                  <Input.Password
                    placeholder="Password"
                  />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item style={{ marginBottom: 0, marginTop: 8 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                size="large"
              >
                Connect
              </Button>
            </Form.Item>
          </Form>

          <Text
            type="secondary"
            style={{ textAlign: 'center', display: 'block', fontSize: 12 }}
          >
            Enter your database credentials to connect
          </Text>
        </Space>
      </Card>
    </div>
  )
}

export default Login