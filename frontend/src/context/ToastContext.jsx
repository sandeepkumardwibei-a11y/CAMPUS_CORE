import { createContext, useContext, useState, useCallback } from 'react'
import { CheckCircle2, XCircle, Info, X } from 'lucide-react'

const ToastContext = createContext(null)
let seq = 0

const toneMap = {
  success: { icon: CheckCircle2, cls: 'text-emerald-500' },
  error: { icon: XCircle, cls: 'text-rose-500' },
  info: { icon: Info, cls: 'text-emerald-400' },
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const remove = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), [])

  const push = useCallback((message, type = 'info') => {
    setToasts((t) => {
      // Don't stack an identical message that's already visible
      if (t.some((x) => x.message === message && x.type === type)) return t
      const id = ++seq
      setTimeout(() => remove(id), 4000)
      return [...t, { id, message, type }].slice(-4)
    })
  }, [remove])

  const toast = {
    success: (m) => push(m, 'success'),
    error: (m) => push(m, 'error'),
    info: (m) => push(m, 'info'),
  }

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 w-[min(92vw,360px)]">
        {toasts.map((t) => {
          const { icon: Icon, cls } = toneMap[t.type] || toneMap.info
          return (
            <div key={t.id} className="glass rounded-xl px-4 py-3 flex items-start gap-3 animate-fade-up">
              <Icon size={18} className={`mt-0.5 shrink-0 ${cls}`} />
              <p className="text-sm flex-1" style={{ color: 'var(--text)' }}>{t.message}</p>
              <button onClick={() => remove(t.id)} className="opacity-50 hover:opacity-100">
                <X size={15} />
              </button>
            </div>
          )
        })}
      </div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)
