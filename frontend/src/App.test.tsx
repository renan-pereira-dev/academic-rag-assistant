import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

function mockFetch(status: number, body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('mostra o aviso de que a fonte oficial é o documento citado', () => {
    render(<App />)
    expect(screen.getByRole('note')).toHaveTextContent('A fonte oficial é sempre o documento citado')
  })

  it('não permite enviar pergunta vazia', async () => {
    const fetchMock = mockFetch(200, { answer: 'x', sources: [] })
    render(<App />)

    const button = screen.getByRole('button', { name: 'Enviar' })
    expect(button).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Sua pergunta'), '   ')
    expect(button).toBeDisabled()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('envia a pergunta para POST /api/chat e mostra a resposta com duas fontes', async () => {
    const fetchMock = mockFetch(200, {
      answer: 'A partir do 3º período.',
      sources: [
        { document: 'Manual Institucional de Estágios 2025', page: 12, excerpt: 'O estágio poderá...' },
        { document: 'Manual de Graduação', page: 40, excerpt: 'Estágio supervisionado...' },
      ],
    })
    render(<App />)

    await userEvent.type(screen.getByLabelText('Sua pergunta'), 'A partir de qual período posso estagiar?')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(await screen.findByText('A partir do 3º período.')).toBeInTheDocument()
    expect(screen.getByText('A partir de qual período posso estagiar?')).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(2)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/chat')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ question: 'A partir de qual período posso estagiar?' })
  })

  it('mostra a mensagem de validação da API quando ela devolve 400', async () => {
    mockFetch(400, { status: 400, detail: 'A pergunta não pode ser vazia.' })
    render(<App />)

    await userEvent.type(screen.getByLabelText('Sua pergunta'), 'x')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('A pergunta não pode ser vazia.')
  })

  it('mostra erro genérico, sem detalhes internos, quando a API falha', async () => {
    mockFetch(500, { status: 500, detail: 'stack trace interno' })
    render(<App />)

    await userEvent.type(screen.getByLabelText('Sua pergunta'), 'Como funciona o trancamento?')
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Não foi possível obter uma resposta agora')
    expect(alert).not.toHaveTextContent('stack trace interno')
  })
})
