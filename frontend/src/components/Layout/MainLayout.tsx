import { useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'
import { Layout, Tag, Space, Avatar, Dropdown, theme } from 'antd'
import { LogoutOutlined, UserOutlined } from '@ant-design/icons'
import type { MenuProps } from 'antd'
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

  const getDbTypeColor = (dbType?: string) => {
    switch (dbType?.toUpperCase()) {
      case 'MYSQL': return '#00758F'
      case 'POSTGRESQL': return '#336791'
      case 'ORACLE': return '#F80000'
      default: return '#1890ff'
    }
  }

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      label: (
        <Space>
          <UserOutlined />
          <span>{user?.username}@{user?.host}</span>
        </Space>
      ),
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'logout',
      label: (
        <Space>
          <LogoutOutlined />
          <span>Logout</span>
        </Space>
      ),
      danger: true,
      onClick: logout,
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <SessionTimeout />
      <Sidebar collapsed={collapsed} onCollapse={setCollapsed} />
      <Layout style={{ marginLeft: collapsed ? 80 : 200, transition: 'margin-left 0.2s' }}>
        <Header
          style={{
            padding: '0 24px',
            background: '#fff',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            borderBottom: '1px solid #f0f0f0',
            boxShadow: '0 1px 4px rgba(0,0,0,0.05)',
          }}
        >
          <div
            style={{
              fontSize: '18px',
              fontWeight: 700,
              cursor: 'pointer',
              color: '#3c4b64',
            }}
            onClick={() => navigate('/dashboard')}
          >
            D-BAM
          </div>
          {user && (
            <Space size="middle">
              <Space>
                <Tag
                  color={getDbTypeColor(user.dbType)}
                  style={{ margin: 0, fontWeight: 500 }}
                >
                  {user.dbType?.toUpperCase()}
                </Tag>
                <Tag
                  color={user.role === 'admin' ? 'purple' : 'default'}
                  style={{ margin: 0 }}
                >
                  {user.role.toUpperCase()}
                </Tag>
              </Space>
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
                <Space style={{ cursor: 'pointer' }}>
                  <Avatar
                    size="small"
                    icon={<UserOutlined />}
                    style={{
                      background: '#3c4b64',
                    }}
                  />
                  <span style={{ fontWeight: 500 }}>{user.username}</span>
                </Space>
              </Dropdown>
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
