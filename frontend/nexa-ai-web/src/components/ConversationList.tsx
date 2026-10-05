/**
 * The sidebar conversation list.
 *
 * <p>Filters and archive are handled here rather than in the chat page, because they are
 * navigation state: they change which conversations exist to be selected, not the content of the
 * selected one.
 */

import { useMemo, useState } from 'react'
import { Archive, ArchiveRestore, MessageSquare, Search, Trash2 } from 'lucide-react'

import type { Conversation } from '../api/chat'

export type ConversationFilter = 'active' | 'archived'

export function ConversationList({
  conversations,
  selectedId,
  filter,
  onFilterChange,
  onSelect,
  onSearchChange,
  onRename,
  onArchiveToggle,
  onDelete,
}: {
  readonly conversations: readonly Conversation[]
  readonly selectedId: string | null
  readonly filter: ConversationFilter
  readonly onFilterChange: (filter: ConversationFilter) => void
  readonly onSelect: (id: string) => void
  readonly onSearchChange: (query: string) => void
  readonly onRename: (id: string, title: string) => void
  readonly onArchiveToggle: (conversation: Conversation) => void
  readonly onDelete: (conversation: Conversation) => void
}): React.JSX.Element {
  const [renamingId, setRenamingId] = useState<string | null>(null)

  const grouped = useMemo(() => {
    // Grouped by recency, because a flat list of forty conversations sorted by date is a wall.
    const now = Date.now()
    const today: Conversation[] = []
    const week: Conversation[] = []
    const older: Conversation[] = []

    for (const conversation of conversations) {
      const stamp = conversation.lastMessageAt ?? conversation.createdAt
      const ageDays = (now - new Date(stamp).getTime()) / 86_400_000
      if (ageDays < 1) {
        today.push(conversation)
      } else if (ageDays < 7) {
        week.push(conversation)
      } else {
        older.push(conversation)
      }
    }

    return [
      { label: 'Today', items: today },
      { label: 'Previous 7 days', items: week },
      { label: 'Older', items: older },
    ].filter((group) => group.items.length > 0)
  }, [conversations])

  return (
    <div className="nexa-conversation-list">
      <div className="nexa-conversation-filters">
        <div className="input-group input-group-sm">
          <span className="input-group-text">
            <Search size={14} aria-hidden="true" />
          </span>
          <input
            type="search"
            className="form-control"
            placeholder={filter === 'archived' ? 'Search archived' : 'Search'}
            aria-label="Search conversations"
            onChange={(event) => onSearchChange(event.target.value)}
          />
        </div>

        <div className="btn-group btn-group-sm w-100 mt-2" role="group" aria-label="Filter">
          <button
            type="button"
            className={`btn ${filter === 'active' ? 'btn-primary' : 'btn-outline-secondary'}`}
            aria-pressed={filter === 'active'}
            onClick={() => onFilterChange('active')}
          >
            Active
          </button>
          <button
            type="button"
            className={`btn ${filter === 'archived' ? 'btn-primary' : 'btn-outline-secondary'}`}
            aria-pressed={filter === 'archived'}
            onClick={() => onFilterChange('archived')}
          >
            Archived
          </button>
        </div>
      </div>

      {grouped.length === 0 ? (
        <p className="text-body-secondary small px-2 py-3 mb-0">
          No conversations yet.
        </p>
      ) : (
        grouped.map((group) => (
          <section key={group.label} className="nexa-conversation-group">
            <h3 className="nexa-conversation-group-label">{group.label}</h3>
            <ul className="list-unstyled mb-0">
              {group.items.map((conversation) => (
                <li key={conversation.id}>
                  {renamingId === conversation.id ? (
                    <RenameForm
                      initialTitle={conversation.title}
                      onCancel={() => setRenamingId(null)}
                      onSubmit={(title) => {
                        onRename(conversation.id, title)
                        setRenamingId(null)
                      }}
                    />
                  ) : (
                    <div
                      className={`nexa-conversation-item ${selectedId === conversation.id ? 'is-selected' : ''}`}
                    >
                      <button
                        type="button"
                        className="nexa-conversation-select"
                        aria-current={selectedId === conversation.id ? 'true' : undefined}
                        onClick={() => onSelect(conversation.id)}
                      >
                        <MessageSquare size={14} className="flex-shrink-0" aria-hidden="true" />
                        <span className="nexa-conversation-title">{conversation.title}</span>
                      </button>

                      <div className="nexa-conversation-actions">
                        <button
                          type="button"
                          className="btn btn-sm btn-ghost"
                          aria-label={`Rename ${conversation.title}`}
                          onClick={() => {
                            setRenamingId(conversation.id)
                          }}
                        >
                          Rename
                        </button>

                        <button
                          type="button"
                          className="btn btn-sm btn-ghost"
                          aria-label={
                            conversation.status === 'ARCHIVED'
                              ? `Restore ${conversation.title}`
                              : `Archive ${conversation.title}`
                          }
                          onClick={() => onArchiveToggle(conversation)}
                        >
                          {conversation.status === 'ARCHIVED' ? (
                            <ArchiveRestore size={14} aria-hidden="true" />
                          ) : (
                            <Archive size={14} aria-hidden="true" />
                          )}
                        </button>

                        <button
                          type="button"
                          className="btn btn-sm btn-ghost text-danger"
                          aria-label={`Delete ${conversation.title}`}
                          onClick={() => onDelete(conversation)}
                        >
                          <Trash2 size={14} aria-hidden="true" />
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </section>
        ))
      )}
    </div>
  )
}

/**
 * The inline rename form.
 *
 * <p>A prompt() would work and is why this is a component instead: a modal dialog cannot be
 * styled, cannot be keyboard-navigated predictably, and blocks the thread. An inline field is
 * also closer to how the rename will feel once it is drag-to-rename.
 */
function RenameForm({
  initialTitle,
  onSubmit,
  onCancel,
}: {
  readonly initialTitle: string
  readonly onSubmit: (title: string) => void
  readonly onCancel: () => void
}): React.JSX.Element {
  const [title, setTitle] = useState(initialTitle)

  return (
    <form
      className="nexa-conversation-rename"
      onSubmit={(event) => {
        event.preventDefault()
        // Guarded rather than submitted blindly: a blank rename is rejected by the API with a
        // 400, and there is no reason to spend a round trip finding that out.
        if (title.trim() !== '') {
          onSubmit(title.trim())
        }
      }}
    >
      <div className="d-flex gap-1 p-1">
        <input
          type="text"
          className="form-control form-control-sm"
          value={title}
          aria-label="Conversation title"
          autoFocus
          onChange={(event) => setTitle(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Escape') {
              onCancel()
            }
          }}
        />
        <button type="submit" className="btn btn-sm btn-primary" disabled={title.trim() === ''}>
          Save
        </button>
        <button type="button" className="btn btn-sm btn-ghost" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
