import { useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import {
  DashboardOutlined,
  UserOutlined,
  KeyOutlined,
  MonitorOutlined,
  TableOutlined,
  HddOutlined,
  CodeOutlined,
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
          label: 'My Account',
        },
      ]

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
          cursor: 'pointer',
        }}
        onClick={() => navigate('/dashboard')}
      >
        {collapsed ? 'DB' : 'D-BAM'}
      </div>
      <Menu
        theme="dark"
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={({ key }) => navigate(key)}
      />
    </Sider>
  )
}

export default Sidebar
