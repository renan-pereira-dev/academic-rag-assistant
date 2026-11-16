// Contrato de POST /api/chat (ver backend: chat/dto).

export interface Source {
  document: string
  page: number
  excerpt: string
}

export interface ChatResponse {
  answer: string
  sources: Source[]
}

export type ChatMessage =
  | { id: number; role: 'user'; text: string }
  | { id: number; role: 'assistant'; text: string; sources: Source[] }
  | { id: number; role: 'error'; text: string }
