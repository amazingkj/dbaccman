import { useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import {
  DashboardOutlined,
  UserOutlined,
  KeyOutlined,
  MonitorOutlined,
  TableOutlined,
  LogoutOutlined,
} from '@ant-design/icons'
import { useAuth } from '../../hooks/useAuth'

const { Sider } = Layout

interface SidebarProps {
  collapsed: boolean
  onCollapse: (collapsed: boolean) => void
}

function Sidebar({ collapsed, onCollapse }: SidebarProps) {
  const location = useLocation()
  const navigate = useNavigate()
  const { logout, user } = useAuth()

  const menuItems = [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: 'Dashboard',
    },
    {
      key: '/accounts',
      icon: <UserOutlined />,
      label: 'Account Management',
    },
    {
      key: '/permissions',
      icon: <KeyOutlined />,
      label: 'Permission Management',
    },
    {
      key: '/sessions',
      icon: <MonitorOutlined />,
      label: 'Session Monitoring',
    },
    {
      key: '/tables',
      icon: <TableOutlined />,
      label: 'Table Management',
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Logout',
      danger: true,
    },
  ]

  const handleMenuClick = (key: string) => {
    if (key === 'logout') {
      logout()
    } else {
      navigate(key)
    }
  }

  return (
    <Sider
      collapsible
      collapsed={collapsed}
      onCollapse={onCollapse}
      theme="dark"
    >
      <div
        style={{
          height: 32,
          margin: 16,
          background: 'rgba(255, 255, 255, 0.2)',
          borderRadius: 6,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontWeight: 'bold',
        }}
      >
        {collapsed ? 'DB' : 'DBGate'}
      </div>
      {!collapsed && user && (
        <div
          style={{
            padding: '0 16px 16px',
            color: 'rgba(255, 255, 255, 0.65)',
            fontSize: '12px',
            textAlign: 'center',
          }}
        >
          {user.username} ({user.role})
        </div>
      )}
      <Menu
        theme="dark"
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={({ key }) => handleMenuClick(key)}
      />
    </Sider>
  )
}

export default Sidebar
