import { Suspense, lazy } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { Spin } from 'antd'
import MainLayout from './components/Layout/MainLayout'
import ProtectedRoute from './components/common/ProtectedRoute'
import AdminRoute from './components/common/AdminRoute'

// Lazy load pages for code splitting
const Login = lazy(() => import('./pages/Login'))
const Dashboard = lazy(() => import('./pages/Dashboard'))
const Accounts = lazy(() => import('./pages/Accounts'))
const Permissions = lazy(() => import('./pages/Permissions'))
const Sessions = lazy(() => import('./pages/Sessions'))
const Tables = lazy(() => import('./pages/Tables'))
const Tablespaces = lazy(() => import('./pages/Tablespaces'))
const SqlConsole = lazy(() => import('./pages/SqlConsole'))
const MyTables = lazy(() => import('./pages/MyTables'))
const MyTablespaces = lazy(() => import('./pages/MyTablespaces'))
const MyConsole = lazy(() => import('./pages/MyConsole'))

// Loading component for Suspense fallback
const PageLoading = () => (
  <div style={{
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    height: '100%',
    minHeight: 200,
  }}>
    <Spin size="large" />
  </div>
)

function App() {
  return (
    <Suspense fallback={<PageLoading />}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <MainLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          {/* Admin-only routes */}
          <Route path="accounts" element={<AdminRoute><Accounts /></AdminRoute>} />
          <Route path="permissions" element={<AdminRoute><Permissions /></AdminRoute>} />
          <Route path="sessions" element={<AdminRoute><Sessions /></AdminRoute>} />
          <Route path="tables" element={<AdminRoute><Tables /></AdminRoute>} />
          <Route path="tablespaces" element={<AdminRoute><Tablespaces /></AdminRoute>} />
          <Route path="sql-console" element={<AdminRoute><SqlConsole /></AdminRoute>} />
          {/* User routes (own schema only) */}
          <Route path="my-tables" element={<MyTables />} />
          <Route path="my-tablespaces" element={<MyTablespaces />} />
          <Route path="my-console" element={<MyConsole />} />
        </Route>
      </Routes>
    </Suspense>
  )
}

export default App
