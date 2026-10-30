import { afterEach, describe, expect, it } from 'vitest'
import { CLASE_IMPRIMIENDO, MARGEN_INFERIOR_MM, prepararImpresion, terminarImpresion } from './impresion'

function comprobanteDeAlto(px: number) {
  const el = document.createElement('section')
  el.getBoundingClientRect = () => ({ height: px }) as DOMRect
  return el
}

const hoja = () => document.getElementById('hoja-del-comprobante')

describe('impresión del comprobante en la térmica de 80 mm', () => {
  afterEach(() => terminarImpresion())

  it('ajusta la hoja al largo del comprobante, sin márgenes', () => {
    // 600 px a 96 ppp son 158,75 mm.
    const alto = prepararImpresion(comprobanteDeAlto(600))
    expect(alto).toBe(159 + MARGEN_INFERIOR_MM)
    expect(hoja()?.textContent).toBe(`@page { size: 80mm ${159 + MARGEN_INFERIOR_MM}mm; margin: 0; }`)
    expect(document.documentElement.classList.contains(CLASE_IMPRIMIENDO)).toBe(true)
  })

  it('si no puede medir, deja la hoja del controlador', () => {
    expect(prepararImpresion(comprobanteDeAlto(0))).toBeNull()
    expect(hoja()).toBeNull()
    expect(document.documentElement.classList.contains(CLASE_IMPRIMIENDO)).toBe(true)
  })

  it('no acumula hojas si se imprime dos veces', () => {
    prepararImpresion(comprobanteDeAlto(600))
    prepararImpresion(comprobanteDeAlto(700))
    expect(document.querySelectorAll('#hoja-del-comprobante')).toHaveLength(1)
  })

  it('al terminar devuelve la pantalla a la normalidad', () => {
    prepararImpresion(comprobanteDeAlto(600))
    terminarImpresion()
    expect(hoja()).toBeNull()
    expect(document.documentElement.classList.contains(CLASE_IMPRIMIENDO)).toBe(false)
  })
})
