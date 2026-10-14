import { describe, expect, it } from 'vitest'
import { clonazepam, losartan } from '../test/datos'
import { nombreEnComprobante, referenciaNeutra } from './comprobante'

describe('nombre en el comprobante (HU-06)', () => {
  it('un producto sin control legal se imprime con su nombre', () => {
    expect(nombreEnComprobante(losartan, 1)).toBe('Losartán 50 mg')
  })

  it('un producto con control legal se imprime con una referencia neutra', () => {
    const impreso = nombreEnComprobante(clonazepam, 2)
    expect(impreso).toBe(referenciaNeutra(2))
    expect(impreso).not.toMatch(/clonazepam/i)
  })

  it('la referencia neutra no menciona el producto ni el régimen de control', () => {
    const referencia = referenciaNeutra(3)
    expect(referencia).toContain('3')
    expect(referencia).not.toMatch(/DS|404|405|control|psicotr|estupef/i)
  })
})
