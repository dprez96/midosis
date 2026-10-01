import { detalleDeProducto, type Producto } from '../dominio/catalogo'
import type { Posologia } from '../dominio/posologia'

/**
 * La atención en curso en el mesón: los productos ya identificados, su posología y el
 * último aviso.
 *
 * Es un reductor puro para que dos lecturas seguidas del lector no se pisen: cada
 * identificación se resuelve contra el estado vigente, no contra el de cuando empezó la
 * consulta.
 */

export interface Linea {
  id: number
  producto: Producto
  /** Null mientras no se registre (HU-03). */
  posologia: Posologia | null
  /** La plantilla de la que salió la posología, o null si se ingresó a mano. */
  plantillaId: string | null
}

export interface Aviso {
  tipo: 'exito' | 'info' | 'error'
  texto: string
}

export interface Atencion {
  lineas: Linea[]
  aviso: Aviso | null
  siguienteId: number
}

export type Accion =
  | { tipo: 'identificado'; producto: Producto }
  | { tipo: 'posologia'; id: number; posologia: Posologia | null; plantillaId: string | null }
  | { tipo: 'quitar'; id: number }
  | { tipo: 'avisar'; aviso: Aviso | null }

export const ATENCION_NUEVA: Atencion = { lineas: [], aviso: null, siguienteId: 1 }

export function reducirAtencion(estado: Atencion, accion: Accion): Atencion {
  switch (accion.tipo) {
    case 'identificado': {
      const { producto } = accion
      if (estado.lineas.some((linea) => linea.producto.gtin === producto.gtin)) {
        return { ...estado, aviso: { tipo: 'info', texto: `${producto.nombre} ya está en esta atención.` } }
      }
      return {
        lineas: [...estado.lineas, { id: estado.siguienteId, producto, posologia: null, plantillaId: null }],
        siguienteId: estado.siguienteId + 1,
        aviso: { tipo: 'exito', texto: `Identificado: ${producto.nombre}. ${detalleDeProducto(producto)}.` },
      }
    }
    case 'posologia':
      return {
        ...estado,
        lineas: estado.lineas.map((linea) =>
          linea.id === accion.id ? { ...linea, posologia: accion.posologia, plantillaId: accion.plantillaId } : linea,
        ),
      }
    case 'quitar': {
      const quitada = estado.lineas.find((linea) => linea.id === accion.id)
      if (!quitada) return estado
      return {
        ...estado,
        lineas: estado.lineas.filter((linea) => linea.id !== accion.id),
        aviso: { tipo: 'info', texto: `Se quitó ${quitada.producto.nombre} de la atención.` },
      }
    }
    case 'avisar':
      return { ...estado, aviso: accion.aviso }
  }
}
