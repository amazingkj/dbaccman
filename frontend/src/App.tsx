import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './components/Layout/MainLayout'
import ProtectedRoute from './components/common/ProtectedRoute'
import AdminRoute from './components/common/AdminRoute'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Accounts from './pages/Accounts'
import Permissions from './pages/Permissions'
import Sessions from './pages/Sessions'
import Tables from './pages/Tables'
import Tablespaces from './pages/Tablespaces'
import SqlConsole from './pages/SqlConsole'

function App() {
  return (
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
      </Route>
    </Routes>
  )
}

export default App
