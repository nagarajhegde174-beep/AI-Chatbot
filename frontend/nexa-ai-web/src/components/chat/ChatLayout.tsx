import { useEffect, useRef, useState, useCallback } from 'react'
import { Download, Menu, Sparkles, Trash2 } from 'lucide-react'
import { Sidebar } from './Sidebar.tsx'
import { MessageItem } from './MessageItem.tsx'
import { MessageInput } from './MessageInput.tsx'
import { ModelSelector } from './ModelSelector.tsx'
import { LoadingSpinner } from '../common/LoadingSpinner.tsx'
import { ErrorBanner } from '../common/ErrorBanner.tsx'
import { chatService } from '../../services/chatService.ts'
import { streamMessage } from '../../services/aiService.ts'
import type { Conversation, FeedbackType, Message } from '../../types/index.ts'

export function ChatLayout() {
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [loadingConversations, setLoadingConversations] = useState(true)
  const [loadingMessages, setLoadingMessages] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showArchived, setShowArchived] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [selectedModel, setSelectedModel] = useState<string>('gpt-4o-mini')
  const [isStreaming, setIsStreaming] = useState(false)
  const [streamController, setStreamController] = useState<AbortController | null>(null)

  const messagesEndRef = useRef<HTMLDivElement>(null)

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  const loadConversations = useCallback(async () => {
    setLoadingConversations(true)
    try {
      const list = await chatService.listConversations(showArchived ? true : false)
      setConversations(list)
      if (list.length > 0 && !activeId) {
        const first = list[0]
        if (first) {
          setActiveId(first.id)
        }
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load conversations.')
    } finally {
      setLoadingConversations(false)
    }
  }, [showArchived, activeId])

  useEffect(() => {
    void loadConversations()
  }, [loadConversations])

  useEffect(() => {
    if (!activeId) {
      setMessages([])
      return
    }

    let isMounted = true
    setLoadingMessages(true)

    chatService
      .listMessages(activeId)
      .then((res) => {
        if (isMounted) {
          setMessages(res)
          setTimeout(scrollToBottom, 50)
        }
      })
      .catch((err: unknown) => {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Failed to load chat history.')
        }
      })
      .finally(() => {
        if (isMounted) setLoadingMessages(false)
      })

    return () => {
      isMounted = false
    }
  }, [activeId])

  const handleCreateConversation = async () => {
    try {
      const newConv = await chatService.createConversation('New Conversation', 'gpt-4o-mini')
      setConversations((prev) => [newConv, ...prev])
      setActiveId(newConv.id)
      setMessages([])
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to create conversation.')
    }
  }

  const handleSearch = async (query: string) => {
    if (!query.trim()) {
      void loadConversations()
      return
    }
    try {
      const results = await chatService.searchConversations(query.trim())
      setConversations(results)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Search failed.')
    }
  }

  const handleRename = async (id: string, newTitle: string) => {
    try {
      const updated = await chatService.updateConversation(id, { title: newTitle })
      setConversations((prev) => prev.map((c) => (c.id === id ? { ...c, title: updated.title } : c)))
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to rename conversation.')
    }
  }

  const handleToggleArchive = async (id: string, currentStatus: boolean) => {
    try {
      await chatService.setArchiveStatus(id, !currentStatus)
      setConversations((prev) => prev.filter((c) => c.id !== id))
      if (activeId === id) {
        setActiveId(null)
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to update archive status.')
    }
  }

  const handleDelete = async (id: string) => {
    if (!window.confirm('Are you sure you want to delete this conversation?')) return
    try {
      await chatService.deleteConversation(id)
      setConversations((prev) => prev.filter((c) => c.id !== id))
      if (activeId === id) {
        setActiveId(null)
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to delete conversation.')
    }
  }

  useEffect(() => {
    if (activeId) {
      const active = conversations.find((c) => c.id === activeId)
      if (active?.model) {
        setSelectedModel(active.model)
      }
    }
  }, [activeId, conversations])

  const handleStopGeneration = () => {
    if (streamController) {
      streamController.abort()
      setStreamController(null)
    }
    setIsStreaming(false)
    setMessages((prev) =>
      prev.map((m) =>
        m.status === 'IN_PROGRESS'
          ? { ...m, status: m.content ? 'COMPLETED' : 'INCOMPLETE' }
          : m
      )
    )
  }

  const handleSendMessage = async (content: string) => {
    let convId = activeId
    if (!convId) {
      try {
        const newConv = await chatService.createConversation(
          content.slice(0, 30) || 'New Conversation',
          selectedModel
        )
        setConversations((prev) => [newConv, ...prev])
        setActiveId(newConv.id)
        convId = newConv.id
      } catch (err: unknown) {
        setError(err instanceof Error ? err.message : 'Failed to create conversation.')
        return
      }
    }

    const userMsgId = 'user-' + Date.now()
    const assistantMsgId = 'assistant-' + Date.now()
    const userMsg: Message = {
      id: userMsgId,
      conversationId: convId,
      userId: 'current-user',
      role: 'USER',
      content,
      status: 'COMPLETED',
      sequenceNumber: (messages.length || 0) + 1,
      isRegenerated: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }
    const assistantPlaceholder: Message = {
      id: assistantMsgId,
      conversationId: convId,
      userId: 'nexa-assistant',
      role: 'ASSISTANT',
      content: '',
      model: selectedModel,
      status: 'IN_PROGRESS',
      sequenceNumber: (messages.length || 0) + 2,
      isRegenerated: false,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    }

    setMessages((prev) => [...prev, userMsg, assistantPlaceholder])
    setTimeout(scrollToBottom, 50)
    setIsStreaming(true)

    const ctrl = streamMessage(
      convId,
      content,
      selectedModel,
      (token) => {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === assistantMsgId
              ? { ...m, content: m.content + token, status: 'IN_PROGRESS' }
              : m
          )
        )
        scrollToBottom()
      },
      (summary) => {
        setMessages((prev) =>
          prev.map((m) =>
            m.id === assistantMsgId
              ? {
                  ...m,
                  content: summary.content || m.content,
                  status: 'COMPLETED',
                  totalTokens: summary.usage?.totalTokens,
                  promptTokens: summary.usage?.promptTokens,
                  completionTokens: summary.usage?.completionTokens,
                }
              : m
          )
        )
        setIsStreaming(false)
        setStreamController(null)
        setTimeout(scrollToBottom, 50)
      },
      async (streamErr) => {
        // Fallback to standard chatService if SSE stream endpoint is not available
        try {
          const resp = await chatService.sendMessage(convId, content)
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantMsgId ? resp : m))
          )
        } catch (fallbackErr: unknown) {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantMsgId ? { ...m, status: 'FAILED' } : m
            )
          )
          setError(streamErr || (fallbackErr instanceof Error ? fallbackErr.message : 'Message failed.'))
        } finally {
          setIsStreaming(false)
          setStreamController(null)
        }
      }
    )
    setStreamController(ctrl)
  }

  const handleRetry = async (messageId: string) => {
    if (isStreaming) return
    const index = messages.findIndex((m) => m.id === messageId)
    if (index > 0) {
      const prevUserMsg = messages[index - 1]
      if (prevUserMsg && prevUserMsg.role === 'USER') {
        setMessages((prev) => prev.filter((m) => m.id !== messageId))
        await handleSendMessage(prevUserMsg.content)
        return
      }
    }
    await handleRegenerate(messageId)
  }

  const handleRegenerate = async (messageId: string) => {
    if (!activeId) return
    try {
      const newResponse = await chatService.regenerateMessage(activeId, messageId)
      setMessages((prev) => [...prev, newResponse])
      setTimeout(scrollToBottom, 50)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Regeneration failed.')
    }
  }

  const handleEdit = async (messageId: string, newContent: string) => {
    if (!activeId) return
    try {
      const newResponse = await chatService.editMessage(activeId, messageId, newContent)
      setMessages((prev) =>
        prev.map((m) => (m.id === messageId ? { ...m, content: newContent } : m)).concat(newResponse)
      )
      setTimeout(scrollToBottom, 50)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Edit failed.')
    }
  }

  const handleFeedback = async (messageId: string, feedback: FeedbackType) => {
    if (!activeId) return
    try {
      const updated = await chatService.submitFeedback(activeId, messageId, feedback)
      setMessages((prev) => prev.map((m) => (m.id === messageId ? { ...m, feedback: updated.feedback } : m)))
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Feedback submission failed.')
    }
  }

  const handleExport = async (format: 'json' | 'markdown' | 'text') => {
    if (!activeId) return
    try {
      const res = await chatService.exportConversation(activeId, format)
      const blob = new Blob([res.content], { type: 'text/plain;charset=utf-8' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `conversation-${activeId}.${format === 'markdown' ? 'md' : format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Export failed.')
    }
  }

  const activeConversation = conversations.find((c) => c.id === activeId)

  return (
    <div className="d-flex flex-grow-1 overflow-hidden" style={{ height: 'calc(100vh - 57px)' }}>
      {/* Sidebar for Desktop */}
      <div className="d-none d-md-block h-100">
        <Sidebar
          conversations={conversations}
          activeId={activeId}
          loading={loadingConversations}
          onSelect={(id) => {
            setActiveId(id)
            setSidebarOpen(false)
          }}
          onCreate={() => {
            void handleCreateConversation()
            setSidebarOpen(false)
          }}
          onSearch={(q) => void handleSearch(q)}
          onRename={handleRename}
          onToggleArchive={handleToggleArchive}
          onDelete={handleDelete}
          showArchived={showArchived}
          onToggleShowArchived={() => setShowArchived((prev) => !prev)}
        />
      </div>

      {/* Mobile Drawer */}
      {sidebarOpen && (
        <div
          className="position-fixed top-0 start-0 w-100 h-100 z-3 d-md-none bg-dark bg-opacity-50"
          onClick={() => setSidebarOpen(false)}
        >
          <div
            className="bg-body h-100 shadow"
            style={{ width: '280px' }}
            onClick={(e) => e.stopPropagation()}
          >
            <Sidebar
              conversations={conversations}
              activeId={activeId}
              loading={loadingConversations}
              onSelect={(id) => {
                setActiveId(id)
                setSidebarOpen(false)
              }}
              onCreate={() => {
                void handleCreateConversation()
                setSidebarOpen(false)
              }}
              onSearch={(q) => void handleSearch(q)}
              onRename={handleRename}
              onToggleArchive={handleToggleArchive}
              onDelete={handleDelete}
              showArchived={showArchived}
              onToggleShowArchived={() => setShowArchived((prev) => !prev)}
            />
          </div>
        </div>
      )}

      {/* Main Chat Area */}
      <main className="d-flex flex-column flex-grow-1 h-100 overflow-hidden bg-body">
        {/* Chat Header */}
        <div className="navbar border-bottom px-3 py-2 d-flex justify-content-between align-items-center flex-shrink-0 bg-body">
          <div className="d-flex align-items-center gap-2 overflow-hidden">
            <button
              type="button"
              className="btn btn-sm btn-outline-secondary d-md-none p-1"
              onClick={() => setSidebarOpen(true)}
              aria-label="Toggle sidebar"
            >
              <Menu size={18} />
            </button>
            <div className="overflow-hidden">
              <h2 className="fs-6 fw-bold mb-0 text-truncate">
                {activeConversation ? activeConversation.title : 'NexaAI Chat'}
              </h2>
              {activeConversation && (
                <span className="text-secondary small d-none d-sm-inline" style={{ fontSize: '0.75rem' }}>
                  Model: {activeConversation.model} • RAG: {activeConversation.ragEnabled ? 'On' : 'Off'}
                </span>
              )}
            </div>
          </div>

          <div className="d-flex align-items-center gap-2">
            <ModelSelector
              currentModel={selectedModel}
              onSelectModel={(m) => setSelectedModel(m)}
              disabled={isStreaming}
            />

            {activeConversation && (
              <div className="d-flex align-items-center gap-1">
                <div className="dropdown">
                  <button
                    type="button"
                    className="btn btn-sm btn-outline-secondary dropdown-toggle d-flex align-items-center gap-1"
                    data-bs-toggle="dropdown"
                    aria-expanded="false"
                    title="Export conversation"
                  >
                    <Download size={14} />
                    <span className="d-none d-sm-inline">Export</span>
                  </button>
                  <ul className="dropdown-menu dropdown-menu-end shadow-sm">
                    <li>
                      <button className="dropdown-item small" onClick={() => void handleExport('markdown')}>
                        Export as Markdown (.md)
                      </button>
                    </li>
                    <li>
                      <button className="dropdown-item small" onClick={() => void handleExport('json')}>
                        Export as JSON (.json)
                      </button>
                    </li>
                    <li>
                      <button className="dropdown-item small" onClick={() => void handleExport('text')}>
                        Export as Plain Text (.txt)
                      </button>
                    </li>
                  </ul>
                </div>

                <button
                  type="button"
                  className="btn btn-sm btn-outline-danger p-1 ms-1"
                  onClick={() => void handleDelete(activeConversation.id)}
                  title="Delete conversation"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Error notification banner */}
        {error && (
          <div className="px-3 pt-2">
            <ErrorBanner message={error} onDismiss={() => setError(null)} />
          </div>
        )}

        {/* Message List */}
        <div className="flex-grow-1 overflow-y-auto p-3 p-md-4">
          {loadingMessages && <LoadingSpinner message="Loading chat history..." />}

          {!loadingMessages && messages.length === 0 && (
            <div className="h-100 d-flex flex-column align-items-center justify-content-center text-center p-4 text-secondary">
              <div className="rounded-circle bg-primary-subtle text-primary p-3 mb-3">
                <Sparkles size={28} />
              </div>
              <h3 className="fs-5 fw-bold mb-1 text-body">Start a new conversation</h3>
              <p className="small mb-3" style={{ maxWidth: '420px' }}>
                Select a model from the top bar and ask anything. Spring AI Model Router provides multi-provider fallback and streaming token rendering.
              </p>
              <div className="d-flex flex-wrap gap-2 justify-content-center">
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => void handleSendMessage('What are the core capabilities of NexaAI?')}
                >
                  "What are the core capabilities of NexaAI?"
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-secondary"
                  onClick={() => void handleSendMessage('Explain the microservices architecture.')}
                >
                  "Explain the microservices architecture."
                </button>
              </div>
            </div>
          )}

          {!loadingMessages &&
            messages.map((m) => (
              <MessageItem
                key={m.id}
                message={m}
                onRegenerate={handleRegenerate}
                onRetry={handleRetry}
                onEdit={handleEdit}
                onFeedback={handleFeedback}
              />
            ))}
          <div ref={messagesEndRef} />
        </div>

        {/* Bottom Message Input */}
        <MessageInput
          onSend={handleSendMessage}
          disabled={loadingMessages}
          isStreaming={isStreaming}
          onStop={handleStopGeneration}
        />
      </main>
    </div>
  )
}
