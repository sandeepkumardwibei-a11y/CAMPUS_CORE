import { useState, useEffect, useCallback } from 'react'

// Runs an async fn, exposes { data, loading, error, reload }
export function useAsync(fn, deps = [], immediate = true) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(immediate)
  const [error, setError] = useState(null)

  const run = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const d = await fn()
      setData(d)
      return d
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  useEffect(() => {
    if (immediate) run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run])

  return { data, loading, error, reload: run, setData }
}

// Read an entity's primary key regardless of the backend's field name.
// The backend uses domain-specific keys (courseId, programId, invoiceId, …)
// and never a plain `id`, so pass the expected key and fall back gracefully.
export function idOf(obj, key) {
  if (!obj) return null
  if (key && obj[key] != null) return obj[key]
  return obj.id ?? null
}

// Coerce various backend list shapes into an array
export function asArray(d) {
  if (Array.isArray(d)) return d
  if (d?.content && Array.isArray(d.content)) return d.content // Spring Page
  if (d && typeof d === 'object') return [d]
  return []
}

// Keep only rows whose status counts as "active" for dropdown pickers.
// Programs -> excludes DISCONTINUED, Courses -> excludes INACTIVE,
// Departments/Users -> keeps only ACTIVE. A missing/blank status defaults to active
// (backend defaults new rows to ACTIVE). Results are sorted ascending by the given id key.
export function activeOnly(d, idKey) {
  const list = asArray(d).filter((x) => {
    const s = String(x?.status ?? '').toUpperCase()
    return s === '' || s === 'ACTIVE'
  })
  if (idKey) list.sort((a, b) => (a?.[idKey] ?? 0) - (b?.[idKey] ?? 0))
  return list
}
