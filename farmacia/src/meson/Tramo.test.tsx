import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Plantilla } from '../dominio/posologia'
import { catalogoFalso, losartan } from '../test/datos'
import { PantallaMeson } from './PantallaMeson'

type Usuario = ReturnType<typeof userEvent.setup>

const unoDiario: Plantilla = {
  id: 'plantilla-uno-diario',
  cantidad: 1,
  unidad: 'comprimido',
  frecuencia: 'PT24H',
  duracionDias: null,
  indicaciones: null,
}

const lineas = () => within(screen.getByRole('region', { name: 'Productos de esta atención' }))

async function prepararLosartan(usuario: Usuario) {
  render(
    <PantallaMeson
      api={catalogoFalso([losartan], { [losartan.gtin]: [unoDiario] })}
      puedeGuardarPlantillas={false}
      hoy={() => '2026-10-15'}
    />,
  )
  await usuario.keyboard(`${losartan.gtin}{Enter}`)
  await usuario.click(await screen.findByRole('button', { name: '1 comprimido cada 24 horas' }))
}

async function registrarEntrega(usuario: Usuario, duracion: string, entregado: string) {
  const duracionIndicada = screen.getByLabelText('Duración indicada en la receta (días)')
  await usuario.clear(duracionIndicada)
  await usuario.type(duracionIndicada, duracion)
  await usuario.type(screen.getByLabelText('Cantidad entregada (comprimidos)'), entregado)
  await usuario.click(screen.getByRole('button', { name: 'Registrar entrega' }))
}

describe('Tramo entregado en el mesón (HU-04)', () => {
  it('180 días indicados y 60 comprimidos a uno diario: el retiro cubre 60 días y dice hasta cuándo', async () => {
    const usuario = userEvent.setup()
    await prepararLosartan(usuario)
    await registrarEntrega(usuario, '180', '60')

    const entrega = lineas().getByRole('group', { name: 'Entrega de Losartán 50 mg' })
    expect(within(entrega).getByText('Este retiro cubre 60 días:')).toBeInTheDocument()
    expect(within(entrega).getByText(/alcanza hasta el 14-12-2026/)).toBeInTheDocument()
    expect(within(entrega).getByText('Tratamiento indicado: 180 días. Quedan 120 días por retirar.')).toBeInTheDocument()
    expect(within(entrega).queryByRole('alert')).not.toBeInTheDocument()
  })

  it('advierte cuando lo entregado cubre más de lo indicado', async () => {
    const usuario = userEvent.setup()
    await prepararLosartan(usuario)
    await registrarEntrega(usuario, '7', '30')

    expect(screen.getByRole('alert')).toHaveTextContent('Lo entregado cubre más de lo indicado (30 de 7 días)')
    expect(screen.getByText(/Este retiro completa lo indicado/)).toBeInTheDocument()
  })

  it('no acepta una entrega sin la duración de la receta', async () => {
    const usuario = userEvent.setup()
    await prepararLosartan(usuario)
    await usuario.type(screen.getByLabelText('Cantidad entregada (comprimidos)'), '60')
    await usuario.click(screen.getByRole('button', { name: 'Registrar entrega' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Indique la duración del tratamiento según la receta')
  })

  it('la entrega registrada se puede corregir', async () => {
    const usuario = userEvent.setup()
    await prepararLosartan(usuario)
    await registrarEntrega(usuario, '180', '60')
    await usuario.click(screen.getByRole('button', { name: 'Cambiar la entrega de Losartán 50 mg' }))
    const entregado = screen.getByLabelText('Cantidad entregada (comprimidos)')
    await usuario.clear(entregado)
    await usuario.type(entregado, '30')
    await usuario.click(screen.getByRole('button', { name: 'Registrar entrega' }))

    expect(screen.getByText('Este retiro cubre 30 días:')).toBeInTheDocument()
  })
})
