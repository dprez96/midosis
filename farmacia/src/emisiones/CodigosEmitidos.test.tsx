import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorDeApi, type ApiDeRevocacion, type EmisionReciente } from '../api/cliente'
import { CodigosEmitidos } from './CodigosEmitidos'
import { registroLocal } from '../dominio/intentos'

const vigente: EmisionReciente = {
  id: '01M0877000WQNC8FHDYAA6Y83C',
  emitidaEn: '2026-10-15T14:00:00Z',
  expiraEn: '2026-10-18T14:00:00Z',
  productos: ['Losartán 50 mg', 'Metformina 850 mg'],
  revocadaEn: null,
}

const revocada: EmisionReciente = {
  id: '01M0877000WQNC8FHDYAA6Y83D',
  emitidaEn: '2026-10-15T13:00:00Z',
  expiraEn: '2026-10-18T13:00:00Z',
  productos: ['Clonazepam 0,5 mg'],
  revocadaEn: '2026-10-15T13:10:00Z',
}

function apiFalsa(emisiones: EmisionReciente[]) {
  const lista = [...emisiones]
  const api = {
    recientes: vi.fn<ApiDeRevocacion['recientes']>(async () => [...lista]),
    revocar: vi.fn<ApiDeRevocacion['revocar']>(async (id) => {
      const i = lista.findIndex((e) => e.id === id)
      lista[i] = { ...lista[i]!, revocadaEn: '2026-10-15T15:00:00Z' }
    }),
  }
  return api
}

async function abrirRevocacion(usuario: ReturnType<typeof userEvent.setup>, producto: string) {
  const fila = (await screen.findByText(producto)).closest('li')!
  await usuario.click(within(fila).getByRole('button', { name: 'Revocar' }))
  return within(fila)
}

describe('CodigosEmitidos', () => {
  it('lista los códigos vigentes y solo ofrece revocar los que no están revocados', async () => {
    render(<CodigosEmitidos api={apiFalsa([vigente, revocada])} />)

    const fila = (await screen.findByText('Losartán 50 mg, Metformina 850 mg')).closest('li')!
    expect(within(fila).getByText(/Vence el/)).toBeInTheDocument()
    const filaRevocada = screen.getByText('Clonazepam 0,5 mg').closest('li')!
    expect(within(filaRevocada).getByText(/Revocado el/)).toBeInTheDocument()
    expect(within(filaRevocada).queryByRole('button', { name: 'Revocar' })).not.toBeInTheDocument()
  })

  it('exige un motivo antes de revocar', async () => {
    const usuario = userEvent.setup()
    render(<CodigosEmitidos api={apiFalsa([vigente])} />)

    const fila = await abrirRevocacion(usuario, 'Losartán 50 mg, Metformina 850 mg')
    const confirmar = fila.getByRole('button', { name: 'Confirmar la revocación' })
    expect(confirmar).toBeDisabled()
    await usuario.type(fila.getByLabelText('Motivo de la revocación'), 'no')
    expect(confirmar).toBeDisabled()
    await usuario.type(fila.getByLabelText('Motivo de la revocación'), 'mbre de otro producto')
    expect(confirmar).toBeEnabled()
  })

  it('revoca con el motivo y muestra el código como revocado', async () => {
    const usuario = userEvent.setup()
    const api = apiFalsa([vigente])
    render(<CodigosEmitidos api={api} />)

    const fila = await abrirRevocacion(usuario, 'Losartán 50 mg, Metformina 850 mg')
    await usuario.type(fila.getByLabelText('Motivo de la revocación'), '  Posología mal registrada  ')
    await usuario.click(fila.getByRole('button', { name: 'Confirmar la revocación' }))

    expect(api.revocar).toHaveBeenCalledWith(vigente.id, 'Posología mal registrada')
    expect(await screen.findByRole('status')).toHaveTextContent('El código quedó revocado.')
    expect(await screen.findByText(/Revocado el/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Revocar' })).not.toBeInTheDocument()
  })

  it('muestra el motivo de core si no se pudo revocar', async () => {
    const usuario = userEvent.setup()
    const api = apiFalsa([vigente])
    api.revocar.mockRejectedValue(new ErrorDeApi(409, 'Ese código ya venció: no se puede canjear.'))
    render(<CodigosEmitidos api={api} />)

    const fila = await abrirRevocacion(usuario, 'Losartán 50 mg, Metformina 850 mg')
    await usuario.type(fila.getByLabelText('Motivo de la revocación'), 'Error de digitación')
    await usuario.click(fila.getByRole('button', { name: 'Confirmar la revocación' }))

    expect(await fila.findByRole('alert')).toHaveTextContent('Ese código ya venció')
    expect(fila.getByRole('button', { name: 'Confirmar la revocación' })).toBeEnabled()
  })

  it('cancelar cierra el formulario sin revocar', async () => {
    const usuario = userEvent.setup()
    const api = apiFalsa([vigente])
    render(<CodigosEmitidos api={api} />)

    const fila = await abrirRevocacion(usuario, 'Losartán 50 mg, Metformina 850 mg')
    await usuario.click(fila.getByRole('button', { name: 'Cancelar' }))

    expect(fila.queryByLabelText('Motivo de la revocación')).not.toBeInTheDocument()
    expect(api.revocar).not.toHaveBeenCalled()
  })

  it('sin códigos vigentes lo dice', async () => {
    render(<CodigosEmitidos api={apiFalsa([])} />)

    expect(await screen.findByText('No hay códigos vigentes en las últimas 72 horas.')).toBeInTheDocument()
  })

  it('muestra las emisiones que fallaron y permite descartarlas', async () => {
    const usuario = userEvent.setup()
    const intentos = registroLocal(() => null)
    intentos.anotar({ en: '2026-10-27T18:00:00Z', productos: ['Losartán 50 mg'], motivo: 'No hay conexión con el servidor de MiDosis.' })
    render(<CodigosEmitidos api={apiFalsa([])} intentos={intentos} />)

    const seccion = screen.getByRole('region', { name: 'Emisiones que fallaron' })
    expect(within(seccion).getByText('Losartán 50 mg')).toBeInTheDocument()
    expect(within(seccion).getByText(/No hay conexión/)).toBeInTheDocument()

    await usuario.click(within(seccion).getByRole('button', { name: 'Descartar' }))
    expect(screen.queryByRole('region', { name: 'Emisiones que fallaron' })).not.toBeInTheDocument()
    expect(intentos.listar()).toEqual([])
  })

  it('sin emisiones fallidas no muestra esa sección', () => {
    render(<CodigosEmitidos api={apiFalsa([])} intentos={registroLocal(() => null)} />)
    expect(screen.queryByRole('region', { name: 'Emisiones que fallaron' })).not.toBeInTheDocument()
  })

  it('si core no responde muestra el error', async () => {
    const api = apiFalsa([])
    api.recientes.mockRejectedValue(new ErrorDeApi(0, 'No hay conexión con el servidor de MiDosis.'))
    render(<CodigosEmitidos api={api} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('No hay conexión')
  })
})
