import { request } from './apiClient.ts'
import type { Conversation, FeedbackType, Message } from '../types/index.ts'

export const chatService = {
  async listConversations(archived?: boolean): Promise<Conversation[]> {
    const q = archived !== undefined ? `?archived=${archived}` : ''
    return request<Conversation[]>(`/api/v1/chat/conversations${q}`)
  },

  async createConversation(title?: string, model?: string): Promise<Conversation> {
    return request<Conversation>('/api/v1/chat/conversations', {
      method: 'POST',
      body: JSON.stringify({ title, model }),
    })
  },

  async getConversation(id: string): Promise<Conversation> {
    return request<Conversation>(`/api/v1/chat/conversations/${id}`)
  },

  async updateConversation(id: string, updates: { title?: string; model?: string; isArchived?: boolean }): Promise<Conversation> {
    return request<Conversation>(`/api/v1/chat/conversations/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(updates),
    })
  },

  async deleteConversation(id: string): Promise<void> {
    await request<void>(`/api/v1/chat/conversations/${id}`, {
      method: 'DELETE',
    })
  },

  async setArchiveStatus(id: string, archive: boolean): Promise<Conversation> {
    const action = archive ? 'archive' : 'unarchive'
    return request<Conversation>(`/api/v1/chat/conversations/${id}/${action}`, {
      method: 'POST',
    })
  },

  async searchConversations(query: string): Promise<Conversation[]> {
    return request<Conversation[]>(`/api/v1/chat/conversations/search?q=${encodeURIComponent(query)}`)
  },

  async listMessages(conversationId: string): Promise<Message[]> {
    return request<Message[]>(`/api/v1/chat/conversations/${conversationId}/messages`)
  },

  async sendMessage(conversationId: string, content: string, model?: string): Promise<Message> {
    return request<Message>(`/api/v1/chat/conversations/${conversationId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content, model }),
    })
  },

  async regenerateMessage(conversationId: string, messageId: string): Promise<Message> {
    return request<Message>(`/api/v1/chat/conversations/${conversationId}/messages/${messageId}/regenerate`, {
      method: 'POST',
    })
  },

  async editMessage(conversationId: string, messageId: string, content: string): Promise<Message> {
    return request<Message>(`/api/v1/chat/conversations/${conversationId}/messages/${messageId}/edit`, {
      method: 'POST',
      body: JSON.stringify({ content }),
    })
  },

  async submitFeedback(conversationId: string, messageId: string, feedback: FeedbackType, comment?: string): Promise<Message> {
    return request<Message>(`/api/v1/chat/conversations/${conversationId}/messages/${messageId}/feedback`, {
      method: 'POST',
      body: JSON.stringify({ feedback, comment }),
    })
  },

  async deleteMessage(conversationId: string, messageId: string): Promise<void> {
    await request<void>(`/api/v1/chat/conversations/${conversationId}/messages/${messageId}`, {
      method: 'DELETE',
    })
  },

  async exportConversation(conversationId: string, format: 'json' | 'markdown' | 'text' = 'markdown'): Promise<{ content: string; format: string }> {
    return request<{ content: string; format: string }>(`/api/v1/chat/conversations/${conversationId}/export?format=${format}`)
  },
}
