import { describe, expect, it } from 'vitest'
import { losartan, metformina } from '../test/datos'
import { ATENCION_NUEVA, reducirAtencion } from './atencion'

describe('reducirAtencion', () => {
  it('agrega cada producto identificado con un identificador propio', () => {
    let atencion = reducirAtencion(ATENCION_NUEVA, { tipo: 'identificado', producto: losartan })
    atencion = reducirAtencion(atencion, { tipo: 'identificado', producto: metformina })

    expect(atencion.lineas.map((l) => [l.id, l.producto.nombre])).toEqual([
      [1, 'Losartán 50 mg'],
      [2, 'Metformina 850 mg'],
    ])
  })

  it('no duplica un producto que ya está, aunque lleguen dos lecturas seguidas', () => {
    let atencion = reducirAtencion(ATENCION_NUEVA, { tipo: 'identificado', producto: losartan })
    atencion = reducirAtencion(atencion, { tipo: 'identificado', producto: losartan })

    expect(atencion.lineas).toHaveLength(1)
    expect(atencion.aviso).toEqual({ tipo: 'info', texto: 'Losartán 50 mg ya está en esta atención.' })
  })

  it('un aviso no toca lo capturado', () => {
    const conLosartan = reducirAtencion(ATENCION_NUEVA, { tipo: 'identificado', producto: losartan })
    const conError = reducirAtencion(conLosartan, { tipo: 'avisar', aviso: { tipo: 'error', texto: 'falló' } })

    expect(conError.lineas).toBe(conLosartan.lineas)
  })

  it('quitar una línea que no existe no cambia nada', () => {
    const conLosartan = reducirAtencion(ATENCION_NUEVA, { tipo: 'identificado', producto: losartan })

    expect(reducirAtencion(conLosartan, { tipo: 'quitar', id: 99 })).toBe(conLosartan)
  })
})
