import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ErrorDeApi, type ApiDeDemanda, type DemandaProyectada } from '../api/cliente'
import { Demanda } from './Demanda'

const semanas = Array.from({ length: 12 }, (_, i) => new Date(Date.UTC(2026, 10, 2 + 7 * i)).toISOString().slice(0, 10))

const proyectada: DemandaProyectada = {
  semanas,
  productos: [
    { gtin: '7802250012344', nombre: 'Losartán 50 mg', unidades: [0, 0, 30, 0, 0, 0, 30, 0, 0, 0, 0, 30], tasaDeRetorno: 0.85 },
  ],
  supuesto: 'La tasa de retorno de cada producto es la razón entre los retiros registrados y las renovaciones esperadas.',
  desviacion: [{ gtin: '7802250012344', nombre: 'Losartán 50 mg', semana: '2026-10-26', proyectado: 30, real: 20 }],
}

const api = (r: () => Promise<DemandaProyectada>): ApiDeDemanda => ({ demanda: vi.fn(r) })

describe('Demanda proyectada (HU-27)', () => {
  it('muestra por producto y semana, con el total y la tasa de retorno', async () => {
    render(<Demanda api={api(async () => proyectada)} />)

    const tabla = await screen.findByRole('table', { name: /Unidades por semana/ })
    const fila = within(tabla).getByRole('row', { name: /Losartán 50 mg/ })
    expect(within(fila).getByText('90')).toBeInTheDocument()
    expect(within(fila).getByText('85 %')).toBeInTheDocument()
    expect(within(tabla).getByRole('columnheader', { name: '02-11' })).toBeInTheDocument()
  })

  it('declara el supuesto aplicado', async () => {
    render(<Demanda api={api(async () => proyectada)} />)
    expect(await screen.findByText(/razón entre los retiros registrados/)).toBeInTheDocument()
  })

  it('contrasta lo proyectado con lo retirado y muestra la desviación', async () => {
    render(<Demanda api={api(async () => proyectada)} />)
    const tabla = await screen.findByRole('table', { name: /proyectado frente a retirado/ })
    const fila = within(tabla).getByRole('row', { name: /Losartán 50 mg/ })
    expect(within(fila).getByText('-10')).toBeInTheDocument()
  })

  it('sin tratamientos en curso lo dice', async () => {
    render(<Demanda api={api(async () => ({ semanas, productos: [], supuesto: '', desviacion: [] }))} />)
    expect(await screen.findByText(/No hay retiros proyectados/)).toBeInTheDocument()
  })

  it('muestra el error de core', async () => {
    render(<Demanda api={api(async () => { throw new ErrorDeApi(403, 'Su perfil no tiene permiso para esta acción.') })} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('no tiene permiso')
  })
})
