import type { ChatResponse } from './types'

// Em desenvolvimento a API roda em http://localhost:8080 (CORS liberado para :5173).
// Pode ser trocada com a variável VITE_API_URL.
const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '')

const GENERIC_ERROR = 'Não foi possível obter uma resposta agora. Tente novamente em instantes.'

export async function askQuestion(question: string): Promise<ChatResponse> {
  let response: Response
  try {
    response = await fetch(`${API_URL}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    })
  } catch {
    throw new Error('Não foi possível conectar ao servidor. Verifique se o back-end está rodando.')
  }

  if (!response.ok) {
    // Erros de validação (400) vêm como ProblemDetail com a mensagem em "detail".
    const problem = await response.json().catch(() => null)
    throw new Error(response.status === 400 && problem?.detail ? problem.detail : GENERIC_ERROR)
  }

  const data = (await response.json()) as ChatResponse
  return { answer: data.answer, sources: data.sources ?? [] }
}
