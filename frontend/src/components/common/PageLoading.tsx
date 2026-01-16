import { Spin, Skeleton, Row, Col, Card } from 'antd'
import { LoadingOutlined } from '@ant-design/icons'

interface PageLoadingProps {
  message?: string
  type?: 'spinner' | 'skeleton' | 'cards'
}

/**
 * Reusable page loading component with different loading styles
 */
export function PageLoading({ message = 'Loading...', type = 'spinner' }: PageLoadingProps) {
  if (type === 'skeleton') {
    return (
      <div style={{ padding: '24px 0' }}>
        <Skeleton active paragraph={{ rows: 4 }} />
        <Skeleton active paragraph={{ rows: 4 }} style={{ marginTop: 24 }} />
      </div>
    )
  }

  if (type === 'cards') {
    return (
      <div>
        {/* Skeleton cards row */}
        <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
          {[1, 2, 3, 4].map((i) => (
            <Col xs={24} sm={12} lg={6} key={i}>
              <Card style={{ height: 90 }}>
                <Skeleton.Avatar active size="large" style={{ marginRight: 16 }} />
                <Skeleton.Input active style={{ width: 100 }} />
              </Card>
            </Col>
          ))}
        </Row>
        {/* Skeleton table */}
        <Card>
          <Skeleton active paragraph={{ rows: 8 }} />
        </Card>
      </div>
    )
  }

  // Default spinner
  return (
    <div
      style={{
        textAlign: 'center',
        padding: '100px 0',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 16,
      }}
    >
      <Spin
        indicator={<LoadingOutlined style={{ fontSize: 36 }} spin />}
        size="large"
      />
      <p style={{ color: '#8c8c8c', margin: 0 }}>{message}</p>
    </div>
  )
}

/**
 * Inline loading indicator for buttons and small areas
 */
export function InlineLoading({ text = 'Loading...' }: { text?: string }) {
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <LoadingOutlined spin />
      {text}
    </span>
  )
}

export default PageLoading
