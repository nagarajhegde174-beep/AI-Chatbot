import { useState, useEffect } from 'react'
import {
  Play, Plus, Trash2, CheckCircle2, AlertTriangle, Clock, ArrowRight,
  ShieldCheck, FileText, Sparkles, Bell, ArrowDown,
  RefreshCw, Check, Edit3, HelpCircle, Layers, ArrowLeft
} from 'lucide-react'
import { workflowService } from '../../services/workflowService.ts'
import type {
  WorkflowItem,
  WorkflowNode,
  WorkflowEdge,
  WorkflowNodeType,
  WorkflowExecution,
  WorkflowValidationResult
} from '../../types/index.ts'

const DEFAULT_TEMPLATE_NODES: readonly WorkflowNode[] = [
  { id: 'node-trigger', type: 'TRIGGER', label: 'Manual Trigger', config: { triggerType: 'MANUAL' } },
  { id: 'node-doc', type: 'DOCUMENT_INPUT', label: 'Read Document', config: { documentId: 'quarterly-report.pdf' } },
  { id: 'node-extract', type: 'TEXT_EXTRACTION', label: 'Extract Text Content', config: { cleanWhitespace: true } },
  { id: 'node-summarize', type: 'SUMMARIZATION', label: 'Summarize Key Findings', config: { length: 'CONCISE' } },
  { id: 'node-ai', type: 'AI_PROMPT', label: 'Generate Strategic Report', config: { promptTemplate: 'Produce an executive briefing from: {input}' }, requiresApproval: true },
  { id: 'node-output', type: 'OUTPUT', label: 'Format Markdown Report', config: { format: 'MARKDOWN' } },
  { id: 'node-notify', type: 'NOTIFICATION', label: 'Dispatch In-App Notification', config: { channel: 'IN_APP' } }
]

const NODE_TYPE_INFO: Record<WorkflowNodeType, { label: string; icon: typeof Sparkles; color: string; desc: string }> = {
  TRIGGER: { label: 'Trigger', icon: Play, color: '#6E56CF', desc: 'Starting event for workflow execution' },
  DOCUMENT_INPUT: { label: 'Document Input', icon: FileText, color: '#3B82F6', desc: 'Ingest PDF, text file, or knowledge document' },
  TEXT_EXTRACTION: { label: 'Text Extraction', icon: Layers, color: '#10B981', desc: 'Extract clean text and structure from raw document' },
  AI_PROMPT: { label: 'AI Prompt', icon: Sparkles, color: '#8B5CF6', desc: 'Submit context to multi-model LLM inference' },
  SUMMARIZATION: { label: 'Summarization', icon: Sparkles, color: '#EC4899', desc: 'Context-compressed summary via AI Service' },
  EXTRACTION: { label: 'Data Extraction', icon: Layers, color: '#F59E0B', desc: 'Extract structured JSON entities from input text' },
  DOCUMENT_SEARCH: { label: 'Document Search', icon: FileText, color: '#06B6D4', desc: 'Semantic search across indexed RAG documents' },
  CONDITION: { label: 'Condition Rule', icon: HelpCircle, color: '#EAB308', desc: 'Branch evaluation based on text pattern or value' },
  OUTPUT: { label: 'Result Output', icon: CheckCircle2, color: '#14B8A6', desc: 'Final formatted report or data output' },
  NOTIFICATION: { label: 'Notification', icon: Bell, color: '#6366F1', desc: 'Dispatches alert or completion webhook' },
}

