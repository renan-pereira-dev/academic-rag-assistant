import type { ChatMessage } from '../types'
import { SourceCard } from './SourceCard'

interface MessageBubbleProps {
  message: ChatMessage
}

export function MessageBubble({ message }: MessageBubbleProps) {
  if (message.role === 'user') {
    return <div className="message message--user">{message.text}</div>
  }

  if (message.role === 'error') {
    return (
      <div className="message message--error" role="alert">
        {message.text}
      </div>
    )
  }

  return (
    <div className="message message--assistant">
      <p className="message__text">{message.text}</p>
      {message.sources.length > 0 && (
        <section className="message__sources" aria-label="Fontes">
          <h3 className="message__sources-title">Fontes</h3>
          {message.sources.map((source, index) => (
            <SourceCard key={`${source.document}-${source.page}-${index}`} source={source} />
          ))}
        </section>
      )}
    </div>
  )
}
