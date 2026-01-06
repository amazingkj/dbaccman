import { useEffect, useState, useRef } from 'react'
import { ClockCircleOutlined } from '@ant-design/icons'
import { useAuthStore, SESSION_TIMEOUT_MS, SESSION_WARNING_MS } from '../../store/authStore'

function SessionCountdown() {
  const { isAuthenticated, lastActivity } = useAuthStore()
  const [remainingTime, setRemainingTime] = useState(SESSION_TIMEOUT_MS)
  const [tooltip, setTooltip] = useState({ visible: false, x: 0, y: 0 })
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!isAuthenticated) return

    const updateRemaining = () => {
      const now = Date.now()
      const elapsed = now - lastActivity
      const remaining = Math.max(0, SESSION_TIMEOUT_MS - elapsed)
      setRemainingTime(remaining)
    }

    updateRemaining()
    const interval = setInterval(updateRemaining, 1000)

    return () => clearInterval(interval)
  }, [isAuthenticated, lastActivity])

  if (!isAuthenticated) return null

  const seconds = Math.ceil(remainingTime / 1000)
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  const timeString = `${mins}:${secs.toString().padStart(2, '0')}`

  const isWarning = remainingTime <= SESSION_WARNING_MS
  const isCritical = remainingTime <= 60 * 1000

  const getColor = () => {
    if (isCritical) return '#ff4d4f'
    if (isWarning) return '#faad14'
    return '#8c8c8c'
  }

  const getBackgroundColor = () => {
    if (isCritical) return 'rgba(255, 77, 79, 0.08)'
    if (isWarning) return 'rgba(250, 173, 20, 0.08)'
    return 'transparent'
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    setTooltip({ visible: true, x: e.clientX, y: e.clientY })
  }

  const handleMouseLeave = () => {
    setTooltip({ visible: false, x: 0, y: 0 })
  }

  return (
    <>
      <div
        ref={containerRef}
        onMouseMove={handleMouseMove}
        onMouseLeave={handleMouseLeave}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 4,
          padding: '4px 10px',
          borderRadius: 16,
          background: getBackgroundColor(),
          transition: 'all 0.3s ease',
          cursor: 'default',
        }}
      >
        <ClockCircleOutlined
          style={{
            fontSize: 12,
            color: getColor(),
            animation: isCritical ? 'pulse 1s infinite' : 'none',
          }}
        />
        <span
          style={{
            fontSize: 12,
            fontWeight: 500,
            color: getColor(),
            minWidth: 36,
            textAlign: 'center',
          }}
        >
          {timeString}
        </span>
      </div>

      {/* Custom cursor-following tooltip */}
      {tooltip.visible && (
        <div
          style={{
            position: 'fixed',
            left: tooltip.x + 12,
            top: tooltip.y + 12,
            padding: '6px 10px',
            background: 'rgba(0, 0, 0, 0.75)',
            color: '#fff',
            fontSize: 12,
            borderRadius: 6,
            pointerEvents: 'none',
            zIndex: 9999,
            whiteSpace: 'nowrap',
            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.15)',
          }}
        >
          Session timeout (resets on activity)
        </div>
      )}

      <style>
        {`
          @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
          }
        `}
      </style>
    </>
  )
}

export default SessionCountdown
