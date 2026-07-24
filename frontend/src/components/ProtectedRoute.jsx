import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { tokenStore } from '../lib/api'

export default function ProtectedRoute({ children }) {
  const { user } = useAuth()
  const loc = useLocation()
  if (!user || !tokenStore.access) {
    // Send first-time / logged-out visitors to the public landing page,
    // which then offers Login or Create account.
    return <Navigate to="/welcome" replace state={{ from: loc.pathname }} />
  }
  return children
}
