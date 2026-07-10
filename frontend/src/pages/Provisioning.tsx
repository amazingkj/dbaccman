import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Typography,
  Steps,
  Form,
  Input,
  InputNumber,
  Button,
  Space,
  Card,
  Select,
  Switch,
  Alert,
  message,
  Divider,
  Tag,
  Tooltip,
  Result,
} from 'antd'
import {
  RocketOutlined,
  ThunderboltOutlined,
  CopyOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  MinusCircleOutlined,
  UserOutlined,
  HddOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { provisioningApi } from '../api/provisioning'
import { useAuthStore } from '../store/authStore'
import type { ProvisionRequest, ProvisionPlan, ProvisionResult } from '../types'

const { Title, Text } = Typography
const { Option } = Select

const SIZE_PATTERN = /^\d+[KMGT]?$/i
const NAME_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_$#]*$/

const ORACLE_COMMON_ROLES = ['CONNECT', 'RESOURCE', 'SELECT_CATALOG_ROLE', 'EXECUTE_CATALOG_ROLE']

interface FormValues {
  serviceName: string
  username: string
  password: string
  // Oracle
  dataDir: string
  dataTablespace: string
  dataSize: string
  createIndexTs: boolean
  indexTablespace: string
  indexSize: string
  tempTablespace: string
  tempSize: string
  autoExtend: boolean
  roles: string[]
  quota: string
  profile?: string
  // PostgreSQL
  pgTablespace: string
  pgLocation: string
  pgCreateDb: boolean
  pgCreateRole: boolean
  pgReplication: boolean
  pgCreateDatabase: boolean
  pgDatabaseName: string
  pgEncoding: string
  pgConnectionLimit: number
  // MySQL
  myHost: string
  myCreateDatabase: boolean
  myDatabaseName: string
  myCharset: string
  myGrantAll: boolean
}

function generatePassword(): string {
  const letters = 'abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ'
  const digits = '23456789'
  const specials = '!@#$%^&*'
  const all = letters + digits + specials
  const pick = (chars: string) => chars[Math.floor(Math.random() * chars.length)]
  // Guarantee at least one letter, digit, and special character
  let pw = pick(letters) + pick(digits) + pick(specials)
  for (let i = 0; i < 13; i++) pw += pick(all)
  return pw
    .split('')
    .sort(() => Math.random() - 0.5)
    .join('')
}

function Provisioning() {
  const navigate = useNavigate()
  const { user } = useAuthStore()
  const dbType = user?.dbType?.toUpperCase()
  const isOracle = dbType === 'ORACLE'
  const isPostgres = dbType === 'POSTGRESQL'
  const isMySql = dbType === 'MYSQL'

  const [form] = Form.useForm<FormValues>()
  const [currentStep, setCurrentStep] = useState(0)
  const [plan, setPlan] = useState<ProvisionPlan | null>(null)
  const [result, setResult] = useState<ProvisionResult | null>(null)
  const [request, setRequest] = useState<ProvisionRequest | null>(null)
  const [loading, setLoading] = useState(false)
  const [executing, setExecuting] = useState(false)
  const createIndexTs = Form.useWatch('createIndexTs', form)
  const pgCreateDatabase = Form.useWatch('pgCreateDatabase', form)
  const myCreateDatabase = Form.useWatch('myCreateDatabase', form)

  // Auto-fill names following the operational naming convention
  const handleServiceNameChange = (value: string) => {
    const name = value.trim()
    if (!name || !NAME_PATTERN.test(name)) return
    form.setFieldsValue({
      username: name,
      dataTablespace: `${name}_tablespace`,
      indexTablespace: `${name}_indexspace`,
      tempTablespace: `${name}_tempspace`,
      pgTablespace: `${name}_tablespace`,
      pgLocation: `/postgre/${name}`,
      pgDatabaseName: name,
      myDatabaseName: name,
    })
  }

  const buildRequest = (values: FormValues): ProvisionRequest => {
    const base = { username: values.username.trim(), password: values.password }
    if (isOracle) {
      const dir = values.dataDir.replace(/\/+$/, '')
      return {
        ...base,
        oracle: {
          dataTablespace: values.dataTablespace,
          dataFilePath: `${dir}/${values.dataTablespace.toLowerCase()}.dbf`,
          dataSize: values.dataSize.toUpperCase(),
          indexTablespace: values.createIndexTs ? values.indexTablespace : null,
          indexFilePath: values.createIndexTs
            ? `${dir}/${values.indexTablespace.toLowerCase()}.dbf`
            : null,
          indexSize: values.indexSize.toUpperCase(),
          tempTablespace: values.tempTablespace,
          tempFilePath: `${dir}/temp/${values.tempTablespace.toLowerCase()}.dbf`,
          tempSize: values.tempSize.toUpperCase(),
          autoExtend: values.autoExtend,
          roles: values.roles,
          quota: values.quota,
          profile: values.profile?.trim() || null,
        },
      }
    }
    if (isPostgres) {
      return {
        ...base,
        postgres: {
          tablespace: values.pgTablespace,
          location: values.pgLocation,
          createDb: values.pgCreateDb,
          createRole: values.pgCreateRole,
          replication: values.pgReplication,
          databaseName: values.pgCreateDatabase ? values.pgDatabaseName : null,
          encoding: values.pgEncoding,
          connectionLimit: values.pgConnectionLimit,
        },
      }
    }
    return {
      ...base,
      mysql: {
        host: values.myHost,
        databaseName: values.myCreateDatabase ? values.myDatabaseName : null,
        charset: values.myCharset,
        grantAllOnDatabase: values.myGrantAll,
      },
    }
  }

  const handlePreview = async () => {
    try {
      const values = await form.validateFields()
      setLoading(true)
      const req = buildRequest(values)
      const res = await provisioningApi.preview(req)
      setRequest(req)
      setPlan(res.data)
      setCurrentStep(1)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } }; errorFields?: unknown }
      if (err.errorFields) return // form validation error - already shown inline
      message.error(err.response?.data?.error || 'Failed to build provisioning plan')
    } finally {
      setLoading(false)
    }
  }

  const handleExecute = async () => {
    if (!request) return
    setExecuting(true)
    try {
      const res = await provisioningApi.execute(request)
      setResult(res.data)
      setCurrentStep(2)
      if (res.data.success) {
        message.success('Provisioning completed successfully')
      } else {
        message.warning('Provisioning stopped - check the failed step below')
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { error?: string } } }
      message.error(err.response?.data?.error || 'Provisioning failed')
    } finally {
      setExecuting(false)
    }
  }

  const handleReset = () => {
    setCurrentStep(0)
    setPlan(null)
    setResult(null)
    setRequest(null)
  }

  const copyAllSql = () => {
    if (!plan) return
    navigator.clipboard.writeText(plan.steps.map((s) => `-- ${s.title}\n${s.sql};`).join('\n\n'))
    message.success('SQL copied to clipboard')
  }

  const statusIcon = (status: string) => {
    switch (status) {
      case 'SUCCESS':
        return <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 18 }} />
      case 'FAILED':
        return <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />
      default:
        return <MinusCircleOutlined style={{ color: '#bfbfbf', fontSize: 18 }} />
    }
  }

  const sizeRule = [
    { required: true, message: 'Required' },
    { pattern: SIZE_PATTERN, message: 'e.g., 8G, 256M' },
  ]
  const nameRule = (label: string) => [
    { required: true, message: `Please enter ${label}` },
    { pattern: NAME_PATTERN, message: 'Letters, digits, and underscores only' },
  ]

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Title level={2} style={{ margin: 0, marginBottom: 4 }}>
          <RocketOutlined style={{ marginRight: 12 }} />
          Provisioning
        </Title>
        <Text type="secondary">
          One-stop service account setup - tablespaces, user, grants, and quotas in a single guided flow
        </Text>
      </div>

      <Card>
        <Steps
          current={currentStep}
          items={[
            { title: 'Configure', icon: <UserOutlined /> },
            { title: 'Review SQL', icon: <HddOutlined /> },
            { title: 'Result', icon: <ThunderboltOutlined /> },
          ]}
          style={{ marginBottom: 32, maxWidth: 700 }}
        />

        {currentStep === 0 && (
          <Form
            form={form}
            layout="vertical"
            style={{ maxWidth: 720 }}
            initialValues={{
              dataDir: '/u02/oradata',
              dataSize: '8G',
              createIndexTs: true,
              indexSize: '1G',
              tempSize: '256M',
              autoExtend: false,
              roles: ['CONNECT', 'RESOURCE'],
              quota: 'UNLIMITED',
              pgCreateDb: true,
              pgCreateRole: false,
              pgReplication: false,
              pgCreateDatabase: true,
              pgEncoding: 'UTF8',
              pgConnectionLimit: -1,
              myHost: '%',
              myCreateDatabase: true,
              myCharset: 'utf8mb4',
              myGrantAll: true,
            }}
          >
            <Divider orientation="left" plain>
              Service
            </Divider>
            <Form.Item
              name="serviceName"
              label="Service Name"
              tooltip="Base name used to derive the username, tablespace names, and database name (e.g., apim_demo)"
              rules={nameRule('service name')}
            >
              <Input
                placeholder="e.g., apim_demo"
                onChange={(e) => handleServiceNameChange(e.target.value)}
              />
            </Form.Item>
            <Space size="large" align="start" wrap>
              <Form.Item name="username" label="Username" rules={nameRule('username')} style={{ minWidth: 280 }}>
                <Input placeholder="Account username" />
              </Form.Item>
              <Form.Item
                name="password"
                label="Password"
                rules={[
                  { required: true, message: 'Please enter password' },
                  { min: 8, message: 'At least 8 characters' },
                ]}
                style={{ minWidth: 280 }}
              >
                <Input.Password
                  placeholder="Min 8 chars with letter, digit, special"
                  addonAfter={
                    <Tooltip title="Generate strong password">
                      <ReloadOutlined
                        onClick={() => form.setFieldsValue({ password: generatePassword() })}
                        style={{ cursor: 'pointer' }}
                      />
                    </Tooltip>
                  }
                />
              </Form.Item>
            </Space>

            {isOracle && (
              <>
                <Divider orientation="left" plain>
                  Tablespaces
                </Divider>
                <Form.Item
                  name="dataDir"
                  label="Datafile Directory"
                  tooltip="Directory on the Oracle server where datafiles are created. Temp files go under {dir}/temp/"
                  rules={[{ required: true, message: 'Please enter datafile directory' }]}
                >
                  <Input placeholder="/u02/oradata/apim" />
                </Form.Item>
                <Space size="large" align="start" wrap>
                  <Form.Item name="dataTablespace" label="Data Tablespace" rules={nameRule('data tablespace')} style={{ minWidth: 280 }}>
                    <Input />
                  </Form.Item>
                  <Form.Item name="dataSize" label="Size" rules={sizeRule} style={{ width: 100 }}>
                    <Input />
                  </Form.Item>
                  <Form.Item name="autoExtend" label="Autoextend" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Space>
                <Space size="large" align="start" wrap>
                  <Form.Item name="createIndexTs" label="Separate Index Tablespace" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  {createIndexTs && (
                    <>
                      <Form.Item name="indexTablespace" label="Index Tablespace" rules={nameRule('index tablespace')} style={{ minWidth: 280 }}>
                        <Input />
                      </Form.Item>
                      <Form.Item name="indexSize" label="Size" rules={sizeRule} style={{ width: 100 }}>
                        <Input />
                      </Form.Item>
                    </>
                  )}
                </Space>
                <Space size="large" align="start" wrap>
                  <Form.Item name="tempTablespace" label="Temp Tablespace" rules={nameRule('temp tablespace')} style={{ minWidth: 280 }}>
                    <Input />
                  </Form.Item>
                  <Form.Item name="tempSize" label="Size" rules={sizeRule} style={{ width: 100 }}>
                    <Input />
                  </Form.Item>
                </Space>

                <Divider orientation="left" plain>
                  Privileges
                </Divider>
                <Space size="large" align="start" wrap>
                  <Form.Item
                    name="roles"
                    label="Roles"
                    tooltip="Roles granted to the new user. Type to add a custom role (e.g., apimrole)"
                    style={{ minWidth: 280 }}
                  >
                    <Select mode="tags" placeholder="Select or type roles">
                      {ORACLE_COMMON_ROLES.map((r) => (
                        <Option key={r} value={r}>
                          {r}
                        </Option>
                      ))}
                    </Select>
                  </Form.Item>
                  <Form.Item name="quota" label="Tablespace Quota" style={{ width: 160 }}>
                    <Select>
                      <Option value="UNLIMITED">UNLIMITED</Option>
                      <Option value="1G">1G</Option>
                      <Option value="10G">10G</Option>
                      <Option value="100G">100G</Option>
                    </Select>
                  </Form.Item>
                  <Form.Item
                    name="profile"
                    label="Profile (optional)"
                    tooltip="Existing Oracle profile to assign, e.g., defaultprofile"
                    style={{ minWidth: 200 }}
                  >
                    <Input placeholder="Leave empty to skip" />
                  </Form.Item>
                </Space>
              </>
            )}

            {isPostgres && (
              <>
                <Divider orientation="left" plain>
                  Tablespace & Database
                </Divider>
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 16 }}
                  message="The tablespace directory must already exist on the server and be owned by the postgres OS user."
                  description={
                    <code>sudo mkdir -p /postgre/myservice && sudo chown postgres:postgres /postgre/myservice</code>
                  }
                />
                <Space size="large" align="start" wrap>
                  <Form.Item name="pgTablespace" label="Tablespace" rules={nameRule('tablespace')} style={{ minWidth: 280 }}>
                    <Input />
                  </Form.Item>
                  <Form.Item
                    name="pgLocation"
                    label="Location"
                    rules={[{ required: true, message: 'Please enter directory path' }]}
                    style={{ minWidth: 320 }}
                  >
                    <Input placeholder="/postgre/apim/apim_demo" />
                  </Form.Item>
                </Space>
                <Space size="large" align="start" wrap>
                  <Form.Item name="pgCreateDatabase" label="Create Database" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  {pgCreateDatabase && (
                    <>
                      <Form.Item name="pgDatabaseName" label="Database Name" rules={nameRule('database name')} style={{ minWidth: 240 }}>
                        <Input />
                      </Form.Item>
                      <Form.Item name="pgEncoding" label="Encoding" style={{ width: 140 }}>
                        <Select>
                          <Option value="UTF8">UTF8</Option>
                          <Option value="EUC_KR">EUC_KR</Option>
                          <Option value="LATIN1">LATIN1</Option>
                        </Select>
                      </Form.Item>
                      <Form.Item
                        name="pgConnectionLimit"
                        label="Connection Limit"
                        tooltip="-1 means unlimited"
                        style={{ width: 140 }}
                      >
                        <InputNumber min={-1} max={10000} style={{ width: '100%' }} />
                      </Form.Item>
                    </>
                  )}
                </Space>

                <Divider orientation="left" plain>
                  Role Options
                </Divider>
                <Space size="large" wrap>
                  <Form.Item name="pgCreateDb" label="CREATEDB" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="pgCreateRole" label="CREATEROLE" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item name="pgReplication" label="REPLICATION" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Space>
              </>
            )}

            {isMySql && (
              <>
                <Divider orientation="left" plain>
                  Database
                </Divider>
                <Space size="large" align="start" wrap>
                  <Form.Item
                    name="myHost"
                    label="Host"
                    tooltip="Host the user can connect from (% = any host)"
                    rules={[{ required: true, message: 'Please enter host' }]}
                    style={{ width: 160 }}
                  >
                    <Input />
                  </Form.Item>
                  <Form.Item name="myCreateDatabase" label="Create Database" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  {myCreateDatabase && (
                    <>
                      <Form.Item name="myDatabaseName" label="Database Name" rules={nameRule('database name')} style={{ minWidth: 240 }}>
                        <Input />
                      </Form.Item>
                      <Form.Item name="myCharset" label="Charset" style={{ width: 140 }}>
                        <Select>
                          <Option value="utf8mb4">utf8mb4</Option>
                          <Option value="utf8">utf8</Option>
                          <Option value="latin1">latin1</Option>
                        </Select>
                      </Form.Item>
                      <Form.Item name="myGrantAll" label="Grant ALL on DB" valuePropName="checked">
                        <Switch />
                      </Form.Item>
                    </>
                  )}
                </Space>
              </>
            )}

            <Divider />
            <Button type="primary" size="large" icon={<HddOutlined />} loading={loading} onClick={handlePreview}>
              Review SQL
            </Button>
          </Form>
        )}

        {currentStep === 1 && plan && (
          <div style={{ maxWidth: 900 }}>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message={`${plan.steps.length} statements will be executed in order on ${plan.dbType}. If a step fails, execution stops and the remaining steps are skipped.`}
            />
            {plan.steps.map((step) => (
              <Card key={step.order} size="small" style={{ marginBottom: 8 }}>
                <Space direction="vertical" style={{ width: '100%' }} size={4}>
                  <Space>
                    <Tag color="blue">{step.order}</Tag>
                    <Text strong>{step.title}</Text>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {step.description}
                    </Text>
                  </Space>
                  <pre
                    style={{
                      margin: 0,
                      padding: '8px 12px',
                      background: '#f6f8fa',
                      borderRadius: 6,
                      fontSize: 12,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                    }}
                  >
                    {step.sql}
                  </pre>
                </Space>
              </Card>
            ))}
            <Space style={{ marginTop: 16 }}>
              <Button onClick={() => setCurrentStep(0)}>Back</Button>
              <Button icon={<CopyOutlined />} onClick={copyAllSql}>
                Copy SQL
              </Button>
              <Button type="primary" icon={<ThunderboltOutlined />} loading={executing} onClick={handleExecute}>
                Execute
              </Button>
            </Space>
          </div>
        )}

        {currentStep === 2 && result && (
          <div style={{ maxWidth: 900 }}>
            <Result
              status={result.success ? 'success' : 'error'}
              title={result.success ? 'Provisioning Completed' : 'Provisioning Stopped'}
              subTitle={
                result.success
                  ? 'All steps executed successfully. The new account is ready to use.'
                  : 'A step failed - completed steps were NOT rolled back. Fix the cause and re-run the remaining steps, or clean up manually.'
              }
              style={{ padding: '16px 0' }}
            />
            {result.steps.map((step) => (
              <Card key={step.order} size="small" style={{ marginBottom: 8 }}>
                <Space direction="vertical" style={{ width: '100%' }} size={4}>
                  <Space>
                    {statusIcon(step.status)}
                    <Tag color="blue">{step.order}</Tag>
                    <Text strong>{step.title}</Text>
                    {step.status === 'SUCCESS' && (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {step.durationMs} ms
                      </Text>
                    )}
                    {step.status === 'SKIPPED' && <Tag>Skipped</Tag>}
                  </Space>
                  {step.status === 'FAILED' && step.error && (
                    <Alert type="error" message={step.error} showIcon style={{ marginTop: 4 }} />
                  )}
                  <pre
                    style={{
                      margin: 0,
                      padding: '8px 12px',
                      background: '#f6f8fa',
                      borderRadius: 6,
                      fontSize: 12,
                      whiteSpace: 'pre-wrap',
                      wordBreak: 'break-all',
                      opacity: step.status === 'SKIPPED' ? 0.5 : 1,
                    }}
                  >
                    {step.sql}
                  </pre>
                </Space>
              </Card>
            ))}
            <Space style={{ marginTop: 16 }}>
              <Button icon={<RocketOutlined />} onClick={handleReset}>
                New Provisioning
              </Button>
              <Button type="primary" icon={<UserOutlined />} onClick={() => navigate('/accounts')}>
                Go to Accounts
              </Button>
            </Space>
          </div>
        )}
      </Card>
    </div>
  )
}

export default Provisioning
