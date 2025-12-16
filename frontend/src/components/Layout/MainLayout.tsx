import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Layout, theme } from 'antd'
import Sidebar from './Sidebar'

const { Header, Content, Footer } = Layout

function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken()

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sidebar collapsed={collapsed} onCollapse={setCollapsed} />
      <Layout>
        <Header style={{ padding: 0, background: colorBgContainer }}>
          <div style={{ padding: '0 24px', fontSize: '18px', fontWeight: 'bold' }}>
            DBGate - Database Admin Console
          </div>
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
          DBGate ©{new Date().getFullYear()} - Database Management Tool
        </Footer>
      </Layout>
    </Layout>
  )
}

export default MainLayout
