import { describe, expect, it } from 'vitest'
import {
  CAMPOS_VACIOS,
  describirFrecuencia,
  describirPosologia,
  frecuenciaIso,
  validarPosologia,
  type CamposDePosologia,
} from './posologia'

describe('describirPosologia', () => {
  it('se lee como en el mesón', () => {
    expect(
      describirPosologia({
        cantidad: 1,
        unidad: 'comprimido',
        frecuencia: 'PT12H',
        duracionDias: 30,
        indicaciones: 'Con alimentos',
      }),
    ).toBe('1 comprimido cada 12 horas por 30 días. Con alimentos')
  })

  it('usa plural, coma decimal y omite lo que no se indicó', () => {
    expect(
      describirPosologia({ cantidad: 0.5, unidad: 'comprimido', frecuencia: 'PT24H', duracionDias: null, indicaciones: null }),
    ).toBe('0,5 comprimidos cada 24 horas')
    expect(
      describirPosologia({ cantidad: 2, unidad: 'inhalacion', frecuencia: 'PT6H', duracionDias: 1, indicaciones: null }),
    ).toBe('2 inhalaciones cada 6 horas por 1 día')
  })
})

describe('frecuencias', () => {
  it('describe horas, días y minutos', () => {
    expect(describirFrecuencia('PT1H')).toBe('cada 1 hora')
    expect(describirFrecuencia('PT24H')).toBe('cada 24 horas')
    expect(describirFrecuencia('PT72H')).toBe('cada 3 días')
    expect(describirFrecuencia('PT30M')).toBe('cada 30 minutos')
  })

  it('el intervalo viaja siempre en horas', () => {
    expect(frecuenciaIso(8, 'horas')).toBe('PT8H')
    expect(frecuenciaIso(2, 'dias')).toBe('PT48H')
  })
})

describe('validarPosologia', () => {
  const completos: CamposDePosologia = {
    cantidad: '1,5',
    unidad: 'comprimido',
    intervalo: '8',
    unidadDeIntervalo: 'horas',
    duracionDias: '',
    indicaciones: '  En   ayunas ',
  }

  it('convierte los campos en la posología que espera core', () => {
    expect(validarPosologia(completos)).toEqual({
      posologia: { cantidad: 1.5, unidad: 'comprimido', frecuencia: 'PT8H', duracionDias: null, indicaciones: 'En ayunas' },
    })
  })

  it('explica qué falta, en orden', () => {
    expect(validarPosologia(CAMPOS_VACIOS)).toEqual({ error: 'Indique la cantidad por toma.' })
    expect(validarPosologia({ ...completos, unidad: '' })).toEqual({ error: 'Elija la unidad de la dosis.' })
    expect(validarPosologia({ ...completos, intervalo: '' })).toEqual({ error: 'Indique cada cuánto se toma.' })
  })

  it('aplica los mismos límites que core', () => {
    expect(validarPosologia({ ...completos, cantidad: '0,29' })).toMatchObject({ posologia: { cantidad: 0.29 } })
    expect(validarPosologia({ ...completos, cantidad: '0' })).toHaveProperty('error')
    expect(validarPosologia({ ...completos, cantidad: '0,125' })).toHaveProperty('error')
    expect(validarPosologia({ ...completos, intervalo: '91', unidadDeIntervalo: 'dias' })).toHaveProperty('error')
    expect(validarPosologia({ ...completos, intervalo: '7.5' })).toHaveProperty('error')
    expect(validarPosologia({ ...completos, duracionDias: '366' })).toHaveProperty('error')
    expect(validarPosologia({ ...completos, indicaciones: 'a'.repeat(121) })).toHaveProperty('error')
  })
})
