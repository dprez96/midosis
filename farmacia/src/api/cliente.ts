import type { Producto } from '../dominio/catalogo'
import type { Plantilla, Posologia } from '../dominio/posologia'

/** Error de una llamada a core, con un mensaje que se puede mostrar en el mesón. */
export class ErrorDeApi extends Error {
  readonly estado: number

  constructor(estado: number, mensaje: string) {
    super(mensaje)
    this.name = 'ErrorDeApi'
    this.estado = estado
  }
}

export type ObtenerToken = () => Promise<string>

export interface ApiDeCatalogo {
  /** El producto del código, o null si no está en el catálogo de la comuna. */
  buscarProducto(gtin: string): Promise<Producto | null>
  /** Búsqueda manual asistida por nombre, principio activo o comienzo del código. */
  buscarPorTexto(texto: string, cancelacion?: AbortSignal): Promise<Producto[]>
}

export interface ApiDePlantillas {
  listarPlantillas(gtin: string): Promise<Plantilla[]>
  /** Solo el químico farmacéutico; core responde 403 a cualquier otro rol y 409 si ya existe. */
  crearPlantilla(gtin: string, posologia: Posologia): Promise<Plantilla>
}

export type ApiDelMeson = ApiDeCatalogo & ApiDePlantillas

const MENSAJES_POR_ESTADO: Record<number, string> = {
  401: 'La sesión expiró. Vuelva a iniciar sesión.',
  403: 'Su perfil no tiene permiso para esta acción.',
}

function mensajePorEstado(estado: number): string {
  const conocido = MENSAJES_POR_ESTADO[estado]
  if (conocido) return conocido
  if (estado >= 500) return 'El servidor no respondió como se esperaba. Intente de nuevo en un momento.'
  return `La solicitud no se pudo completar (${estado}).`
}

/**
 * Cliente de core. Cada llamada lleva el token de la sesión: core saca de él la comuna y
 * el rol, así que la aplicación nunca los envía por su cuenta.
 */
export function crearApi(obtenerToken: ObtenerToken, base = '/api'): ApiDelMeson {
  async function pedir<T>(ruta: string, opciones: RequestInit = {}): Promise<T> {
    const token = await obtenerToken()
    let respuesta: Response
    try {
      respuesta = await fetch(`${base}${ruta}`, {
        ...opciones,
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
          ...(opciones.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        },
      })
    } catch (error) {
      if (opciones.signal?.aborted) throw error
      throw new ErrorDeApi(0, 'No hay conexión con el servidor de MiDosis.')
    }
    if (!respuesta.ok) {
      const cuerpo = (await respuesta.json().catch(() => null)) as { error?: unknown } | null
      const mensaje = typeof cuerpo?.error === 'string' ? cuerpo.error : mensajePorEstado(respuesta.status)
      throw new ErrorDeApi(respuesta.status, mensaje)
    }
    return (await respuesta.json()) as T
  }

  return {
    async buscarProducto(gtin) {
      try {
        return await pedir<Producto>(`/catalogo/productos/${encodeURIComponent(gtin)}`)
      } catch (error) {
        if (error instanceof ErrorDeApi && error.estado === 404) return null
        throw error
      }
    },

    buscarPorTexto(texto, cancelacion) {
      return pedir<Producto[]>(`/catalogo/productos?texto=${encodeURIComponent(texto)}`, { signal: cancelacion })
    },

    listarPlantillas(gtin) {
      return pedir<Plantilla[]>(`/catalogo/productos/${encodeURIComponent(gtin)}/plantillas`)
    },

    crearPlantilla(gtin, posologia) {
      return pedir<Plantilla>(`/catalogo/productos/${encodeURIComponent(gtin)}/plantillas`, {
        method: 'POST',
        body: JSON.stringify(posologia),
      })
    },
  }
}
