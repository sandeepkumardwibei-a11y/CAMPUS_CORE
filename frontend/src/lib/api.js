import axios from 'axios'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8300/api'

export const tokenStore = {
  get access() { return localStorage.getItem('cc-access') },
  get refresh() { return localStorage.getItem('cc-refresh') },
  set(access, refresh) {
    if (access) localStorage.setItem('cc-access', access)
    if (refresh) localStorage.setItem('cc-refresh', refresh)
  },
  clear() {
    localStorage.removeItem('cc-access')
    localStorage.removeItem('cc-refresh')
    localStorage.removeItem('cc-user')
  },
}

const api = axios.create({ baseURL: BASE_URL, headers: { 'Content-Type': 'application/json' } })

api.interceptors.request.use((config) => {
  const t = tokenStore.access
  if (t) config.headers.Authorization = `Bearer ${t}`
  // For file uploads (FormData) we must NOT force application/json — deleting the
  // header lets the browser set multipart/form-data with the correct boundary.
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    if (config.headers) {
      delete config.headers['Content-Type']
      delete config.headers['content-type']
    }
  }
  return config
})

let refreshing = null

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const { config, response } = error
    const original = config || {}
    // Attempt one silent refresh on 401 (skip auth endpoints)
    if (
      response &&
      response.status === 401 &&
      !original._retry &&
      tokenStore.refresh &&
      !String(original.url || '').includes('/auth/')
    ) {
      original._retry = true
      try {
        refreshing =
          refreshing ||
          axios.post(`${BASE_URL}/auth/refresh`, { refreshToken: tokenStore.refresh })
        const r = await refreshing
        refreshing = null
        const data = r.data?.data || r.data
        tokenStore.set(data.accessToken, data.refreshToken)
        original.headers.Authorization = `Bearer ${data.accessToken}`
        return api(original)
      } catch (e) {
        refreshing = null
        tokenStore.clear()
        if (!location.pathname.startsWith('/login')) location.href = '/login'
        return Promise.reject(e)
      }
    }
    return Promise.reject(error)
  }
)

// Normalizes the backend ApiResponse envelope { success, message, data }
export function unwrap(res) {
  const body = res?.data
  if (body && typeof body === 'object' && 'data' in body) return body.data
  return body
}

export function apiMessage(err, fallback = 'Something went wrong') {
  return (
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    err?.message ||
    fallback
  )
}

export default api
