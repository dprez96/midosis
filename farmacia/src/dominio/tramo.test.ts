import { describe, expect, it } from 'vitest'
import coberturaJson from '../../../contrato/vectores/cobertura.json?raw'
import type { Posologia } from './posologia'
import {
  calcularCobertura,
  cobertura,
  fechaLegible,
  minutosDeIntervalo,
  sumarDias,
  tomasEnLoEntregado,
  validarTramo,
} from './tramo'

interface CasoDeCobertura {
  nombre: string
  du: number
  e: number
  f: string
  retiro: string
  esperado: {
    co: number
    agotamiento: string
    completaLoIndicado: boolean
    diasPendientes: number
    excedeLoIndicado: boolean
  }
}

/** Los mismos casos que corren el motor y la aplicación del paciente. */
const vectores = JSON.parse(coberturaJson) as { casos: CasoDeCobertura[] }

const unoDiario: Posologia = { cantidad: 1, unidad: 'comprimido', frecuencia: 'PT24H', duracionDias: 180, indicaciones: null }

describe('cobertura del tramo (ADR-010)', () => {
  it('trae los casos del contrato', () => {
    expect(vectores.casos.length).toBeGreaterThanOrEqual(5)
  })

  it.each(vectores.casos.map((c) => [c.nombre, c] as const))('coincide con el contrato: %s', (_, caso) => {
    const { coberturaDias, ...resto } = cobertura(caso.e, caso.f, caso.du, caso.retiro)
    expect({ co: coberturaDias, ...resto }).toEqual(caso.esperado)
  })

  it('el caso de la HU-04: 180 días indicados y 60 comprimidos a uno diario cubren 60 días', () => {
    const c = calcularCobertura(unoDiario, { duracionIndicadaDias: 180, unidadesEntregadas: 60 }, '2026-10-15')
    expect(c).toEqual({
      dosisEntregadas: 60,
      unidadesSobrantes: 0,
      coberturaDias: 60,
      agotamiento: '2026-12-14',
      completaLoIndicado: false,
      diasPendientes: 120,
      excedeLoIndicado: false,
    })
  })
})

describe('tomas en lo entregado', () => {
  it('cuenta tomas, no unidades', () => {
    expect(tomasEnLoEntregado(200, 2)).toEqual({ tomas: 100, sobrante: 0 })
    expect(tomasEnLoEntregado(30, 0.5)).toEqual({ tomas: 60, sobrante: 0 })
  })

  it('informa lo que sobra sin completar una toma', () => {
    expect(tomasEnLoEntregado(25, 2)).toEqual({ tomas: 12, sobrante: 1 })
    expect(tomasEnLoEntregado(1, 0.3)).toEqual({ tomas: 3, sobrante: 0.1 })
  })
})

describe('intervalos y fechas', () => {
  it('lee la forma canónica del contrato', () => {
    expect(minutosDeIntervalo('PT12H')).toBe(720)
    expect(minutosDeIntervalo('PT1H30M')).toBe(90)
    expect(minutosDeIntervalo('PT45M')).toBe(45)
    expect(minutosDeIntervalo('P1D')).toBeNull()
  })

  it('suma días cruzando meses, años y un 29 de febrero', () => {
    expect(sumarDias('2026-12-20', 30)).toBe('2027-01-19')
    expect(sumarDias('2028-02-01', 30)).toBe('2028-03-02')
  })

  it('muestra la fecha como se lee en Chile', () => {
    expect(fechaLegible('2026-12-14')).toBe('14-12-2026')
  })
})

describe('validación del tramo', () => {
  it('acepta coma decimal y la duración prellenada', () => {
    expect(validarTramo({ duracionIndicadaDias: '180', unidadesEntregadas: '60' }, unoDiario)).toEqual({
      tramo: { duracionIndicadaDias: 180, unidadesEntregadas: 60 },
    })
    const medio = { ...unoDiario, cantidad: 0.5 }
    expect(validarTramo({ duracionIndicadaDias: '30', unidadesEntregadas: '7,5' }, medio)).toEqual({
      tramo: { duracionIndicadaDias: 30, unidadesEntregadas: 7.5 },
    })
  })

  it('exige la duración de la receta', () => {
    expect(validarTramo({ duracionIndicadaDias: '', unidadesEntregadas: '60' }, unoDiario)).toHaveProperty('error')
    expect(validarTramo({ duracionIndicadaDias: '400', unidadesEntregadas: '60' }, unoDiario)).toHaveProperty('error')
  })

  it('rechaza lo que no alcanza para una toma', () => {
    const dos = { ...unoDiario, cantidad: 2 }
    expect(validarTramo({ duracionIndicadaDias: '30', unidadesEntregadas: '1' }, dos)).toEqual({
      error: 'Lo entregado no alcanza para una toma de 2 comprimidos.',
    })
  })
})
