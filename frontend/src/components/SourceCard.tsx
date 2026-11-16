import type { Source } from '../types'

interface SourceCardProps {
  source: Source
}

export function SourceCard({ source }: SourceCardProps) {
  return (
    <article className="source-card" aria-label={`Fonte: ${source.document}, página ${source.page}`}>
      <header className="source-card__header">
        <span className="source-card__document">{source.document}</span>
        <span className="source-card__page">Página {source.page}</span>
      </header>
      <blockquote className="source-card__excerpt">{source.excerpt}</blockquote>
    </article>
  )
}
