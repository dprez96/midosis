import type { Producto } from '../dominio/catalogo'
import { peticionDeEmision, type CodigoEmitido, type Consentimiento, type LineaParaEmitir } from '../dominio/emision'
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

export interface ApiDeEmision {
  /** Emite el código de la atención (HU-01). Core responde 503 si la emisión no está disponible. */
  emitir(lineas: LineaParaEmitir[], consentimiento: Consentimiento): Promise<CodigoEmitido>
}

/** Una emisión de la comuna que todavía se puede canjear (HU-07). */
export interface EmisionReciente {
  id: string
  emitidaEn: string
  expiraEn: string
  /** Nombres de los productos, para que el mesón reconozca la atención. */
  productos: string[]
  revocadaEn: string | null
}

export interface ApiDeRevocacion {
  /** Las emisiones vigentes o recién revocadas de la comuna, de la más nueva a la más antigua. */
  recientes(): Promise<EmisionReciente[]>
  /** Core responde 409 si ya estaba revocada o venció, y 404 si no existe en la comuna. */
  revocar(id: string, motivo: string): Promise<void>
}

export type ApiDelMeson = ApiDeCatalogo & ApiDePlantillas & ApiDeEmision & ApiDeRevocacion

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

    emitir(lineas, consentimiento) {
      return pedir<CodigoEmitido>('/emisiones', {
        method: 'POST',
        body: JSON.stringify(peticionDeEmision(lineas, consentimiento)),
      })
    },

    recientes() {
      return pedir<EmisionReciente[]>('/emisiones/recientes')
    },

    async revocar(id, motivo) {
      await pedir<unknown>(`/emisiones/${encodeURIComponent(id)}/revocacion`, {
        method: 'POST',
        body: JSON.stringify({ motivo }),
      })
    },
  }
}
