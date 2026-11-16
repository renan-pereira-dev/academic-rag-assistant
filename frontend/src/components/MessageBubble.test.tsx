import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { MessageBubble } from './MessageBubble'
import { SourceCard } from './SourceCard'

describe('SourceCard', () => {
  it('mostra documento, página e trecho', () => {
    render(
      <SourceCard
        source={{ document: 'Manual de Graduação', page: 40, excerpt: 'A disciplina optativa é...' }}
      />,
    )

    const card = screen.getByRole('article', { name: 'Fonte: Manual de Graduação, página 40' })
    expect(within(card).getByText('Manual de Graduação')).toBeInTheDocument()
    expect(within(card).getByText('Página 40')).toBeInTheDocument()
    expect(within(card).getByText('A disciplina optativa é...')).toBeInTheDocument()
  })
})

describe('MessageBubble', () => {
  it('renderiza uma resposta com duas fontes como dois cartões', () => {
    render(
      <MessageBubble
        message={{
          id: 1,
          role: 'assistant',
          text: 'A partir do 3º período.',
          sources: [
            { document: 'Manual Institucional de Estágios 2025', page: 12, excerpt: 'O estágio poderá...' },
            { document: 'Manual de Graduação', page: 40, excerpt: 'Estágio supervisionado...' },
          ],
        }}
      />,
    )

    expect(screen.getByText('A partir do 3º período.')).toBeInTheDocument()
    const cards = screen.getAllByRole('article')
    expect(cards).toHaveLength(2)
    expect(within(cards[0]).getByText('Manual Institucional de Estágios 2025')).toBeInTheDocument()
    expect(within(cards[0]).getByText('Página 12')).toBeInTheDocument()
    expect(within(cards[0]).getByText('O estágio poderá...')).toBeInTheDocument()
    expect(within(cards[1]).getByText('Manual de Graduação')).toBeInTheDocument()
    expect(within(cards[1]).getByText('Página 40')).toBeInTheDocument()
    expect(within(cards[1]).getByText('Estágio supervisionado...')).toBeInTheDocument()
  })

  it('não mostra cartões nem o título "Fontes" quando a resposta é uma recusa', () => {
    render(
      <MessageBubble message={{ id: 2, role: 'assistant', text: 'Não encontrei essa informação.', sources: [] }} />,
    )

    expect(screen.getByText('Não encontrei essa informação.')).toBeInTheDocument()
    expect(screen.queryAllByRole('article')).toHaveLength(0)
    expect(screen.queryByText('Fontes')).not.toBeInTheDocument()
  })
})
