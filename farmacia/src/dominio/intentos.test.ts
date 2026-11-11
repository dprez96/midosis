import { describe, expect, it } from 'vitest'
import { MAXIMO_DE_INTENTOS, registroLocal } from './intentos'

const intento = (en: string) => ({ en, productos: ['Losartán 50 mg'], motivo: 'No hay conexión con el servidor de MiDosis.' })

describe('registro de emisiones fallidas', () => {
  it('guarda en el navegador, el más reciente primero', () => {
    localStorage.clear()
    const registro = registroLocal()
    registro.anotar(intento('2026-10-27T18:00:00Z'))
    registro.anotar(intento('2026-10-27T18:05:00Z'))

    expect(registroLocal().listar().map((i) => i.en)).toEqual(['2026-10-27T18:05:00Z', '2026-10-27T18:00:00Z'])
    localStorage.clear()
  })

  it('sin almacenamiento del navegador funciona en memoria', () => {
    const registro = registroLocal(() => {
      throw new Error('almacenamiento bloqueado')
    })
    registro.anotar(intento('2026-10-27T18:00:00Z'))
    expect(registro.listar()).toHaveLength(1)
  })

  it('conserva solo los más recientes', () => {
    const registro = registroLocal(() => null)
    for (let i = 0; i < MAXIMO_DE_INTENTOS + 5; i++) registro.anotar(intento(`2026-10-27T18:${String(i).padStart(2, '0')}:00Z`))
    expect(registro.listar()).toHaveLength(MAXIMO_DE_INTENTOS)
  })
})
