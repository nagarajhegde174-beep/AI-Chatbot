export type Role = 'USER' | 'ADMIN'

export interface User {
  readonly userId: string
  readonly email: string
  readonly displayName: string
  readonly roles: readonly Role[]
}

export interface AuthTokens {
  readonly accessToken: string
  readonly refreshToken?: string
  readonly expiresIn?: number
  readonly user: User
}

export interface Conversation {
  readonly id: string
  readonly userId: string
  readonly title: string
  readonly model: string
  readonly systemPrompt?: string | null
  readonly ragEnabled: boolean
  readonly isArchived: boolean
  readonly createdAt: string
  readonly updatedAt: string
  readonly lastMessageAt?: string | null
  readonly messageCount: number
}

export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM'
export type MessageStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | 'INCOMPLETE'
export type FeedbackType = 'THUMBS_UP' | 'THUMBS_DOWN'

export interface Message {
  readonly id: string
  readonly conversationId: string
  readonly userId: string
  readonly role: MessageRole
  readonly content: string
  readonly model?: string | null
  readonly status: MessageStatus
  readonly promptTokens?: number | null
  readonly completionTokens?: number | null
  readonly totalTokens?: number | null
  readonly sequenceNumber: number
  readonly feedback?: FeedbackType | null
  readonly feedbackComment?: string | null
  readonly isRegenerated: boolean
  readonly originalMessageId?: string | null
  readonly createdAt: string
  readonly updatedAt: string
}

export type Theme = 'light' | 'dark' | 'system'

export type AppRoute =
  | 'home'
  | 'pricing'
  | 'faq'
  | 'login'
  | 'register'
  | 'verify-email'
  | 'forgot-password'
  | 'otp-verify'
  | 'reset-password'
  | 'change-password'
  | 'app'
  | 'chat'
  | 'documents'
  | 'workflows'
  | 'usage'
  | 'billing'
  | 'settings'
  | 'admin'

export type WorkflowNodeType =
  | 'TRIGGER'
  | 'DOCUMENT_INPUT'
  | 'TEXT_EXTRACTION'
  | 'AI_PROMPT'
  | 'SUMMARIZATION'
  | 'EXTRACTION'
  | 'DOCUMENT_SEARCH'
  | 'CONDITION'
  | 'OUTPUT'
  | 'NOTIFICATION'

export interface WorkflowNode {
  readonly id: string
  readonly type: WorkflowNodeType
  readonly label: string
  readonly config?: Record<string, unknown>
  readonly requiresApproval?: boolean
}

export interface WorkflowEdge {
  readonly id: string
  readonly source: string
  readonly target: string
  readonly conditionBranch?: string
}

export interface WorkflowItem {
  readonly id: string
  readonly userId: string
  readonly name: string
  readonly description?: string | null
  readonly triggerType: string
  readonly nodes: readonly WorkflowNode[]
  readonly edges: readonly WorkflowEdge[]
  readonly isEnabled: boolean
  readonly createdAt: string
  readonly updatedAt: string
}

export interface WorkflowExecutionStepLog {
  readonly nodeId: string
  readonly label: string
  readonly type: string
  readonly status: 'SUCCESS' | 'FAILED' | 'WAITING_APPROVAL'
  readonly outputPreview?: string
  readonly error?: string
  readonly durationMs?: number
  readonly timestamp: string
}

export interface WorkflowExecution {
  readonly id: string
  readonly workflowId: string
  readonly userId: string
  readonly status: 'PENDING' | 'RUNNING' | 'WAITING_APPROVAL' | 'COMPLETED' | 'FAILED'
  readonly currentStepIndex: number
  readonly inputPayload?: string | null
  readonly outputPayload?: string | null
  readonly stepLogs: readonly WorkflowExecutionStepLog[]
  readonly errorMessage?: string | null
  readonly requiresApproval: boolean
  readonly pendingApprovalNodeId?: string | null
  readonly startedAt: string
  readonly completedAt?: string | null
}

export interface WorkflowValidationResult {
  readonly valid: boolean
  readonly errors: readonly string[]
  readonly warnings: readonly string[]
}

export interface DocumentItem {
  readonly id: string
  readonly filename: string
  readonly originalFilename: string
  readonly contentType: string
  readonly sizeBytes: number
  readonly status: 'UPLOADED' | 'EXTRACTING' | 'CHUNKING' | 'EMBEDDING' | 'READY' | 'FAILED'
  readonly chunkCount?: number
  readonly errorMessage?: string | null
  readonly createdAt: string
  readonly updatedAt: string
}

export interface UserUsageData {
  readonly aiRequests: number
  readonly tokenUsage: {
    readonly promptTokens: number
    readonly completionTokens: number
    readonly totalTokens: number
  }
  readonly documentCount: number
  readonly storageUsageBytes: number
  readonly storageUsageFormatted: string
  readonly dailyUsage: ReadonlyArray<{
    readonly date: string
    readonly requests: number
    readonly tokens: number
    readonly estimatedCostUsd: number
  }>
  readonly monthlyUsage: {
    readonly currentMonthTokens: number
    readonly currentMonthRequests: number
    readonly currentMonthCostUsd: number
  }
  readonly modelUsage: ReadonlyArray<{
    readonly model: string
    readonly provider: string
    readonly requests: number
    readonly tokens: number
    readonly estimatedCostUsd: number
  }>
  readonly remainingQuota: number
  readonly subscriptionStatus: {
    readonly tier: string
    readonly tokenQuota: number
    readonly tokensUsed: number
    readonly status: string
  }
}

export interface AdminOverviewData {
  readonly totalUsers: number
  readonly activeUsers: number
  readonly totalAiRequests: number
  readonly successfulRequests: number
  readonly failedRequests: number
  readonly totalTokens: number
  readonly totalErrors: number
  readonly errorRatePercent: number
  readonly subscriptionStatistics: Record<string, number>
  readonly totalEstimatedAiCostUsd: number
}
