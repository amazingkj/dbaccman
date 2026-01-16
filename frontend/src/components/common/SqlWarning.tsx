import React from 'react'
import { Alert } from 'antd'
import { WarningOutlined, CloseCircleOutlined } from '@ant-design/icons'
import type { SqlValidationResult } from '../../utils/sqlValidation'

interface SqlWarningProps {
  validation: SqlValidationResult | null
  style?: React.CSSProperties
}

/**
 * Displays SQL injection warnings based on validation results
 */
export const SqlWarning: React.FC<SqlWarningProps> = ({ validation, style }) => {
  if (!validation || validation.severity === 'none') {
    return null
  }

  const type = validation.severity === 'danger' ? 'error' : 'warning'
  const icon = validation.severity === 'danger' ? <CloseCircleOutlined /> : <WarningOutlined />

  const message = validation.severity === 'danger'
    ? 'Security Alert: Potentially dangerous input detected'
    : 'Warning: Special characters detected'

  const description = validation.warnings.join('. ')

  return (
    <Alert
      type={type}
      icon={icon}
      message={message}
      description={description || undefined}
      showIcon
      style={{ marginBottom: 16, ...style }}
    />
  )
}

export default SqlWarning
