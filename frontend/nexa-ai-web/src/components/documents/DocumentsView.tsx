import { useState, useEffect, useRef, type ChangeEvent } from 'react'
import {
  Upload, FileText, Search, Trash2, RotateCw, Download,
  AlertCircle, Eye, X, Loader2
} from 'lucide-react'
import { request } from '../../services/apiClient.ts'
import type { DocumentItem } from '../../types/index.ts'

export function DocumentsView() {
  const [documents, setDocuments] = useState<DocumentItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState<string | null>(null)
  const [selectedDoc, setSelectedDoc] = useState<DocumentItem | null>(null)
  const [docContent, setDocContent] = useState<{ textContent?: string; chunks?: Array<{ sequenceNumber?: number; tokenCount?: number; content?: string }> } | null>(null)
  const [loadingContent, setLoadingContent] = useState(false)

  const fileInputRef = useRef<HTMLInputElement>(null)

  const fetchDocuments = async (query = '') => {
    setLoading(true)
    setError(null)
    try {
      const q = query ? `?q=${encodeURIComponent(query)}` : ''
      const res = await request<{ content?: DocumentItem[] } | DocumentItem[]>(`/api/v1/documents${q}`)
      // Spring Data Page returns content array
      const list: DocumentItem[] = Array.isArray(res) ? res : (res.content || [])
      setDocuments(list)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load documents library.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let active = true
    request<{ content?: DocumentItem[] } | DocumentItem[]>('/api/v1/documents')
      .then((res) => {
        if (!active) return
        const list: DocumentItem[] = Array.isArray(res) ? res : (res.content ?? [])
        setDocuments(list)
        setLoading(false)
      })
      .catch((err: unknown) => {
        if (!active) return
        setError(err instanceof Error ? err.message : 'Failed to load documents library.')
        setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    void fetchDocuments(searchQuery)
  }

  const handleFileUpload = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    setUploading(true)
    setUploadProgress(`Uploading ${file.name}...`)
    setError(null)

    const formData = new FormData()
    formData.append('file', file)

    try {
      await request<DocumentItem>('/api/v1/documents', {
        method: 'POST',
        body: formData,
      })
      setUploadProgress('Uploaded successfully. Processing chunks...')
      setTimeout(() => {
        setUploadProgress(null)
        void fetchDocuments(searchQuery)
      }, 1000)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Document upload failed.')
      setUploadProgress(null)
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleReprocess = async (id: string) => {
    try {
      await request<DocumentItem>(`/api/v1/documents/${id}/reprocess`, {
        method: 'POST',
      })
      void fetchDocuments(searchQuery)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Reprocess request failed.')
    }
  }

  const handleDelete = async (id: string, name: string) => {
    if (!window.confirm(`Delete document "${name}"? This removes its embeddings and chunks.`)) return
    try {
      await request<void>(`/api/v1/documents/${id}`, {
        method: 'DELETE',
      })
      setDocuments((prev) => prev.filter((d) => d.id !== id))
      if (selectedDoc?.id === id) {
        setSelectedDoc(null)
        setDocContent(null)
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Delete failed.')
    }
  }

  const handleViewContent = async (doc: DocumentItem) => {
    setSelectedDoc(doc)
    setLoadingContent(true)
    try {
      const data = await request<{ textContent?: string; chunks?: Array<{ sequenceNumber?: number; tokenCount?: number; content?: string }> }>(`/api/v1/documents/${doc.id}/content`)
      setDocContent(data)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to retrieve document content.')
    } finally {
      setLoadingContent(false)
    }
  }

  const handleDownload = (id: string) => {
    const gatewayUrl = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'
    const token = localStorage.getItem('nexa_access_token')
    window.open(`${gatewayUrl}/api/v1/documents/${id}/download?token=${token || ''}`, '_blank')
  }

  const renderStatusBadge = (status: string) => {
    switch (status) {
      case 'READY':
        return <span className="nexa-badge nexa-badge-secondary">READY</span>
      case 'FAILED':
        return <span className="nexa-badge bg-danger-subtle text-danger border-danger">FAILED</span>
      case 'CHUNKING':
      case 'EMBEDDING':
      case 'EXTRACTING':
      case 'UPLOADED':
        return (
          <span className="nexa-badge nexa-badge-brand d-inline-flex align-items-center gap-1">
            <Loader2 size={10} className="spin" />
            <span>{status}</span>
          </span>
        )
      default:
        return <span className="nexa-badge nexa-badge-subtle">{status}</span>
    }
  }

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
  }

  return (
    <div className="flex-grow-1 overflow-y-auto p-4 p-md-5">
      <div className="container-xl" style={{ maxWidth: '1120px' }}>
        {/* Header */}
        <div className="d-flex flex-column flex-md-row align-items-md-center justify-content-between gap-3 mb-4">
          <div>
            <div className="d-inline-flex align-items-center gap-1.5 nexa-badge nexa-badge-secondary mb-2">
              <FileText size={13} />
              <span>RAG Document Library</span>
            </div>
            <h1 className="fs-3 fw-bold tracking-tight text-body mb-1">Documents & Knowledge Base</h1>
            <p className="text-secondary small mb-0">
              Upload documents to extract, chunk, and embed them into pgvector for chat retrieval.
            </p>
          </div>

          <div className="d-flex align-items-center gap-2">
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileUpload}
              className="d-none"
              accept=".pdf,.docx,.txt,.csv,.md"
            />
            <button
              type="button"
              className="nexa-btn-primary"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
            >
              <Upload size={15} />
              <span>{uploading ? 'Uploading...' : 'Upload Document'}</span>
            </button>
          </div>
        </div>

        {uploadProgress && (
          <div className="alert alert-info d-flex align-items-center gap-2 small p-3 mb-4 rounded-3">
            <Loader2 size={16} className="spin" />
            <span>{uploadProgress}</span>
          </div>
        )}

        {error && (
          <div className="alert alert-danger d-flex align-items-center justify-content-between small p-3 mb-4 rounded-3">
            <div className="d-flex align-items-center gap-2">
              <AlertCircle size={16} />
              <span>{error}</span>
            </div>
            <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none" onClick={() => fetchDocuments()}>
              Dismiss
            </button>
          </div>
        )}

        {/* Search & Filter Bar */}
        <div className="nexa-card p-3 mb-4">
          <form onSubmit={handleSearchSubmit} className="d-flex gap-2">
            <div className="position-relative flex-grow-1">
              <Search size={15} className="position-absolute start-0 ms-2.5 top-50 translate-middle-y text-muted" />
              <input
                type="search"
                className="nexa-input ps-4 py-1.5 small"
                placeholder="Search documents by filename..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <button type="submit" className="nexa-btn-secondary px-3 py-1.5 small">
              Search
            </button>
            {searchQuery && (
              <button
                type="button"
                className="nexa-btn-ghost px-2 py-1.5 small"
                onClick={() => {
                  setSearchQuery('')
                  void fetchDocuments('')
                }}
              >
                Clear
              </button>
            )}
          </form>
        </div>

        {/* Document Table */}
        <div className="nexa-card overflow-hidden">
          <div className="p-3 border-bottom border-subtle d-flex align-items-center justify-content-between">
            <span className="small fw-semibold text-secondary">
              Total Documents: <strong className="text-body font-monospace">{documents.length}</strong>
            </span>
            <button
              type="button"
              className="btn btn-sm btn-link p-0 text-decoration-none small text-secondary d-flex align-items-center gap-1"
              onClick={() => fetchDocuments(searchQuery)}
              disabled={loading}
            >
              <RotateCw size={13} className={loading ? 'spin' : ''} />
              <span>Refresh</span>
            </button>
          </div>

          {loading ? (
            <div className="p-5 text-center text-secondary small">
              <Loader2 size={24} className="spin mb-2 mx-auto" />
              <div>Loading documents library...</div>
            </div>
          ) : documents.length === 0 ? (
            <div className="p-5 text-center text-muted small">
              <div className="mb-3 d-inline-block position-relative">
                <img
                  src="/assets/rag-document-analysis.png"
                  alt="Document RAG Grounding"
                  width={140}
                  height={140}
                  className="rounded-4 shadow-sm border border-subtle"
                  style={{ objectFit: 'cover' }}
                />
              </div>
              <div className="fw-bold text-body fs-6 mb-1">No documents in your library yet</div>
              <p className="mb-3 text-secondary mx-auto" style={{ maxWidth: '420px' }}>
                Upload PDF, DOCX, TXT, or CSV documents. NexaAI automatically generates vector embeddings so you can query them with semantic citations.
              </p>
              <button
                type="button"
                className="nexa-btn-primary mx-auto"
                onClick={() => fileInputRef.current?.click()}
              >
                <Upload size={14} />
                <span>Upload your first document</span>
              </button>
            </div>
          ) : (
            <div className="nexa-table-container border-0 rounded-0">
              <table className="nexa-table">
                <thead>
                  <tr>
                    <th>Filename</th>
                    <th>Type</th>
                    <th>Size</th>
                    <th>Status</th>
                    <th>Chunks</th>
                    <th>Uploaded</th>
                    <th className="text-end">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {documents.map((doc) => (
                    <tr key={doc.id}>
                      <td>
                        <div className="d-flex align-items-center gap-2">
                          <FileText size={16} className="text-secondary flex-shrink-0" />
                          <span className="fw-medium text-body text-truncate" style={{ maxWidth: '240px' }} title={doc.originalFilename || doc.filename}>
                            {doc.originalFilename || doc.filename}
                          </span>
                        </div>
                      </td>
                      <td className="small text-secondary font-monospace">
                        {doc.contentType ? doc.contentType.split('/').pop() : 'txt'}
                      </td>
                      <td className="small text-secondary font-monospace">
                        {formatBytes(doc.sizeBytes)}
                      </td>
                      <td>{renderStatusBadge(doc.status)}</td>
                      <td className="small font-monospace">
                        {doc.chunkCount !== undefined ? doc.chunkCount : '—'}
                      </td>
                      <td className="small text-muted font-monospace">
                        {new Date(doc.createdAt).toLocaleDateString()}
                      </td>
                      <td className="text-end">
                        <div className="d-inline-flex align-items-center gap-1">
                          <button
                            type="button"
                            className="btn btn-sm btn-ghost p-1 text-secondary"
                            onClick={() => handleViewContent(doc)}
                            title="Inspect content & chunks"
                          >
                            <Eye size={15} />
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-ghost p-1 text-secondary"
                            onClick={() => handleDownload(doc.id)}
                            title="Download original file"
                          >
                            <Download size={15} />
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-ghost p-1 text-secondary"
                            onClick={() => handleReprocess(doc.id)}
                            title="Reprocess text & embeddings"
                          >
                            <RotateCw size={15} />
                          </button>
                          <button
                            type="button"
                            className="btn btn-sm btn-ghost p-1 text-danger"
                            onClick={() => handleDelete(doc.id, doc.originalFilename || doc.filename)}
                            title="Delete document"
                          >
                            <Trash2 size={15} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Content Inspector Modal */}
        {selectedDoc && (
          <div className="position-fixed inset-0 d-flex align-items-center justify-content-center p-3" style={{ background: 'rgba(0,0,0,0.5)', zIndex: 1060, top: 0, left: 0, right: 0, bottom: 0 }}>
            <div className="nexa-card-elevated w-100 overflow-hidden d-flex flex-column" style={{ maxWidth: '720px', maxHeight: '80vh' }}>
              <div className="p-3 border-bottom border-subtle d-flex align-items-center justify-content-between">
                <div>
                  <h6 className="fw-bold mb-0 text-body">{selectedDoc.originalFilename || selectedDoc.filename}</h6>
                  <span className="small text-muted font-monospace">{formatBytes(selectedDoc.sizeBytes)} • {selectedDoc.status}</span>
                </div>
                <button
                  type="button"
                  className="btn btn-sm btn-ghost p-1"
                  onClick={() => {
                    setSelectedDoc(null)
                    setDocContent(null)
                  }}
                >
                  <X size={18} />
                </button>
              </div>

              <div className="p-3 overflow-y-auto flex-grow-1">
                {loadingContent ? (
                  <div className="py-5 text-center text-secondary small">
                    <Loader2 size={24} className="spin mb-2 mx-auto" />
                    <div>Loading extracted text...</div>
                  </div>
                ) : (
                  <div>
                    <h6 className="small fw-semibold text-secondary mb-2">Extracted Content Preview:</h6>
                    <pre className="p-3 rounded bg-surface border border-subtle font-monospace small text-body overflow-x-auto" style={{ maxHeight: '250px', whiteSpace: 'pre-wrap' }}>
                      {docContent?.textContent || 'No plain text extracted.'}
                    </pre>

                    {docContent?.chunks && docContent.chunks.length > 0 && (
                      <div className="mt-3">
                        <h6 className="small fw-semibold text-secondary mb-2">Chunks ({docContent.chunks.length}):</h6>
                        <div className="d-flex flex-column gap-2" style={{ maxHeight: '200px', overflowY: 'auto' }}>
                          {docContent.chunks.map((ch, i) => (
                            <div key={i} className="p-2.5 rounded bg-surface border border-subtle small font-monospace">
                              <strong className="text-secondary d-block mb-1">Chunk #{ch.sequenceNumber || i + 1} ({ch.tokenCount || 0} tokens):</strong>
                              <span className="text-body">{ch.content}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>

              <div className="p-3 border-top border-subtle text-end">
                <button
                  type="button"
                  className="nexa-btn-secondary small"
                  onClick={() => {
                    setSelectedDoc(null)
                    setDocContent(null)
                  }}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
