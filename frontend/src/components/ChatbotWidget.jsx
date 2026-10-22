import { useState, useEffect, useRef, useCallback } from 'react'
import { MessageCircle, X, Send, Trash2, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { sendMessageToChatbot, getChatbotHistory, clearChatbotHistory, ensureArray } from '../services/api'
import { toast } from 'sonner'

export default function ChatbotWidget() {
  const { user } = useAuth()
  const isStudent = user?.role === 'STUDENT'
  const accountKey = user?.userId ?? user?.id ?? null

  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)

  const messagesEndRef = useRef(null)

  useEffect(() => {
    // Do not leave a previous account's conversation visible after logout or account switching.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setMessages([])
    setIsOpen(false)
  }, [accountKey])

  // Load history when widget is opened
  const loadHistory = useCallback(async () => {
    setLoadingHistory(true)
    try {
      const history = ensureArray(await getChatbotHistory())
      setMessages(
        history.map((m) => ({
          id: m.id,
          text: m.content,
          sender: m.sender, // "USER" or "BOT"
        })),
      )
    } catch {
      toast.error('Failed to load chat history')
    } finally {
      setLoadingHistory(false)
    }
  }, [])

  useEffect(() => {
    if (isOpen && isStudent) {
      // Loading saved messages is the synchronization this effect handles.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void loadHistory()
    }
  }, [isOpen, isStudent, loadHistory])

  // Scroll to bottom helper
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  // Scroll when message history changes
  useEffect(() => {
    if (isOpen) {
      scrollToBottom()
    }
  }, [messages, isOpen])

  async function handleSend(e) {
    e.preventDefault()
    if (!input.trim() || loading) return

    const userText = input.trim()
    setInput('')

    // Append user message locally
    const newUserMsg = { id: Date.now(), text: userText, sender: 'USER' }
    setMessages((prev) => [...prev, newUserMsg])
    setLoading(true)

    try {
      const res = await sendMessageToChatbot({ message: userText })
      setMessages((prev) => [...prev, { id: Date.now() + 1, text: res.reply, sender: 'BOT' }])
    } catch (error) {
      toast.error(error?.response?.data?.message || 'The assistant could not respond. Please try again.')
      // Remove user's temporary message on error to stay in sync
      setMessages((prev) => prev.filter((m) => m.id !== newUserMsg.id))
    } finally {
      setLoading(false)
    }
  }

  async function handleClearHistory() {
    if (!window.confirm('Clear all conversation history?')) return
    try {
      await clearChatbotHistory()
      setMessages([])
      toast.success('Chat history cleared')
    } catch {
      toast.error('Failed to clear history')
    }
  }

  if (!isStudent) return null

  // Simple Markdown-like formatter for bold and lists
  function formatMessageText(text) {
    if (!text) return ''
    // Handle line breaks
    return text.split('\n').map((line, lineIdx) => {
      // Handle bold **text**
      let parts = line.split(/\*\*(.*?)\*\*/g)
      const formattedLine = parts.map((part, partIdx) => {
        if (partIdx % 2 === 1) {
          return <strong key={partIdx}>{part}</strong>
        }
        return part
      })

      // Check if it's a bullet item
      if (line.startsWith('- ') || line.startsWith('* ')) {
        return (
          <li key={lineIdx} style={{ marginLeft: 14, marginTop: 4 }}>
            {formattedLine.slice(1)}
          </li>
        )
      }

      return (
        <p key={lineIdx} style={{ margin: '4px 0', lineHeight: 1.5 }}>
          {formattedLine}
        </p>
      )
    })
  }

  return (
    <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1000, fontFamily: 'inherit' }}>
      {/* Floating Action Button */}
      {!isOpen && (
        <button
          type="button"
          onClick={() => setIsOpen(true)}
          style={{
            width: 56,
            height: 56,
            borderRadius: '50%',
            background: 'var(--accent)',
            color: '#fff',
            border: 'none',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            boxShadow: '0 4px 14px rgba(124, 58, 237, 0.4)',
            transition: 'transform 0.2s',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.transform = 'scale(1.08)')}
          onMouseLeave={(e) => (e.currentTarget.style.transform = 'scale(1)')}
        >
          <MessageCircle size={26} />
        </button>
      )}

      {/* Expanded Chat Box */}
      {isOpen && (
        <div
          className="card"
          style={{
            width: 'min(360px, calc(100vw - 48px))',
            height: 500,
            display: 'flex',
            flexDirection: 'column',
            boxShadow: '0 8px 30px rgba(0, 0, 0, 0.5)',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            background: '#121214',
            overflow: 'hidden',
          }}
        >
          {/* Header */}
          <div
            style={{
              padding: '12px 16px',
              background: 'rgba(124, 58, 237, 0.1)',
              borderBottom: '1px solid rgba(255,255,255,0.06)',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <div>
              <div style={{ fontWeight: 900, fontSize: 15, display: 'flex', alignItems: 'center', gap: 6 }}>
                <span>RCR Assistant</span>
                <span
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    background: '#10b981',
                    display: 'inline-block',
                  }}
                />
              </div>
              <div style={{ fontSize: 11, opacity: 0.7, marginTop: 2 }}>Academic & System Guide</div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {messages.length > 0 && (
                <button
                  type="button"
                  onClick={handleClearHistory}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: 'rgba(255,255,255,0.6)',
                    cursor: 'pointer',
                    padding: 4,
                  }}
                  title="Clear history"
                >
                  <Trash2 size={16} />
                </button>
              )}
              <button
                type="button"
                onClick={() => setIsOpen(false)}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'rgba(255,255,255,0.6)',
                  cursor: 'pointer',
                  padding: 4,
                }}
              >
                <X size={18} />
              </button>
            </div>
          </div>

          {/* Messages Area */}
          <div
            style={{
              flex: 1,
              padding: 16,
              overflowY: 'auto',
              display: 'flex',
              flexDirection: 'column',
              gap: 12,
            }}
          >
            {loadingHistory ? (
              <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                <Loader2 className="animate-spin" size={24} style={{ opacity: 0.6 }} />
              </div>
            ) : messages.length === 0 ? (
              <div style={{ margin: 'auto', textAlign: 'center', opacity: 0.7, padding: 20 }}>
                <div style={{ fontWeight: 800, fontSize: 14 }}>Ask RCR Assistant</div>
                <div style={{ fontSize: 12, marginTop: 6, lineHeight: 1.5 }}>
                  Ask about any subject, programming, studies, careers, or using the RCR platform.
                </div>
              </div>
            ) : (
              messages.map((m) => {
                const isBot = m.sender === 'BOT'
                return (
                  <div
                    key={m.id}
                    style={{
                      alignSelf: isBot ? 'flex-start' : 'flex-end',
                      maxWidth: '85%',
                      background: isBot ? 'rgba(255, 255, 255, 0.04)' : 'var(--accent)',
                      color: '#fff',
                      padding: '10px 14px',
                      borderRadius: isBot ? '14px 14px 14px 2px' : '14px 14px 2px 14px',
                      border: isBot ? '1px solid rgba(255, 255, 255, 0.04)' : 'none',
                      fontSize: 13,
                    }}
                  >
                    {formatMessageText(m.text)}
                  </div>
                )
              })
            )}

            {/* Typing Indicator */}
            {loading && (
              <div
                style={{
                  alignSelf: 'flex-start',
                  background: 'rgba(255, 255, 255, 0.04)',
                  padding: '10px 14px',
                  borderRadius: '14px 14px 14px 2px',
                  border: '1px solid rgba(255, 255, 255, 0.04)',
                  fontSize: 13,
                  color: 'rgba(255, 255, 255, 0.7)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
              >
                <Loader2 className="animate-spin" size={14} />
                <span>Assistant is typing...</span>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Footer Input Area */}
          <form
            onSubmit={handleSend}
            style={{
              padding: 12,
              borderTop: '1px solid rgba(255, 255, 255, 0.06)',
              display: 'flex',
              gap: 8,
              background: 'rgba(0,0,0,0.1)',
            }}
          >
            <input
              type="text"
              className="input"
              maxLength={2000}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask a question..."
              style={{
                borderRadius: 20,
                fontSize: 13,
                padding: '8px 14px',
                background: 'rgba(255,255,255,0.03)',
              }}
              disabled={loading}
            />
            <button
              type="submit"
              disabled={!input.trim() || loading}
              style={{
                width: 36,
                height: 36,
                borderRadius: '50%',
                background: input.trim() && !loading ? 'var(--accent)' : 'rgba(255, 255, 255, 0.05)',
                color: input.trim() && !loading ? '#fff' : 'rgba(255, 255, 255, 0.3)',
                border: 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: input.trim() && !loading ? 'pointer' : 'default',
                transition: 'all 0.2s',
              }}
            >
              <Send size={16} />
            </button>
          </form>
        </div>
      )}
    </div>
  )
}