export function WorkflowBuilderView() {
  const [workflows, setWorkflows] = useState<WorkflowItem[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  // Builder / Editor State
  const [activeWorkflow, setActiveWorkflow] = useState<WorkflowItem | null>(null)
  const [isEditing, setIsEditing] = useState(false)
  const [builderName, setBuilderName] = useState('')
  const [builderDesc, setBuilderDesc] = useState('')
  const [builderTrigger, setBuilderTrigger] = useState('MANUAL')
  const [builderNodes, setBuilderNodes] = useState<WorkflowNode[]>([])
  const [builderEdges, setBuilderEdges] = useState<WorkflowEdge[]>([])

  // Node Config Modal
  const [editingNode, setEditingNode] = useState<WorkflowNode | null>(null)
  const [editingNodeIndex, setEditingNodeIndex] = useState<number>(-1)

  // Validation State
  const [validation, setValidation] = useState<WorkflowValidationResult | null>(null)

  // Execution Modal State
  const [executingWorkflow, setExecutingWorkflow] = useState<WorkflowItem | null>(null)
  const [inputPayload, setInputPayload] = useState('Analyze quarterly growth performance and produce executive summary.')
  const [activeExecution, setActiveExecution] = useState<WorkflowExecution | null>(null)
  const [executing, setExecuting] = useState(false)

  // History State
  const [historyWorkflow, setHistoryWorkflow] = useState<WorkflowItem | null>(null)
  const [executionsList, setExecutionsList] = useState<WorkflowExecution[]>([])

  useEffect(() => {
    loadWorkflows()
  }, [])

  const loadWorkflows = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await workflowService.getWorkflows()
      setWorkflows(data)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load workflows')
    } finally {
      setLoading(false)
    }
  }

  const handleStartCreate = () => {
    setActiveWorkflow(null)
    setBuilderName('New AI Workflow')
    setBuilderDesc('Custom multi-step automated agentic pipeline')
    setBuilderTrigger('MANUAL')
    setBuilderNodes([...DEFAULT_TEMPLATE_NODES])
    setBuilderEdges([])
    setValidation(null)
    setIsEditing(true)
  }

  const handleStartEdit = (wf: WorkflowItem) => {
    setActiveWorkflow(wf)
    setBuilderName(wf.name)
    setBuilderDesc(wf.description || '')
    setBuilderTrigger(wf.triggerType)
    setBuilderNodes([...wf.nodes])
    setBuilderEdges([...wf.edges])
    setValidation(null)
    setIsEditing(true)
  }

  const handleSaveWorkflow = async () => {
    if (!builderName.trim()) {
      setError('Workflow name is required.')
      return
    }

    try {
      setLoading(true)
      setError(null)

      if (activeWorkflow) {
        await workflowService.updateWorkflow(activeWorkflow.id, {
          name: builderName,
          description: builderDesc,
          triggerType: builderTrigger,
          nodes: builderNodes,
          edges: builderEdges,
        })
        setSuccessMsg('Workflow updated successfully.')
      } else {
        await workflowService.createWorkflow({
          name: builderName,
          description: builderDesc,
          triggerType: builderTrigger,
          nodes: builderNodes,
          edges: builderEdges,
        })
        setSuccessMsg('Workflow created successfully.')
      }

      setIsEditing(false)
      await loadWorkflows()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to save workflow')
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteWorkflow = async (id: string) => {
    if (!confirm('Are you sure you want to delete this workflow?')) return
    try {
      await workflowService.deleteWorkflow(id)
      setSuccessMsg('Workflow deleted.')
      await loadWorkflows()
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to delete workflow')
    }
  }

  const handleValidate = async () => {
    try {
      setError(null)
      const result = await workflowService.validateWorkflow(builderNodes, builderEdges)
      setValidation(result)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Validation failed')
    }
  }

  const handleAddNode = (type: WorkflowNodeType) => {
    const info = NODE_TYPE_INFO[type]
    const newNode: WorkflowNode = {
      id: `node-${Date.now()}`,
      type,
      label: info.label,
      config: type === 'AI_PROMPT' ? { promptTemplate: 'Process {input}' } : {},
      requiresApproval: type === 'AI_PROMPT'
    }
    setBuilderNodes(prev => [...prev, newNode])
  }

  const handleRemoveNode = (index: number) => {
    setBuilderNodes(prev => prev.filter((_, i) => i !== index))
  }

  const handleMoveNode = (index: number, direction: 'up' | 'down') => {
    const target = direction === 'up' ? index - 1 : index + 1
    if (target < 0 || target >= builderNodes.length) return
    const updated = [...builderNodes]
    const current = updated[index]
    const targetNode = updated[target]
    if (current && targetNode) {
      updated[index] = targetNode
      updated[target] = current
      setBuilderNodes(updated)
    }
  }

  const handleOpenNodeModal = (node: WorkflowNode, index: number) => {
    setEditingNode({ ...node })
    setEditingNodeIndex(index)
  }

  const handleSaveNodeConfig = () => {
    if (!editingNode || editingNodeIndex < 0) return
    setBuilderNodes(prev => {
      const updated = [...prev]
      updated[editingNodeIndex] = editingNode
      return updated
    })
    setEditingNode(null)
    setEditingNodeIndex(-1)
  }

  // Execution Handlers
  const handleOpenExecuteModal = (wf: WorkflowItem) => {
    setExecutingWorkflow(wf)
    setActiveExecution(null)
    setInputPayload('Sample financial analysis document: Revenue up 18% YoY. Operating costs optimized.')
  }

  const handleRunExecution = async () => {
    if (!executingWorkflow) return
    try {
      setExecuting(true)
      setError(null)
      const execution = await workflowService.executeWorkflow(executingWorkflow.id, inputPayload)
      setActiveExecution(execution)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Execution failed')
    } finally {
      setExecuting(false)
    }
  }

  const handleApproveStep = async (executionId: string) => {
    try {
      setExecuting(true)
      const resumed = await workflowService.approveExecution(executionId)
      setActiveExecution(resumed)
      setSuccessMsg('Step approved. Workflow completed.')
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Approval failed')
    } finally {
      setExecuting(false)
    }
  }

  const handleOpenHistory = async (wf: WorkflowItem) => {
    setHistoryWorkflow(wf)
    try {
      setLoading(true)
      const list = await workflowService.getExecutions(wf.id)
      setExecutionsList(list)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load execution history')
    } finally {
      setLoading(false)
    }
  }

  // ---------------------------------------------------------------------------
  // RENDER: BUILDER / EDITOR VIEW
  // ---------------------------------------------------------------------------
  if (isEditing) {
    return (
      <div className="d-flex flex-column h-100 overflow-y-auto p-4 bg-base text-body">
        {/* Top Header Bar */}
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 pb-3 mb-4 border-bottom border-subtle">
          <div className="d-flex align-items-center gap-3">
            <button
              type="button"
              className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1.5"
              onClick={() => setIsEditing(false)}
            >
              <ArrowLeft size={16} />
              <span>Back</span>
            </button>
            <div>
              <input
                type="text"
                className="form-control form-control-lg fw-bold bg-transparent border-0 px-0 shadow-none text-body"
                value={builderName}
                onChange={e => setBuilderName(e.target.value)}
                placeholder="Workflow Name"
              />
              <input
                type="text"
                className="form-control form-control-sm text-secondary bg-transparent border-0 px-0 shadow-none"
                value={builderDesc}
                onChange={e => setBuilderDesc(e.target.value)}
                placeholder="Add workflow description..."
              />
            </div>
          </div>

          <div className="d-flex align-items-center gap-2">
            <button
              type="button"
              className="btn btn-outline-secondary btn-sm d-flex align-items-center gap-1.5"
              onClick={handleValidate}
            >
              <ShieldCheck size={16} />
              <span>Validate</span>
            </button>
            <button
              type="button"
              className="nexa-btn-primary btn-sm d-flex align-items-center gap-1.5"
              onClick={handleSaveWorkflow}
            >
              <Check size={16} />
              <span>Save Workflow</span>
            </button>
          </div>
        </div>

        {/* Validation Result Banner */}
        {validation && (
          <div className={`alert ${validation.valid ? 'alert-success' : 'alert-danger'} d-flex align-items-start gap-2 mb-4`}>
            {validation.valid ? <CheckCircle2 size={18} className="mt-0.5 text-success" /> : <AlertTriangle size={18} className="mt-0.5 text-danger" />}
            <div>
              <div className="fw-semibold">
                {validation.valid ? 'Workflow structure is valid and ready to execute!' : 'Workflow has configuration errors:'}
              </div>
              {validation.errors.length > 0 && (
                <ul className="mb-0 mt-1 small ps-3">
                  {validation.errors.map((err, i) => <li key={i}>{err}</li>)}
                </ul>
              )}
            </div>
          </div>
        )}

        {/* Workflow Concept Banner: Trigger -> Action -> Action -> Result */}
        <div className="card p-3 mb-4 bg-surface-card border border-subtle rounded-3">
          <div className="d-flex align-items-center gap-2 text-secondary small fw-medium mb-2">
            <Sparkles size={16} className="text-primary" />
            <span>Workflow Concept Pipeline</span>
          </div>
          <div className="d-flex flex-wrap align-items-center gap-2 small">
            <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-2.5 py-1.5">Trigger</span>
            <ArrowRight size={14} className="text-secondary" />
            <span className="badge bg-body-tertiary text-body border border-subtle px-2.5 py-1.5">Read Document</span>
            <ArrowRight size={14} className="text-secondary" />
            <span className="badge bg-body-tertiary text-body border border-subtle px-2.5 py-1.5">Summarize</span>
            <ArrowRight size={14} className="text-secondary" />
            <span className="badge bg-body-tertiary text-body border border-subtle px-2.5 py-1.5">Extract Information</span>
            <ArrowRight size={14} className="text-secondary" />
            <span className="badge bg-body-tertiary text-body border border-subtle px-2.5 py-1.5">Generate Report</span>
            <ArrowRight size={14} className="text-secondary" />
            <span className="badge bg-success-subtle text-success border border-success-subtle px-2.5 py-1.5">Notification</span>
          </div>
        </div>

        {/* Main Builder Grid */}
        <div className="row g-4">
          {/* Left Column: Visual Pipeline Canvas */}
          <div className="col-12 col-lg-8">
            <div className="d-flex align-items-center justify-content-between mb-3">
              <h5 className="fw-bold mb-0">Pipeline Steps ({builderNodes.length})</h5>
              <span className="small text-secondary">Executes sequentially from top to bottom</span>
            </div>

            <div className="d-flex flex-column gap-3">
              {builderNodes.map((node, idx) => {
                const info = NODE_TYPE_INFO[node.type] || NODE_TYPE_INFO.TRIGGER
                const IconComp = info.icon

                return (
                  <div key={node.id} className="d-flex flex-column align-items-center w-100">
                    <div className="card w-100 bg-surface-card border border-subtle rounded-3 shadow-sm hover-border transition">
                      <div className="card-body p-3">
                        <div className="d-flex align-items-center justify-content-between gap-2">
                          <div className="d-flex align-items-center gap-3">
                            <span
                              className="d-inline-flex align-items-center justify-content-center rounded-2 text-white"
                              style={{ width: '36px', height: '36px', backgroundColor: info.color }}
                            >
                              <IconComp size={18} />
                            </span>
                            <div>
                              <div className="d-flex align-items-center gap-2">
                                <span className="small text-secondary fw-semibold">Step {idx + 1}:</span>
                                <span className="fw-bold text-body">{node.label}</span>
                                {node.requiresApproval && (
                                  <span className="badge bg-warning-subtle text-warning border border-warning-subtle d-inline-flex align-items-center gap-1">
                                    <ShieldCheck size={12} />
                                    <span>Approval Required</span>
                                  </span>
                                )}
                              </div>
                              <span className="small text-secondary">{info.desc}</span>
                            </div>
                          </div>

                          <div className="d-flex align-items-center gap-1">
                            <button
                              type="button"
                              className="btn btn-sm btn-ghost p-1.5 text-secondary"
                              onClick={() => handleMoveNode(idx, 'up')}
                              disabled={idx === 0}
                              title="Move step up"
                            >
                              <ArrowDown size={14} style={{ transform: 'rotate(180deg)' }} />
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm btn-ghost p-1.5 text-secondary"
                              onClick={() => handleMoveNode(idx, 'down')}
                              disabled={idx === builderNodes.length - 1}
                              title="Move step down"
                            >
                              <ArrowDown size={14} />
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-secondary p-1.5 text-body"
                              onClick={() => handleOpenNodeModal(node, idx)}
                              title="Configure parameters"
                            >
                              <Edit3 size={14} />
                            </button>
                            <button
                              type="button"
                              className="btn btn-sm btn-outline-danger p-1.5"
                              onClick={() => handleRemoveNode(idx)}
                              title="Remove step"
                            >
                              <Trash2 size={14} />
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>

                    {idx < builderNodes.length - 1 && (
                      <div className="my-1.5 text-secondary d-flex align-items-center justify-content-center">
                        <ArrowDown size={18} />
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Right Column: Add Node Palette */}
          <div className="col-12 col-lg-4">
            <div className="card p-3 bg-surface-card border border-subtle rounded-3 sticky-top" style={{ top: '1rem' }}>
              <h6 className="fw-bold mb-3 d-flex align-items-center gap-2">
                <Plus size={16} className="text-primary" />
                <span>Add Node to Pipeline</span>
              </h6>
              <div className="d-flex flex-column gap-2">
                {(Object.keys(NODE_TYPE_INFO) as WorkflowNodeType[]).map(type => {
                  const info = NODE_TYPE_INFO[type]
                  const Icon = info.icon

                  return (
                    <button
                      key={type}
                      type="button"
                      className="btn btn-outline-secondary text-start p-2.5 d-flex align-items-center justify-content-between rounded-2 hover-bg-subtle"
                      onClick={() => handleAddNode(type)}
                    >
                      <div className="d-flex align-items-center gap-2.5">
                        <span
                          className="d-inline-flex align-items-center justify-content-center rounded-2 text-white"
                          style={{ width: '28px', height: '28px', backgroundColor: info.color }}
                        >
                          <Icon size={14} />
                        </span>
                        <div>
                          <div className="fw-semibold small text-body">{info.label}</div>
                          <div className="text-secondary small" style={{ fontSize: '0.75rem' }}>{info.desc}</div>
                        </div>
                      </div>
                      <Plus size={14} className="text-secondary" />
                    </button>
                  )
                })}
              </div>
            </div>
          </div>
        </div>

        {/* Node Config Modal */}
        {editingNode && (
          <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content bg-surface-card border border-subtle text-body shadow-lg">
                <div className="modal-header border-bottom border-subtle">
                  <h6 className="modal-title fw-bold">Configure {editingNode.type} Node</h6>
                  <button type="button" className="btn-close" onClick={() => setEditingNode(null)} />
                </div>
                <div className="modal-body">
                  <div className="mb-3">
                    <label className="form-label small fw-semibold">Step Label</label>
                    <input
                      type="text"
                      className="form-control"
                      value={editingNode.label}
                      onChange={e => setEditingNode({ ...editingNode, label: e.target.value })}
                    />
                  </div>

                  {editingNode.type === 'AI_PROMPT' && (
                    <div className="mb-3">
                      <label className="form-label small fw-semibold">Prompt Template</label>
                      <textarea
                        className="form-control font-monospace small"
                        rows={3}
                        value={(editingNode.config?.promptTemplate as string) || ''}
                        onChange={e => setEditingNode({
                          ...editingNode,
                          config: { ...editingNode.config, promptTemplate: e.target.value }
                        })}
                        placeholder="Enter prompt with {input} placeholder..."
                      />
                    </div>
                  )}

                  {editingNode.type === 'DOCUMENT_INPUT' && (
                    <div className="mb-3">
                      <label className="form-label small fw-semibold">Document Reference ID / Filename</label>
                      <input
                        type="text"
                        className="form-control"
                        value={(editingNode.config?.documentId as string) || ''}
                        onChange={e => setEditingNode({
                          ...editingNode,
                          config: { ...editingNode.config, documentId: e.target.value }
                        })}
                        placeholder="e.g. quarterly-report.pdf"
                      />
                    </div>
                  )}

                  {editingNode.type === 'SUMMARIZATION' && (
                    <div className="mb-3">
                      <label className="form-label small fw-semibold">Summary Length</label>
                      <select
                        className="form-select"
                        value={(editingNode.config?.length as string) || 'CONCISE'}
                        onChange={e => setEditingNode({
                          ...editingNode,
                          config: { ...editingNode.config, length: e.target.value }
                        })}
                      >
                        <option value="CONCISE">Concise (1 paragraph)</option>
                        <option value="DETAILED">Detailed (Bullet points)</option>
                        <option value="EXECUTIVE">Executive Summary</option>
                      </select>
                    </div>
                  )}

                  <div className="form-check form-switch p-0 mt-3 pt-3 border-top border-subtle d-flex align-items-center justify-content-between">
                    <div>
                      <label className="form-check-label fw-semibold small d-block" htmlFor="approvalSwitch">
                        Requires Human Approval
                      </label>
                      <span className="small text-secondary d-block">
                        Workflow pauses before running this step until explicitly approved.
                      </span>
                    </div>
                    <input
                      className="form-check-input ms-2"
                      type="checkbox"
                      id="approvalSwitch"
                      checked={editingNode.requiresApproval || false}
                      onChange={e => setEditingNode({ ...editingNode, requiresApproval: e.target.checked })}
                    />
                  </div>
                </div>
                <div className="modal-footer border-top border-subtle">
                  <button type="button" className="btn btn-secondary btn-sm" onClick={() => setEditingNode(null)}>
                    Cancel
                  </button>
                  <button type="button" className="nexa-btn-primary btn-sm" onClick={handleSaveNodeConfig}>
                    Apply Changes
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    )
  }

  // ---------------------------------------------------------------------------
  // RENDER: WORKFLOW LIST / DASHBOARD
  // ---------------------------------------------------------------------------
  return (
    <div className="d-flex flex-column h-100 overflow-y-auto p-4 bg-base text-body">
      {/* Top Header */}
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-3 pb-3 mb-4 border-bottom border-subtle">
        <div>
          <div className="d-flex align-items-center gap-2">
            <h4 className="fw-bold mb-0">Workflow Builder & Agentic AI</h4>
            <span className="badge bg-primary-subtle text-primary border border-primary-subtle">Phase 11</span>
          </div>
          <p className="text-secondary small mb-0 mt-1">
            Build, validate, and execute automated multi-step AI pipelines with controlled tool permissions and human approval.
          </p>
        </div>

        <button
          type="button"
          className="nexa-btn-primary d-flex align-items-center gap-2"
          onClick={handleStartCreate}
        >
          <Plus size={16} />
          <span>Create Workflow</span>
        </button>
      </div>

      {/* Notification Messages */}
      {error && (
        <div className="alert alert-danger d-flex align-items-center justify-content-between py-2 px-3 mb-4">
          <div className="d-flex align-items-center gap-2">
            <AlertTriangle size={16} />
            <span className="small">{error}</span>
          </div>
          <button type="button" className="btn-close btn-close-white small" onClick={() => setError(null)} />
        </div>
      )}

      {successMsg && (
        <div className="alert alert-success d-flex align-items-center justify-content-between py-2 px-3 mb-4">
          <div className="d-flex align-items-center gap-2">
            <CheckCircle2 size={16} />
            <span className="small">{successMsg}</span>
          </div>
          <button type="button" className="btn-close small" onClick={() => setSuccessMsg(null)} />
        </div>
      )}

      {/* Workflows Grid */}
      {loading ? (
        <div className="d-flex align-items-center justify-content-center p-5 text-secondary">
          <RefreshCw size={24} className="spin me-2" />
          <span>Loading workflows...</span>
        </div>
      ) : workflows.length === 0 ? (
        <div className="card p-5 text-center bg-surface-card border border-subtle rounded-3 my-4">
          <div className="d-inline-flex p-3 rounded-circle bg-primary-subtle text-primary mx-auto mb-3">
            <Layers size={36} />
          </div>
          <h5 className="fw-bold">No Workflows Yet</h5>
          <p className="text-secondary small max-w-md mx-auto mb-4">
            Create automated AI workflows that chain triggers, document analysis, multi-model inferences, and notifications.
          </p>
          <div>
            <button type="button" className="nexa-btn-primary" onClick={handleStartCreate}>
              <Plus size={16} className="me-1.5" />
              <span>Create First Workflow</span>
            </button>
          </div>
        </div>
      ) : (
        <div className="row g-3">
          {workflows.map(wf => (
            <div key={wf.id} className="col-12 col-md-6 col-xl-4">
              <div className="card h-100 bg-surface-card border border-subtle rounded-3 shadow-sm hover-border transition">
                <div className="card-body p-4 d-flex flex-column justify-content-between">
                  <div>
                    <div className="d-flex align-items-center justify-content-between mb-2">
                      <span className="badge bg-primary-subtle text-primary border border-primary-subtle">
                        {wf.triggerType}
                      </span>
                      <span className="small text-secondary">
                        {wf.nodes.length} node{wf.nodes.length === 1 ? '' : 's'}
                      </span>
                    </div>

                    <h5 className="fw-bold text-body mb-1">{wf.name}</h5>
                    <p className="text-secondary small mb-3 text-truncate-2">
                      {wf.description || 'No description provided.'}
                    </p>

                    {/* Nodes flow summary */}
                    <div className="d-flex flex-wrap align-items-center gap-1.5 mb-3">
                      {wf.nodes.slice(0, 4).map((node, i) => (
                        <span key={i} className="badge bg-body-tertiary text-secondary border border-subtle small">
                          {node.label}
                        </span>
                      ))}
                      {wf.nodes.length > 4 && (
                        <span className="small text-secondary">+{wf.nodes.length - 4} more</span>
                      )}
                    </div>
                  </div>

                  <div className="d-flex align-items-center justify-content-between pt-3 border-top border-subtle gap-2">
                    <div className="d-flex align-items-center gap-1">
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
                        onClick={() => handleStartEdit(wf)}
                        title="Edit workflow nodes"
                      >
                        <Edit3 size={14} />
                        <span>Edit</span>
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-secondary d-flex align-items-center gap-1"
                        onClick={() => handleOpenHistory(wf)}
                        title="View past execution history"
                      >
                        <Clock size={14} />
                        <span>History</span>
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-outline-danger p-1.5"
                        onClick={() => handleDeleteWorkflow(wf.id)}
                        title="Delete workflow"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>

                    <button
                      type="button"
                      className="nexa-btn-primary btn-sm d-flex align-items-center gap-1"
                      onClick={() => handleOpenExecuteModal(wf)}
                    >
                      <Play size={14} />
                      <span>Execute</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Execution Modal */}
      {executingWorkflow && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.6)' }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content bg-surface-card border border-subtle text-body shadow-lg">
              <div className="modal-header border-bottom border-subtle">
                <div className="d-flex align-items-center gap-2">
                  <Play size={18} className="text-primary" />
                  <h6 className="modal-title fw-bold">Execute: {executingWorkflow.name}</h6>
                </div>
                <button type="button" className="btn-close" onClick={() => setExecutingWorkflow(null)} />
              </div>

              <div className="modal-body p-4">
                {/* Input Payload */}
                <div className="mb-4">
                  <label className="form-label small fw-semibold">Input Payload</label>
                  <textarea
                    className="form-control font-monospace small"
                    rows={3}
                    value={inputPayload}
                    onChange={e => setInputPayload(e.target.value)}
                    placeholder="Enter input context or document text for workflow..."
                    disabled={executing}
                  />
                  <div className="form-text small text-secondary">
                    Initial data passed to the first step of the pipeline.
                  </div>
                </div>

                {/* Live Execution Progress */}
                {activeExecution && (
                  <div className="border border-subtle rounded-3 p-3 bg-base mb-4">
                    <div className="d-flex align-items-center justify-content-between mb-3">
                      <span className="fw-bold small">Execution Status:</span>
                      <span className={`badge ${
                        activeExecution.status === 'COMPLETED' ? 'bg-success-subtle text-success border border-success-subtle' :
                        activeExecution.status === 'WAITING_APPROVAL' ? 'bg-warning-subtle text-warning border border-warning-subtle' :
                        activeExecution.status === 'FAILED' ? 'bg-danger-subtle text-danger border border-danger-subtle' :
                        'bg-primary-subtle text-primary border border-primary-subtle'
                      }`}>
                        {activeExecution.status}
                      </span>
                    </div>

                    {/* Pending Human Approval Alert */}
                    {activeExecution.status === 'WAITING_APPROVAL' && (
                      <div className="alert alert-warning d-flex align-items-center justify-content-between p-3 mb-3">
                        <div className="d-flex align-items-center gap-2">
                          <ShieldCheck size={20} className="text-warning" />
                          <div>
                            <div className="fw-semibold small">Human Approval Checkpoint</div>
                            <div className="small">
                              Step <strong>{activeExecution.pendingApprovalNodeId}</strong> requires explicit approval before running.
                            </div>
                          </div>
                        </div>
                        <button
                          type="button"
                          className="btn btn-warning btn-sm fw-semibold"
                          onClick={() => handleApproveStep(activeExecution.id)}
                          disabled={executing}
                        >
                          Approve & Proceed
                        </button>
                      </div>
                    )}

                    {/* Step Execution Logs */}
                    <div className="d-flex flex-column gap-2">
                      {activeExecution.stepLogs.map((log, idx) => (
                        <div key={idx} className="p-2.5 rounded-2 bg-surface-card border border-subtle small d-flex align-items-center justify-content-between">
                          <div className="d-flex align-items-center gap-2">
                            {log.status === 'SUCCESS' ? (
                              <CheckCircle2 size={16} className="text-success" />
                            ) : log.status === 'WAITING_APPROVAL' ? (
                              <Clock size={16} className="text-warning" />
                            ) : (
                              <AlertTriangle size={16} className="text-danger" />
                            )}
                            <span className="fw-semibold text-body">{log.label}</span>
                            <span className="badge bg-body-tertiary text-secondary">{log.type}</span>
                          </div>
                          <div className="d-flex align-items-center gap-3 text-secondary">
                            {log.durationMs !== undefined && <span>{log.durationMs}ms</span>}
                            <span className="fw-medium text-body">{log.status}</span>
                          </div>
                        </div>
                      ))}
                    </div>

                    {/* Final Output Preview */}
                    {activeExecution.outputPayload && activeExecution.status === 'COMPLETED' && (
                      <div className="mt-3 pt-3 border-top border-subtle">
                        <div className="fw-semibold small text-secondary mb-1">Final Result Output:</div>
                        <pre className="p-3 bg-surface-card rounded-2 border border-subtle small text-body overflow-x-auto mb-0">
                          {activeExecution.outputPayload}
                        </pre>
                      </div>
                    )}

                    {/* Error Message */}
                    {activeExecution.errorMessage && (
                      <div className="alert alert-danger mt-3 mb-0 small">
                        <strong>Error:</strong> {activeExecution.errorMessage}
                      </div>
                    )}
                  </div>
                )}
              </div>

              <div className="modal-footer border-top border-subtle">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setExecutingWorkflow(null)}>
                  Close
                </button>
                <button
                  type="button"
                  className="nexa-btn-primary btn-sm d-flex align-items-center gap-2"
                  onClick={handleRunExecution}
                  disabled={executing}
                >
                  {executing ? <RefreshCw size={14} className="spin" /> : <Play size={14} />}
                  <span>{executing ? 'Executing Pipeline...' : 'Run Workflow'}</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Execution History Modal */}
      {historyWorkflow && (
        <div className="modal show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.6)' }}>
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content bg-surface-card border border-subtle text-body shadow-lg">
              <div className="modal-header border-bottom border-subtle">
                <div className="d-flex align-items-center gap-2">
                  <Clock size={18} className="text-secondary" />
                  <h6 className="modal-title fw-bold">Execution History: {historyWorkflow.name}</h6>
                </div>
                <button type="button" className="btn-close" onClick={() => setHistoryWorkflow(null)} />
              </div>
              <div className="modal-body p-4">
                {executionsList.length === 0 ? (
                  <div className="text-center p-4 text-secondary small">
                    No recorded executions for this workflow yet.
                  </div>
                ) : (
                  <div className="table-responsive">
                    <table className="table table-hover table-borderless small align-middle mb-0">
                      <thead>
                        <tr className="border-bottom border-subtle text-secondary">
                          <th>Run ID</th>
                          <th>Status</th>
                          <th>Started</th>
                          <th>Steps</th>
                          <th>Completed</th>
                        </tr>
                      </thead>
                      <tbody>
                        {executionsList.map(item => (
                          <tr key={item.id} className="border-bottom border-subtle">
                            <td className="font-monospace text-secondary">{item.id.substring(0, 8)}...</td>
                            <td>
                              <span className={`badge ${
                                item.status === 'COMPLETED' ? 'bg-success-subtle text-success' :
                                item.status === 'WAITING_APPROVAL' ? 'bg-warning-subtle text-warning' :
                                item.status === 'FAILED' ? 'bg-danger-subtle text-danger' :
                                'bg-primary-subtle text-primary'
                              }`}>
                                {item.status}
                              </span>
                            </td>
                            <td>{new Date(item.startedAt).toLocaleString()}</td>
                            <td>{item.stepLogs.length} steps</td>
                            <td>{item.completedAt ? new Date(item.completedAt).toLocaleTimeString() : 'In Progress'}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
              <div className="modal-footer border-top border-subtle">
                <button type="button" className="btn btn-secondary btn-sm" onClick={() => setHistoryWorkflow(null)}>
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
