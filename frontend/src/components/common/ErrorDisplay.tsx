import { memo } from 'react'
import { Alert, Button, Space } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'

export interface ErrorDisplayProps {
  error: Error | string | null | undefined
  title?: string
  onRetry?: () => void
  showIcon?: boolean
  type?: 'error' | 'warning' | 'info' | 'success'
  closable?: boolean
  onClose?: () => void
  style?: React.CSSProperties
}

/**
 * Reusable ErrorDisplay component for showing error messages.
 * Optionally provides a retry button for error recovery.
 */
function ErrorDisplayComponent({
  error,
  title = 'An error occurred',
  onRetry,
  showIcon = true,
  type = 'error',
  closable = false,
  onClose,
  style,
}: ErrorDisplayProps) {
  if (!error) return null

  const errorMessage =
    typeof error === 'string' ? error : error.message || 'Unknown error'

  return (
    <Alert
      message={title}
      description={
        <Space direction="vertical" style={{ width: '100%' }}>
          <span>{errorMessage}</span>
          {onRetry && (
            <Button
              type="link"
              size="small"
              icon={<ReloadOutlined />}
              onClick={onRetry}
              style={{ padding: 0 }}
            >
              Try again
            </Button>
          )}
        </Space>
      }
      type={type}
      showIcon={showIcon}
      closable={closable}
      onClose={onClose}
      style={{ marginBottom: 16, borderRadius: 8, ...style }}
    />
  )
}

export const ErrorDisplay = memo(ErrorDisplayComponent)

export default ErrorDisplay
