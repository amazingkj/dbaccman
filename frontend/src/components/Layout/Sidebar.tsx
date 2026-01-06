import { useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, ConfigProvider } from 'antd'
import {
  DashboardOutlined,
  UserOutlined,
  KeyOutlined,
  MonitorOutlined,
  TableOutlined,
  HddOutlined,
  CodeOutlined,
  DatabaseOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '../../store/authStore'

const { Sider } = Layout

interface SidebarProps {
  collapsed: boolean
  onCollapse: (collapsed: boolean) => void
}

function Sidebar({ collapsed, onCollapse }: SidebarProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { user } = useAuthStore()

  const isAdmin = user?.role === 'admin'

  // Admin sees all menu items, regular users only see Dashboard
  const menuItems = isAdmin
    ? [
        {
          key: '/dashboard',
          icon: <DashboardOutlined />,
          label: 'Dashboard',
        },
        {
          key: '/accounts',
          icon: <UserOutlined />,
          label: 'Accounts',
        },
        {
          key: '/permissions',
          icon: <KeyOutlined />,
          label: 'Permissions',
        },
        {
          key: '/sessions',
          icon: <MonitorOutlined />,
          label: 'Sessions',
        },
        {
          key: '/tables',
          icon: <TableOutlined />,
          label: 'Tables',
        },
        {
          key: '/tablespaces',
          icon: <HddOutlined />,
          label: 'Tablespaces',
        },
        {
          key: '/sql-console',
          icon: <CodeOutlined />,
          label: 'SQL Console',
        },
      ]
    : [
        {
          key: '/dashboard',
          icon: <DashboardOutlined />,
          label: 'Dashboard',
        },
      ]

  return (
    <ConfigProvider
      theme={{
        components: {
          Menu: {
            darkItemBg: 'transparent',
            darkSubMenuItemBg: 'transparent',
            darkItemSelectedBg: '#5d87ff',
            darkItemHoverBg: 'rgba(93, 135, 255, 0.15)',
            darkItemSelectedColor: '#ffffff',
          },
          Layout: {
            siderBg: '#2a3547',
            triggerBg: 'transparent',
            triggerColor: 'rgba(255, 255, 255, 0.65)',
          },
        },
      }}
    >
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={onCollapse}
        style={{
          background: '#2a3547',
          overflow: 'auto',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
        }}
      >
        <div
          style={{
            height: collapsed ? 48 : 64,
            margin: '12px 12px 20px',
            background: 'transparent',
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            color: '#fff',
            cursor: 'pointer',
            transition: 'all 0.2s',
            fontFamily: "'Plus Jakarta Sans', sans-serif",
          }}
          onClick={() => navigate('/dashboard')}
        >
          <DatabaseOutlined style={{ fontSize: 22, color: '#5d87ff' }} />
          {!collapsed && (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
              <span style={{ fontWeight: 700, fontSize: 18, lineHeight: 1.2 }}>DONUT</span>
              <span style={{ fontWeight: 300, fontSize: 9, color: 'rgba(255,255,255,0.6)', letterSpacing: '0.5px' }}>
                Database Account Manager
              </span>
            </div>
          )}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'transparent', border: 'none' }}
        />
      </Sider>
    </ConfigProvider>
  )
}

export default Sidebar
