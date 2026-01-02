import { useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { Layout, theme, Tag, Space, Button, Tooltip } from 'antd'
import { DatabaseOutlined, LogoutOutlined } from '@ant-design/icons'
import { useAuthStore } from '../../store/authStore'
import { useAuth } from '../../hooks/useAuth'
import Sidebar from './Sidebar'
import SessionTimeout from '../common/SessionTimeout'

const { Header, Content, Footer } = Layout

function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const { user } = useAuthStore()
  const { logout } = useAuth()
  const navigate = useNavigate()
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <SessionTimeout />
      <Sidebar collapsed={collapsed} onCollapse={setCollapsed} />
      <Layout>
        <Header
          style={{
            padding: '0 24px',
            background: colorBgContainer,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <div
            style={{ fontSize: '18px', fontWeight: 'bold', cursor: 'pointer' }}
            onClick={() => navigate('/dashboard')}
          >
            D-BAM
          </div>
          {user && (
            <Space size="middle">
              <Space>
                <DatabaseOutlined />
                <span>{user.username}@{user.host}:{user.port}</span>
                <Tag color={user.role === 'admin' ? 'blue' : 'default'}>
                  {user.role.toUpperCase()}
                </Tag>
              </Space>
              <Tooltip title="Logout">
                <Button
                  type="text"
                  danger
                  icon={<LogoutOutlined />}
                  onClick={logout}
                >
                  Logout
                </Button>
              </Tooltip>
            </Space>
          )}
        </Header>
        <Content style={{ margin: '24px 16px 0' }}>
          <div
            style={{
              padding: 24,
              minHeight: 360,
              background: colorBgContainer,
              borderRadius: borderRadiusLG,
            }}
          >
            <Outlet />
          </div>
        </Content>
        <Footer style={{ textAlign: 'center' }}>
          D-BAM ©{new Date().getFullYear()} - Click, Not Command
          <br />
          <a
            href="https://github.com/amazingkj/dbaccman/issues"
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: '#1890ff' }}
          >
            Report an issue
          </a>
        </Footer>
      </Layout>
    </Layout>
  )
}

export default MainLayout
