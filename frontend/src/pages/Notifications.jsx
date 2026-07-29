import { useState, useEffect, useCallback } from 'react'
import { Bell, Check, CheckCheck, Send, Search } from 'lucide-react'
import { NotificationApi } from '../lib/services'
import { asArray } from '../lib/hooks'
import { apiMessage } from '../lib/api'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { NOTIF_CATEGORIES } from '../lib/constants'
import {
  PageHeader, Card, Button, Badge, Spinner, EmptyState, Modal, Field, Input, Select,
} from '../components/ui'

export default function Notifications() {
  const toast = useToast()
  const { user } = useAuth()
  const [userId, setUserId] = useState(user?.userId || '')
  const [items, setItems] = useState(null)
  const [unread, setUnread] = useState(null)
  const [loading, setLoading] = useState(false)
  const [sendOpen, setSendOpen] = useState(false)
  const [form, setForm] = useState({ userId: '', message: '', category: 'INFO' })
  const [sending, setSending] = useState(false)

  const load = useCallback(async (id = userId) => {
    if (!id) return
    setLoading(true)
    try {
      const [list, count] = await Promise.all([
        NotificationApi.byUser(Number(id), { page: 0, size: 50 }),
        NotificationApi.unreadCount(Number(id)).catch(() => null),
      ])
      setItems(asArray(list))
      setUnread(typeof count === 'number' ? count : count?.count ?? count?.unread ?? null)
    } catch (e) { toast.error(apiMessage(e)) } finally { setLoading(false) }
  }, [userId, toast])

  useEffect(() => { if (user?.userId) load(user.userId) }, []) // eslint-disable-line

  const markRead = async (id) => {
    try { await NotificationApi.markRead(id); toast.success('Marked as read'); load(); window.dispatchEvent(new Event('cc:data-changed')) }
    catch (e) { toast.error(apiMessage(e)) }
  }
  const markAll = async () => {
    try { await NotificationApi.markAllRead(Number(userId)); toast.success('All marked read'); load(); window.dispatchEvent(new Event('cc:data-changed')) }
    catch (e) { toast.error(apiMessage(e)) }
  }
  const send = async () => {
    // Validation: no empty messages
    if (!form.message || !form.message.trim()) {
      toast.error('Please enter a message before sending.')
      return
    }
    // Validation: cannot send to yourself
    if (Number(form.userId) === Number(user?.userId)) {
      toast.error('You cannot send a notification to yourself.')
      return
    }
    setSending(true)
    try {
      await NotificationApi.send({ userId: Number(form.userId), message: form.message.trim(), category: form.category })
      toast.success('Notification sent'); setSendOpen(false); setForm({ userId: '', message: '', category: 'INFO' })
      if (Number(form.userId) === Number(userId)) load()
    } catch (e) { toast.error(apiMessage(e)) } finally { setSending(false) }
  }

  const isUnread = (n) => String(n.status || '').toUpperCase() === 'UNREAD' || n.read === false

  return (
    <div>
      <PageHeader icon={Bell} title="Notifications" subtitle="Alerts and messages delivered to a user."
        actions={<>
          <Button variant="outline" onClick={markAll} disabled={!userId}><CheckCheck size={16} /> Mark all read</Button>
          <Button onClick={() => setSendOpen(true)}><Send size={16} /> Send</Button>
        </>} />

      <Card className="p-4 mb-5 flex flex-wrap items-end gap-3">
        <div className="w-40"><span className="label">User ID</span><Input type="number" min={1} max={999999} value={userId} onChange={(e) => setUserId(e.target.value)} placeholder="5" /></div>
        <Button onClick={() => load()} loading={loading}><Search size={16} /> Load</Button>
        {unread != null && <span className="ml-auto text-sm" style={{ color: 'var(--text-muted)' }}>Unread: <span className="font-semibold text-emerald-400">{unread}</span></span>}
      </Card>

      <Card className="p-2">
        {loading ? <Spinner /> : !items ? (
          <EmptyState icon={Bell} title="Load notifications" hint="Enter a user ID to see their inbox." />
        ) : items.length === 0 ? (
          <EmptyState icon={Bell} title="Inbox zero" hint="No notifications for this user." />
        ) : (
          <ul className="divide-y" style={{ borderColor: 'var(--border)' }}>
            {items.map((n) => (
              <li key={n.notificationId} className={`flex items-start gap-3 p-4 ${isUnread(n) ? '' : 'opacity-60'}`}>
                <div className={`w-2 h-2 rounded-full mt-2 shrink-0 ${isUnread(n) ? 'bg-emerald-500' : 'bg-transparent'}`} />
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-0.5 flex-wrap">
                    {n.category && <Badge value={n.category} />}
                    {n.senderName && (
                      <span className="text-xs font-semibold text-emerald-500">From: {n.senderName}</span>
                    )}
                    <span className="text-xs" style={{ color: 'var(--text-faint)' }}>{n.createdAt || n.timestamp || ''}</span>
                  </div>
                  <p className="text-sm" style={{ color: 'var(--text)' }}>{n.message}</p>
                </div>
                {isUnread(n) && (
                  <button onClick={() => markRead(n.notificationId)} title="Mark read" className="p-1.5 rounded-lg hover:bg-emerald-500/10 text-emerald-400 shrink-0"><Check size={16} /></button>
                )}
              </li>
            ))}
          </ul>
        )}
      </Card>

      <Modal open={sendOpen} onClose={() => setSendOpen(false)} title="Send notification">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <Field label="Recipient user ID"><Input type="number" min={1} max={999999} value={form.userId} onChange={(e) => setForm({ ...form, userId: e.target.value })} placeholder="5" /></Field>
            <Field label="Category"><Select options={NOTIF_CATEGORIES} value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} /></Field>
          </div>
          <Field label="Message"><Input value={form.message} onChange={(e) => setForm({ ...form, message: e.target.value })} placeholder="Welcome to CampusCore!" /></Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setSendOpen(false)}>Cancel</Button>
            <Button onClick={send} loading={sending}>Send</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
