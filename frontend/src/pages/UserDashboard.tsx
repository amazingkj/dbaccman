import { useState, useEffect } from 'react'
import { Typography, Card, Spin, Alert, Descriptions, Space, Tag, Result } from 'antd'
import {
  ClockCircleOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  ExclamationCircleOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../store/authStore'
import { authApi } from '../api/auth'
import { formatToLocalTime } from '../utils/dateUtils'
import type { PasswordExpiryInfo } from '../types'

const { Title, Text } = Typography

function UserDashboard() {
  const { user } = useAuthStore()
  const [loading, setLoading] = useState(true)
  const [expiryInfo, setExpiryInfo] = useState<PasswordExpiryInfo | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchExpiryInfo = async () => {
      try {
        const response = await authApi.getPasswordExpiry()
        setExpiryInfo(response.data)
      } catch {
        setError('Failed to load password expiry information')
      } finally {
        setLoading(false)
      }
    }

    fetchExpiryInfo()
  }, [])

  const getExpiryStatus = () => {
    if (!expiryInfo) return null

    if (expiryInfo.isExpired) {
      return {
        color: 'red',
        icon: <ExclamationCircleOutlined />,
        text: 'Expired',
        description: 'Your password has expired. Please contact an administrator.',
      }
    }

    if (expiryInfo.daysUntilExpiry === null) {
      return {
        color: 'green',
        icon: <CheckCircleOutlined />,
        text: 'No Expiration',
        description: 'Your password does not have an expiration date.',
      }
    }

    if (expiryInfo.daysUntilExpiry <= 7) {
      return {
        color: 'red',
        icon: <WarningOutlined />,
        text: `${expiryInfo.daysUntilExpiry} days remaining`,
        description: 'Your password will expire soon. Please contact an administrator to reset it.',
      }
    }

    if (expiryInfo.daysUntilExpiry <= 30) {
      return {
        color: 'orange',
        icon: <WarningOutlined />,
        text: `${expiryInfo.daysUntilExpiry} days remaining`,
        description: 'Your password will expire within 30 days.',
      }
    }

    return {
      color: 'green',
      icon: <CheckCircleOutlined />,
      text: `${expiryInfo.daysUntilExpiry} days remaining`,
      description: 'Your password is valid.',
    }
  }

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px 0' }}>
        <Spin size="large" />
        <p style={{ marginTop: 16 }}>Loading...</p>
      </div>
    )
  }

  if (error) {
    return (
      <Result
        status="error"
        title="Error"
        subTitle={error}
      />
    )
  }

  const status = getExpiryStatus()

  return (
    <div style={{ maxWidth: 600, margin: '0 auto', padding: '40px 20px' }}>
      <div style={{ textAlign: 'center', marginBottom: 32 }}>
        <Title level={2}>
          <UserOutlined style={{ marginRight: 8 }} />
          My Account
        </Title>
        <Text type="secondary">
          Connected as {user?.username}@{user?.host}:{user?.port}
        </Text>
      </div>

      <Card>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div style={{ textAlign: 'center' }}>
            <ClockCircleOutlined style={{ fontSize: 64, color: '#1890ff', marginBottom: 16 }} />
            <Title level={3}>Password Status</Title>
          </div>

          {status && (
            <Alert
              message={
                <Space>
                  {status.icon}
                  <span>{status.text}</span>
                </Space>
              }
              description={status.description}
              type={
                status.color === 'green'
                  ? 'success'
                  : status.color === 'orange'
                  ? 'warning'
                  : 'error'
              }
              showIcon={false}
            />
          )}

          {expiryInfo && (
            <Descriptions column={1} bordered>
              <Descriptions.Item label="Username">
                {expiryInfo.username}
              </Descriptions.Item>
              <Descriptions.Item label="Host">
                {expiryInfo.host}
              </Descriptions.Item>
              <Descriptions.Item label="Password Last Changed">
                {formatToLocalTime(expiryInfo.passwordLastChanged)}
              </Descriptions.Item>
              <Descriptions.Item label="Days Until Expiry">
                {expiryInfo.daysUntilExpiry !== null ? (
                  <Tag color={status?.color}>{expiryInfo.daysUntilExpiry} days</Tag>
                ) : (
                  <Tag color="green">No Expiration</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={expiryInfo.isExpired ? 'red' : 'green'}>
                  {expiryInfo.isExpired ? 'Expired' : 'Active'}
                </Tag>
              </Descriptions.Item>
            </Descriptions>
          )}

          <Text type="secondary" style={{ display: 'block', textAlign: 'center' }}>
            To change your password, please contact a database administrator.
          </Text>
        </Space>
      </Card>
    </div>
  )
}

export default UserDashboard
