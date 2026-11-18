import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorDeApi, type ApiDeExportacion, type Descarga } from '../api/cliente'
import { Exportacion } from './Exportacion'

describe('Exportacion', () => {
  it('descarga el ZIP de la comuna con el nombre que propone core', async () => {
    const usuario = userEvent.setup()
    const descarga: Descarga = { nombre: 'midosis-13123-2026-11-18.zip', contenido: new Blob(['zip']) }
    const api = { exportar: vi.fn<ApiDeExportacion['exportar']>(async () => descarga) }
    const guardar = vi.fn()
    render(<Exportacion api={api} guardar={guardar} />)

    await usuario.click(screen.getByRole('button', { name: 'Descargar los datos' }))

    expect(guardar).toHaveBeenCalledWith(descarga)
    expect(await screen.findByText('Se descargó midosis-13123-2026-11-18.zip.')).toBeInTheDocument()
  })

  it('si core lo rechaza, lo dice', async () => {
    const usuario = userEvent.setup()
    const api = {
      exportar: vi.fn<ApiDeExportacion['exportar']>(async () => {
        throw new ErrorDeApi(403, 'Su perfil no tiene permiso para esta acción.')
      }),
    }
    render(<Exportacion api={api} guardar={vi.fn()} />)

    await usuario.click(screen.getByRole('button', { name: 'Descargar los datos' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Su perfil no tiene permiso para esta acción.')
  })
})
