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

export type Theme = 'light' | 'dark'

export type AppRoute = 'login' | 'register' | 'forgot-password' | 'chat'
