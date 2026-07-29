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

// Avatars are stored separately, keyed by user ID, so the choice survives a
// logout (which clears 'cc-user') and is restored automatically on relogin.
function avatarKey(userId) { return `cc-avatar-${userId}` }
function loadAvatar(userId) {
  if (!userId) return null
  try { return localStorage.getItem(avatarKey(userId)) } catch { return null }
}
function saveAvatar(userId, avatar) {
  if (!userId) return
  try {
    if (avatar) localStorage.setItem(avatarKey(userId), avatar)
    else localStorage.removeItem(avatarKey(userId))
  } catch { /* ignore storage errors */ }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const u = loadUser()
    if (u && !u.avatar) {
      const savedAvatar = loadAvatar(u.userId)
      if (savedAvatar) return { ...u, avatar: savedAvatar }
    }
    return u
  })

  const persist = useCallback((data) => {
    // data = AuthResponse { accessToken, refreshToken, userId, name, email, role }
    tokenStore.set(data.accessToken, data.refreshToken)
    const savedAvatar = loadAvatar(data.userId)
    const u = { userId: data.userId, name: data.name, email: data.email, role: data.role, ...(savedAvatar ? { avatar: savedAvatar } : {}) }
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

  // Persist a chosen avatar (emoji) on the local user profile, and also in the
  // durable per-user store so it survives logout and reappears after relogin.
  const updateAvatar = useCallback((avatar) => {
    setUser((prev) => {
      if (!prev) return prev
      const next = { ...prev, avatar }
      localStorage.setItem('cc-user', JSON.stringify(next))
      saveAvatar(prev.userId, avatar)
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
