import { useState, type KeyboardEvent } from 'react'
import { Archive, ArchiveRestore, Check, Edit2, MessageSquare, Trash2, X } from 'lucide-react'
import type { Conversation } from '../../types/index.ts'

interface ConversationItemProps {
  readonly conversation: Conversation
  readonly isActive: boolean
  readonly onSelect: (id: string) => void
  readonly onRename: (id: string, newTitle: string) => Promise<void>
  readonly onToggleArchive: (id: string, currentStatus: boolean) => Promise<void>
  readonly onDelete: (id: string) => Promise<void>
}

export function ConversationItem({
  conversation,
  isActive,
  onSelect,
  onRename,
  onToggleArchive,
  onDelete,
}: ConversationItemProps) {
  const [isEditing, setIsEditing] = useState(false)
  const [title, setTitle] = useState(conversation.title)
  const [submitting, setSubmitting] = useState(false)

  const handleSaveRename = async () => {
    if (!title.trim() || title.trim() === conversation.title) {
      setIsEditing(false)
      setTitle(conversation.title)
      return
    }
    setSubmitting(true)
    try {
      await onRename(conversation.id, title.trim())
      setIsEditing(false)
    } finally {
      setSubmitting(false)
    }
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      void handleSaveRename()
    } else if (e.key === 'Escape') {
      setIsEditing(false)
      setTitle(conversation.title)
    }
  }

  return (
    <div
      className={`list-group-item list-group-item-action d-flex align-items-center justify-content-between p-2 rounded-2 border-0 mb-1 ${
        isActive ? 'active text-white' : ''
      }`}
      style={{ cursor: 'pointer' }}
      onClick={() => !isEditing && onSelect(conversation.id)}
    >
      <div className="d-flex align-items-center gap-2 flex-grow-1 overflow-hidden me-2">
        <MessageSquare size={16} className={`flex-shrink-0 ${isActive ? 'text-white' : 'text-secondary'}`} />
        {isEditing ? (
          <input
            type="text"
            className="form-control form-control-sm py-0 px-1"
            value={title}
            autoFocus
            disabled={submitting}
            onClick={(e) => e.stopPropagation()}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={handleKeyDown}
          />
        ) : (
          <span className="text-truncate small fw-medium">
            {conversation.title}
          </span>
        )}
      </div>

      <div className="d-flex align-items-center gap-1 flex-shrink-0" onClick={(e) => e.stopPropagation()}>
        {isEditing ? (
          <>
            <button
              type="button"
              className="btn btn-sm btn-link p-0 text-success"
              onClick={handleSaveRename}
              title="Save"
            >
              <Check size={14} />
            </button>
            <button
              type="button"
              className="btn btn-sm btn-link p-0 text-danger"
              onClick={() => {
                setIsEditing(false)
                setTitle(conversation.title)
              }}
              title="Cancel"
            >
              <X size={14} />
            </button>
          </>
        ) : (
          <>
            <button
              type="button"
              className={`btn btn-sm btn-link p-0 ${isActive ? 'text-white-50' : 'text-secondary'}`}
              onClick={() => setIsEditing(true)}
              title="Rename"
            >
              <Edit2 size={13} />
            </button>
            <button
              type="button"
              className={`btn btn-sm btn-link p-0 ${isActive ? 'text-white-50' : 'text-secondary'}`}
              onClick={() => void onToggleArchive(conversation.id, conversation.isArchived)}
              title={conversation.isArchived ? 'Unarchive' : 'Archive'}
            >
              {conversation.isArchived ? <ArchiveRestore size={13} /> : <Archive size={13} />}
            </button>
            <button
              type="button"
              className={`btn btn-sm btn-link p-0 ${isActive ? 'text-white-50' : 'text-danger'}`}
              onClick={() => void onDelete(conversation.id)}
              title="Delete"
            >
              <Trash2 size={13} />
            </button>
          </>
        )}
      </div>
    </div>
  )
}
