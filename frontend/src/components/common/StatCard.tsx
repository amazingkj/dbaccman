import { memo, ReactNode } from 'react'
import { Card } from 'antd'

export interface StatCardStyle {
  color: string
  bgColor: string
  icon: ReactNode
}

export interface StatCardProps {
  title: string
  value: number
  style: StatCardStyle
  onClick?: () => void
  suffix?: string
  description?: string
  hoverable?: boolean
  compact?: boolean
}

/**
 * Reusable StatCard component for displaying statistics with icons.
 * Used across Dashboard, Accounts, Sessions, and Permissions pages.
 */
function StatCardComponent({
  title,
  value,
  style,
  onClick,
  suffix,
  description,
  hoverable = true,
  compact = false,
}: StatCardProps) {
  const iconSize = compact ? 48 : 56
  const fontSize = compact ? 22 : 24
  const padding = compact ? '16px 20px' : '20px'
  const height = compact ? 90 : 110
  const gap = compact ? 14 : 16

  return (
    <Card
      hoverable={hoverable && !!onClick}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-label={`${title}: ${value.toLocaleString()}${suffix || ''}`}
      onKeyDown={(e) => {
        if (onClick && (e.key === 'Enter' || e.key === ' ')) {
          e.preventDefault()
          onClick()
        }
      }}
      style={{
        background: '#fff',
        borderRadius: 12,
        border: 'none',
        cursor: onClick ? 'pointer' : 'default',
        overflow: 'hidden',
        height: '100%',
        boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
      }}
      styles={{
        body: {
          padding,
          height,
          display: 'flex',
          alignItems: 'center',
          gap,
        },
      }}
    >
      {/* Icon Container */}
      <div
        style={{
          width: iconSize,
          height: iconSize,
          borderRadius: compact ? 10 : 12,
          background: style.bgColor,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: compact ? 22 : 24,
          color: style.color,
          flexShrink: 0,
        }}
      >
        {style.icon}
      </div>

      {/* Content */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: compact ? 12 : 13,
            color: '#5a6a85',
            fontWeight: 500,
            marginBottom: compact ? 2 : 4,
          }}
        >
          {title}
        </div>
        <div
          style={{
            fontSize,
            fontWeight: 600,
            color: '#2a3547',
            lineHeight: 1.2,
          }}
        >
          {value.toLocaleString()}
          {suffix && (
            <span style={{ fontSize: 14, marginLeft: 4, color: '#5a6a85' }}>
              {suffix}
            </span>
          )}
        </div>
        {description && (
          <div
            style={{
              fontSize: 12,
              color: style.color,
              marginTop: 4,
              fontWeight: 500,
            }}
          >
            {description}
          </div>
        )}
      </div>
    </Card>
  )
}

// Memoized export for performance
export const StatCard = memo(StatCardComponent)

// Pre-defined style constants for common stat card themes
export const STAT_CARD_STYLES = {
  primary: {
    color: '#5d87ff',
    bgColor: 'rgba(93, 135, 255, 0.1)',
  },
  success: {
    color: '#13deb9',
    bgColor: 'rgba(19, 222, 185, 0.1)',
  },
  warning: {
    color: '#ffae1f',
    bgColor: 'rgba(255, 174, 31, 0.1)',
  },
  danger: {
    color: '#fa896b',
    bgColor: 'rgba(250, 137, 107, 0.1)',
  },
  info: {
    color: '#49beff',
    bgColor: 'rgba(73, 190, 255, 0.1)',
  },
  purple: {
    color: '#845ef7',
    bgColor: 'rgba(132, 94, 247, 0.1)',
  },
  red: {
    color: '#ff6b6b',
    bgColor: 'rgba(255, 107, 107, 0.1)',
  },
  muted: {
    color: '#7c8fac',
    bgColor: 'rgba(124, 143, 172, 0.1)',
  },
}

export default StatCard
