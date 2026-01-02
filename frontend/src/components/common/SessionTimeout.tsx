import { useEffect, useState, useCallback } from 'react'
import { Modal, Typography, Progress } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'
import { useAuthStore, SESSION_TIMEOUT_MS, SESSION_WARNING_MS } from '../../store/authStore'
import { useAuth } from '../../hooks/useAuth'

const { Text } = Typography

function SessionTimeout() {
  const { isAuthenticated, lastActivity, updateLastActivity } = useAuthStore()
  const { logout } = useAuth()
  const [showWarning, setShowWarning] = useState(false)
  const [remainingTime, setRemainingTime] = useState(0)

  // Track user activity
  const handleActivity = useCallback(() => {
    if (isAuthenticated && !showWarning) {
      updateLastActivity()
    }
  }, [isAuthenticated, showWarning, updateLastActivity])

  // Add activity listeners
  useEffect(() => {
    if (!isAuthenticated) return

    const events = ['mousedown', 'keydown', 'scroll', 'touchstart', 'click']

    events.forEach(event => {
      window.addEventListener(event, handleActivity, { passive: true })
    })

    return () => {
      events.forEach(event => {
        window.removeEventListener(event, handleActivity)
      })
    }
  }, [isAuthenticated, handleActivity])

  // Check session timeout
  useEffect(() => {
    if (!isAuthenticated) {
      setShowWarning(false)
      return
    }

    const checkTimeout = () => {
      const now = Date.now()
      const elapsed = now - lastActivity
      const remaining = SESSION_TIMEOUT_MS - elapsed

      if (remaining <= 0) {
        // Session expired
        setShowWarning(false)
        logout()
      } else if (remaining <= SESSION_WARNING_MS) {
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
  }, [isAuthenticated, lastActivity, logout])

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

  const warningProgress = Math.round((remainingTime / (SESSION_WARNING_MS / 1000)) * 100)

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
