import { request } from './apiClient.ts'
import type {
  WorkflowItem,
  WorkflowNode,
  WorkflowEdge,
  WorkflowExecution,
  WorkflowValidationResult
} from '../types/index.ts'

export interface CreateWorkflowPayload {
  readonly name: string
  readonly description?: string
  readonly triggerType?: string
  readonly nodes: readonly WorkflowNode[]
  readonly edges: readonly WorkflowEdge[]
}

export interface UpdateWorkflowPayload {
  readonly name: string
  readonly description?: string
  readonly triggerType?: string
  readonly nodes: readonly WorkflowNode[]
  readonly edges: readonly WorkflowEdge[]
  readonly isEnabled?: boolean
}

export const workflowService = {
  async getWorkflows(): Promise<WorkflowItem[]> {
    return request<WorkflowItem[]>('/api/v1/workflows')
  },

  async getWorkflow(id: string): Promise<WorkflowItem> {
    return request<WorkflowItem>(`/api/v1/workflows/${id}`)
  },

  async createWorkflow(payload: CreateWorkflowPayload): Promise<WorkflowItem> {
    return request<WorkflowItem>('/api/v1/workflows', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  },

  async updateWorkflow(id: string, payload: UpdateWorkflowPayload): Promise<WorkflowItem> {
    return request<WorkflowItem>(`/api/v1/workflows/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
  },

  async deleteWorkflow(id: string): Promise<void> {
    return request<void>(`/api/v1/workflows/${id}`, {
      method: 'DELETE',
    })
  },

  async validateWorkflow(nodes: readonly WorkflowNode[], edges: readonly WorkflowEdge[]): Promise<WorkflowValidationResult> {
    return request<WorkflowValidationResult>('/api/v1/workflows/validate', {
      method: 'POST',
      body: JSON.stringify({ name: 'validation', nodes, edges }),
    })
  },

  async executeWorkflow(
    id: string,
    inputPayload: string,
    preApprovedNodeIds?: readonly string[]
  ): Promise<WorkflowExecution> {
    return request<WorkflowExecution>(`/api/v1/workflows/${id}/execute`, {
      method: 'POST',
      body: JSON.stringify({
        inputPayload,
        preApprovedNodeIds: preApprovedNodeIds ? [...preApprovedNodeIds] : [],
      }),
    })
  },

  async getExecutions(workflowId: string): Promise<WorkflowExecution[]> {
    return request<WorkflowExecution[]>(`/api/v1/workflows/${workflowId}/executions`)
  },

  async getExecution(executionId: string): Promise<WorkflowExecution> {
    return request<WorkflowExecution>(`/api/v1/workflows/executions/${executionId}`)
  },

  async approveExecution(executionId: string): Promise<WorkflowExecution> {
    return request<WorkflowExecution>(`/api/v1/workflows/executions/${executionId}/approve`, {
      method: 'POST',
    })
  },
}
