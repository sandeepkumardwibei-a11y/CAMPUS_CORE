import { createContext, useContext, useState, useCallback } from 'react'
import { AuthApi } from '../lib/services'
import { tokenStore } from '../lib/api'

const AuthContext = createContext(null)

function loadUser() {
  try {
    const raw = localStorage.getItem('cc-user')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(loadUser)

  const persist = useCallback((data) => {
    // data = AuthResponse { accessToken, refreshToken, userId, name, email, role }
    tokenStore.set(data.accessToken, data.refreshToken)
    const u = { userId: data.userId, name: data.name, email: data.email, role: data.role }
    localStorage.setItem('cc-user', JSON.stringify(u))
    setUser(u)
    return u
  }, [])

  const login = useCallback(async (email, password) => {
    const data = await AuthApi.login({ email, password })
    return persist(data)
  }, [persist])

  const register = useCallback(async (body) => {
    const data = await AuthApi.register(body)
    return persist(data)
  }, [persist])

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  // Persist a chosen avatar (emoji) on the local user profile.
  const updateAvatar = useCallback((avatar) => {
    setUser((prev) => {
      if (!prev) return prev
      const next = { ...prev, avatar }
      localStorage.setItem('cc-user', JSON.stringify(next))
      return next
    })
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, register, logout, setUser, updateAvatar }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
