import { Component, ErrorInfo, ReactNode } from 'react'
import { Result, Button, Typography, Card } from 'antd'
import { ReloadOutlined, BugOutlined } from '@ant-design/icons'

const { Paragraph, Text } = Typography

interface Props {
  children: ReactNode
  fallback?: ReactNode
}

interface State {
  hasError: boolean
  error: Error | null
  errorInfo: ErrorInfo | null
}

/**
 * Error Boundary component that catches JavaScript errors in child components.
 * Displays a fallback UI and provides error recovery options.
 */
class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props)
    this.state = { hasError: false, error: null, errorInfo: null }
  }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({ error, errorInfo })

    // Log error to console (can be extended to send to monitoring service)
    console.error('Error Boundary caught an error:', error, errorInfo)
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null, errorInfo: null })
    window.location.reload()
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null, errorInfo: null })
  }

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback
      }

      const isDevelopment = import.meta.env.DEV

      return (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '100vh',
            padding: 24,
            background: '#f5f5f5',
          }}
        >
          <Card style={{ maxWidth: 600, width: '100%', borderRadius: 12 }}>
            <Result
              status="error"
              icon={<BugOutlined style={{ color: '#ff4d4f' }} />}
              title="Something went wrong"
              subTitle="An unexpected error occurred. Please try refreshing the page."
              extra={[
                <Button
                  type="primary"
                  icon={<ReloadOutlined />}
                  onClick={this.handleReload}
                  key="reload"
                >
                  Refresh Page
                </Button>,
                <Button onClick={this.handleReset} key="retry">
                  Try Again
                </Button>,
              ]}
            >
              {isDevelopment && this.state.error && (
                <div style={{ textAlign: 'left', marginTop: 16 }}>
                  <Paragraph>
                    <Text strong style={{ color: '#cf1322' }}>
                      Error: {this.state.error.message}
                    </Text>
                  </Paragraph>
                  {this.state.error.stack && (
                    <Paragraph>
                      <pre
                        style={{
                          fontSize: 12,
                          background: '#fafafa',
                          padding: 12,
                          borderRadius: 4,
                          overflow: 'auto',
                          maxHeight: 200,
                          border: '1px solid #f0f0f0',
                        }}
                      >
                        {this.state.error.stack}
                      </pre>
                    </Paragraph>
                  )}
                  {this.state.errorInfo?.componentStack && (
                    <Paragraph>
                      <Text strong>Component Stack:</Text>
                      <pre
                        style={{
                          fontSize: 12,
                          background: '#fafafa',
                          padding: 12,
                          borderRadius: 4,
                          overflow: 'auto',
                          maxHeight: 150,
                          border: '1px solid #f0f0f0',
                        }}
                      >
                        {this.state.errorInfo.componentStack}
                      </pre>
                    </Paragraph>
                  )}
                </div>
              )}
            </Result>
          </Card>
        </div>
      )
    }

    return this.props.children
  }
}

export default ErrorBoundary
