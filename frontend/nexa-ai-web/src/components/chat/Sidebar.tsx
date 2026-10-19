import { useState } from 'react'
import { Archive, Plus, Search } from 'lucide-react'
import { ConversationItem } from './ConversationItem.tsx'
import { LoadingSpinner } from '../common/LoadingSpinner.tsx'
import type { Conversation } from '../../types/index.ts'

interface SidebarProps {
  readonly conversations: readonly Conversation[]
  readonly activeId: string | null
  readonly loading: boolean
  readonly onSelect: (id: string) => void
  readonly onCreate: () => void
  readonly onSearch: (query: string) => void
  readonly onRename: (id: string, newTitle: string) => Promise<void>
  readonly onToggleArchive: (id: string, currentStatus: boolean) => Promise<void>
  readonly onDelete: (id: string) => Promise<void>
  readonly showArchived: boolean
  readonly onToggleShowArchived: () => void
}

export function Sidebar({
  conversations,
  activeId,
  loading,
  onSelect,
  onCreate,
  onSearch,
  onRename,
  onToggleArchive,
  onDelete,
  showArchived,
  onToggleShowArchived,
}: SidebarProps) {
  const [searchQuery, setSearchQuery] = useState('')

  const handleSearchChange = (val: string) => {
    setSearchQuery(val)
    onSearch(val)
  }

  return (
    <aside className="d-flex flex-column h-100 bg-body-tertiary border-end p-3" style={{ minWidth: '280px', maxWidth: '340px' }}>
      <button
        type="button"
        className="btn btn-primary d-flex align-items-center justify-content-center gap-2 mb-3 py-2 fw-semibold"
        onClick={onCreate}
      >
        <Plus size={18} />
        <span>New Conversation</span>
      </button>

      <div className="input-group input-group-sm mb-3">
        <span className="input-group-text bg-body border-end-0 text-secondary">
          <Search size={14} />
        </span>
        <input
          type="text"
          className="form-control border-start-0"
          placeholder="Search chat history..."
          value={searchQuery}
          onChange={(e) => handleSearchChange(e.target.value)}
        />
      </div>

      <div className="d-flex justify-content-between align-items-center mb-2 px-1">
        <span className="small text-secondary fw-semibold text-uppercase tracking-wider" style={{ fontSize: '0.72rem' }}>
          {showArchived ? 'Archived Chats' : 'Recent Chats'}
        </span>
        <button
          type="button"
          className={`btn btn-sm btn-link p-0 text-decoration-none small d-flex align-items-center gap-1 ${
            showArchived ? 'text-primary fw-semibold' : 'text-secondary'
          }`}
          onClick={onToggleShowArchived}
        >
          <Archive size={12} />
          <span>{showArchived ? 'Show Active' : 'Show Archived'}</span>
        </button>
      </div>

      <div className="flex-grow-1 overflow-y-auto list-group list-group-flush pe-1">
        {loading && <LoadingSpinner message="Loading conversations..." />}

        {!loading && conversations.length === 0 && (
          <div className="text-center py-4 text-secondary small">
            {searchQuery ? 'No matching conversations.' : showArchived ? 'No archived chats.' : 'No conversations yet.'}
          </div>
        )}

        {!loading &&
          conversations.map((conv) => (
            <ConversationItem
              key={conv.id}
              conversation={conv}
              isActive={conv.id === activeId}
              onSelect={onSelect}
              onRename={onRename}
              onToggleArchive={onToggleArchive}
              onDelete={onDelete}
            />
          ))}
      </div>
    </aside>
  )
}
