import { describe, expect, it } from 'vitest'
import { leerCodigo, verificadorCorrecto } from './gtin'

/** GTIN sintético con verificador correcto, el mismo de las pruebas de core. */
const LOSARTAN = '7802250012344'

describe('verificadorCorrecto', () => {
  it('acepta los largos de GTIN con su verificador', () => {
    expect(verificadorCorrecto(LOSARTAN)).toBe(true)
    expect(verificadorCorrecto('96385074')).toBe(true)
    expect(verificadorCorrecto('012345678905')).toBe(true)
    expect(verificadorCorrecto('00012345678905')).toBe(true)
  })

  it('rechaza un verificador que no corresponde', () => {
    expect(verificadorCorrecto('7802250012345')).toBe(false)
  })
})

describe('leerCodigo', () => {
  it('entrega el GTIN de un código EAN-13 leído tal cual', () => {
    expect(leerCodigo(LOSARTAN)).toEqual({ tipo: 'gtin', gtin: LOSARTAN })
  })

  it('ignora los espacios que deja quien lo escribe a mano', () => {
    expect(leerCodigo(' 780 2250 012344 ')).toEqual({ tipo: 'gtin', gtin: LOSARTAN })
  })

  it('rechaza un código mal leído por su dígito verificador', () => {
    const lectura = leerCodigo('7802250012345')
    expect(lectura.tipo).toBe('invalido')
    expect(lectura).toMatchObject({ motivo: expect.stringContaining('dígito verificador') })
  })

  it('rechaza largos que ningún GTIN tiene', () => {
    expect(leerCodigo('78022500123')).toMatchObject({ tipo: 'invalido', motivo: expect.stringContaining('se leyeron 11') })
    expect(leerCodigo('78022')).toMatchObject({ tipo: 'invalido' })
  })

  it('rechaza lo que no son dígitos y la lectura vacía', () => {
    expect(leerCodigo('78022500A2344')).toMatchObject({ tipo: 'invalido' })
    expect(leerCodigo('   ')).toMatchObject({ tipo: 'invalido', motivo: expect.stringContaining('No llegó') })
  })

  it('toma el GTIN de una cadena GS1 de DataMatrix, con prefijo de simbología y separadores', () => {
    const datamatrix = `]d201078022500123441726123110LOTE\u001d21ABC123`
    expect(leerCodigo(datamatrix)).toEqual({ tipo: 'gtin', gtin: LOSARTAN })
  })

  it('reduce a 13 dígitos un GTIN-14 que empieza con cero', () => {
    expect(leerCodigo(`0${LOSARTAN}`)).toEqual({ tipo: 'gtin', gtin: LOSARTAN })
  })

  it('conserva el GTIN-14 de un embalaje, que no empieza con cero', () => {
    expect(leerCodigo('17802250012341')).toEqual({ tipo: 'gtin', gtin: '17802250012341' })
  })
})
