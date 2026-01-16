import { useEffect, useState, useMemo } from 'react'
import { Modal, Typography, Progress } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'
import { useAuthStore } from '../../store/authStore'
import { useSettingsStore, getSessionTimeoutMs, getSessionWarningMs } from '../../store/settingsStore'
import { useAuth } from '../../hooks/useAuth'

const { Text } = Typography

function SessionTimeout() {
  const { isAuthenticated, lastActivity, updateLastActivity } = useAuthStore()
  const { sessionTimeoutMinutes, sessionWarningMinutes, autoLogoutOnTimeout } = useSettingsStore()
  const { logout } = useAuth()
  const [showWarning, setShowWarning] = useState(false)
  const [remainingTime, setRemainingTime] = useState(0)

  // Calculate timeout values from settings
  const sessionTimeoutMs = useMemo(
    () => getSessionTimeoutMs(sessionTimeoutMinutes),
    [sessionTimeoutMinutes]
  )
  const sessionWarningMs = useMemo(
    () => getSessionWarningMs(sessionWarningMinutes),
    [sessionWarningMinutes]
  )

  // Check session timeout
  useEffect(() => {
    if (!isAuthenticated || !autoLogoutOnTimeout) {
      setShowWarning(false)
      return
    }

    const checkTimeout = () => {
      const now = Date.now()
      const elapsed = now - lastActivity
      const remaining = sessionTimeoutMs - elapsed

      if (remaining <= 0) {
        // Session expired
        setShowWarning(false)
        logout()
      } else if (remaining <= sessionWarningMs) {
        // Show warning
        setShowWarning(true)
        setRemainingTime(Math.ceil(remaining / 1000))
      } else {
        setShowWarning(false)
      }
    }

    // Check every second
    const interval = setInterval(checkTimeout, 1000)
    checkTimeout()

    return () => clearInterval(interval)
  }, [isAuthenticated, lastActivity, logout, sessionTimeoutMs, sessionWarningMs, autoLogoutOnTimeout])

  // Continue session
  const handleContinue = () => {
    updateLastActivity()
    setShowWarning(false)
  }

  // Logout now
  const handleLogoutNow = () => {
    setShowWarning(false)
    logout()
  }

  // Format remaining time
  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const warningProgress = Math.round((remainingTime / (sessionWarningMs / 1000)) * 100)

  return (
    <Modal
      title={
        <span>
          <ClockCircleOutlined style={{ marginRight: 8, color: '#faad14' }} />
          Session Timeout Warning
        </span>
      }
      open={showWarning}
      onOk={handleContinue}
      onCancel={handleLogoutNow}
      okText="Continue Session"
      cancelText="Logout Now"
      closable={false}
      maskClosable={false}
      centered
    >
      <div style={{ textAlign: 'center', padding: '20px 0' }}>
        <Progress
          type="circle"
          percent={warningProgress}
          format={() => formatTime(remainingTime)}
          status={remainingTime < 60 ? 'exception' : 'active'}
          size={120}
        />
        <div style={{ marginTop: 20 }}>
          <Text>
            Your session will expire due to inactivity.
          </Text>
          <br />
          <Text type="secondary">
            Click "Continue Session" to stay logged in.
          </Text>
        </div>
      </div>
    </Modal>
  )
}

export default SessionTimeout
