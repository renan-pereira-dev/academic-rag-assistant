import { useEffect, useRef, useState, type FormEvent } from 'react'
import { askQuestion } from './api'
import { MessageBubble } from './components/MessageBubble'
import type { ChatMessage } from './types'

// Omit distributivo: preserva os campos de cada variante da união (ex.: "sources").
type WithoutId<T> = T extends unknown ? Omit<T, 'id'> : never

function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const nextId = useRef(0)
  const listEnd = useRef<HTMLDivElement>(null)

  useEffect(() => {
    listEnd.current?.scrollIntoView?.({ behavior: 'smooth' })
  }, [messages, loading])

  const append = (message: WithoutId<ChatMessage>) =>
    setMessages((prev) => [...prev, { ...message, id: nextId.current++ } as ChatMessage])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const text = question.trim()
    if (!text || loading) return

    append({ role: 'user', text })
    setQuestion('')
    setLoading(true)
    try {
      const response = await askQuestion(text)
      append({ role: 'assistant', text: response.answer, sources: response.sources })
    } catch (error) {
      append({ role: 'error', text: (error as Error).message })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app">
      <header className="app__header">
        <h1>Assistente Acadêmico</h1>
        <p className="app__subtitle">Dúvidas sobre estágio, disciplinas e vida acadêmica na UniFBV.</p>
      </header>

      <p className="disclaimer" role="note">
        As respostas são geradas a partir dos documentos oficiais e podem conter erros.{' '}
        <strong>A fonte oficial é sempre o documento citado</strong>: confira a página indicada.
      </p>

      <main className="chat" aria-live="polite">
        {messages.length === 0 && (
          <p className="chat__empty">Faça uma pergunta, por exemplo: “A partir de qual período posso fazer estágio?”</p>
        )}
        {messages.map((message) => (
          <MessageBubble key={message.id} message={message} />
        ))}
        {loading && <div className="message message--assistant message--loading">Buscando nos documentos…</div>}
        <div ref={listEnd} />
      </main>

      <form className="composer" onSubmit={handleSubmit}>
        <label htmlFor="question" className="visually-hidden">
          Sua pergunta
        </label>
        <input
          id="question"
          type="text"
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder="Digite sua pergunta…"
          autoComplete="off"
          disabled={loading}
        />
        <button type="submit" disabled={loading || question.trim() === ''}>
          Enviar
        </button>
      </form>
    </div>
  )
}

export default App
