import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ApiDeCorreccion, EmisionEncontrada } from '../api/cliente'
import { normalizarReferencia, referencia } from '../dominio/correccion'
import { Correccion } from './Correccion'

const cargada: EmisionEncontrada = {
  id: '01M0877000WQNC8FHDYAA6Y83C',
  emitidaEn: '2026-10-15T14:00:00Z',
  productos: ['Losartán 50 mg'],
  revocadaEn: null,
  corregidaPor: null,
}

function apiFalsa(encontradas: EmisionEncontrada[]) {
  return {
    buscarPorReferencia: vi.fn<ApiDeCorreccion['buscarPorReferencia']>(async () => encontradas),
    corregir: vi.fn<ApiDeCorreccion['corregir']>(),
  }
}

describe('referencia', () => {
  it('son los últimos 8 caracteres del jti en dos grupos', () => {
    expect(referencia('01M0877000WQNC8FHDYAA6Y83C')).toBe('YAA6-Y83C')
  })

  it('se normaliza como la escribiría una persona', () => {
    expect(normalizarReferencia('yaa6-y83c')).toBe('YAA6Y83C')
    expect(normalizarReferencia(' o1il 2345 ')).toBe('01112345')
    expect(normalizarReferencia('YAA6-Y83')).toBeNull()
    expect(normalizarReferencia('YAA6-Y83U')).toBeNull()
  })
})

describe('Correccion', () => {
  it('busca por la referencia y, con motivo e instrucción, inicia la corrección', async () => {
    const usuario = userEvent.setup()
    const api = apiFalsa([cargada])
    const alIniciar = vi.fn()
    render(<Correccion api={api} alIniciar={alIniciar} />)

    await usuario.type(screen.getByLabelText('Referencia'), 'yaa6-y83c')
    await usuario.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(api.buscarPorReferencia).toHaveBeenCalledWith('YAA6Y83C')

    const fila = (await screen.findByText('Losartán 50 mg')).closest('li')!
    await usuario.click(within(fila).getByRole('button', { name: 'Corregir este' }))
    const continuar = within(fila).getByRole('button', { name: 'Registrar los productos corregidos' })
    expect(continuar).toBeDisabled()

    await usuario.type(within(fila).getByLabelText('Motivo de la corrección'), 'Dosis mal transcrita')
    expect(continuar).toBeDisabled()
    await usuario.click(within(fila).getByLabelText(/Suspender el tratamiento anterior/))
    await usuario.click(continuar)

    expect(alIniciar).toHaveBeenCalledWith({
      original: cargada.id,
      productos: ['Losartán 50 mg'],
      motivo: 'Dosis mal transcrita',
      instruccion: 'suspender',
    })
  })

  it('un código ya corregido no se ofrece corregir otra vez', async () => {
    const usuario = userEvent.setup()
    render(<Correccion api={apiFalsa([{ ...cargada, corregidaPor: '01M0877000WQNC8FHDYAA6Z99Z' }])} alIniciar={vi.fn()} />)

    await usuario.type(screen.getByLabelText('Referencia'), 'YAA6Y83C')
    await usuario.click(screen.getByRole('button', { name: 'Buscar' }))

    const fila = (await screen.findByText('Losartán 50 mg')).closest('li')!
    expect(within(fila).getByText(/Ya corregido con el código YAA6-Z99Z/)).toBeInTheDocument()
    expect(within(fila).queryByRole('button', { name: 'Corregir este' })).not.toBeInTheDocument()
  })

  it('sin resultados lo dice, y una referencia mal escrita no se busca', async () => {
    const usuario = userEvent.setup()
    const api = apiFalsa([])
    render(<Correccion api={api} alIniciar={vi.fn()} />)

    await usuario.type(screen.getByLabelText('Referencia'), 'YAA6-Y83U')
    expect(screen.getByRole('button', { name: 'Buscar' })).toBeDisabled()
    expect(screen.getByRole('alert')).toHaveTextContent('La referencia son 8 letras o números')

    await usuario.clear(screen.getByLabelText('Referencia'))
    await usuario.type(screen.getByLabelText('Referencia'), 'YAA6Y83C')
    await usuario.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(await screen.findByText(/No hay ningún código de esta farmacia/)).toBeInTheDocument()
  })
})
