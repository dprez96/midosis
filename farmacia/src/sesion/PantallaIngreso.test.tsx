import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PantallaIngreso } from './PantallaIngreso'

describe('PantallaIngreso', () => {
  it('inicia sesión con el correo sin espacios', async () => {
    const usuario = userEvent.setup()
    const iniciar = vi.fn(async () => {})
    render(<PantallaIngreso iniciar={iniciar} />)

    await usuario.type(screen.getByLabelText('Correo'), ' aux@midosis.cl ')
    await usuario.type(screen.getByLabelText('Contraseña'), 'clave-de-prueba')
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(iniciar).toHaveBeenCalledWith('aux@midosis.cl', 'clave-de-prueba')
  })

  it('muestra el motivo del rechazo y borra la contraseña', async () => {
    const usuario = userEvent.setup()
    const iniciar = vi.fn(async () => {
      throw new Error('Correo o contraseña incorrectos.')
    })
    render(<PantallaIngreso iniciar={iniciar} />)

    await usuario.type(screen.getByLabelText('Correo'), 'aux@midosis.cl')
    await usuario.type(screen.getByLabelText('Contraseña'), 'equivocada')
    await usuario.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos.')
    expect(screen.getByLabelText('Contraseña')).toHaveValue('')
  })
})
