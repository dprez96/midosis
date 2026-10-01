import type { ApiDelMeson } from '../api/cliente'
import type { Producto } from '../dominio/catalogo'
import type { Plantilla, Posologia } from '../dominio/posologia'
import type { Perfil, ServicioDeSesion } from '../sesion/sesion'

/** Productos sintéticos, con los mismos GTIN que las pruebas de core. */
export const losartan: Producto = {
  gtin: '7802250012344',
  nombre: 'Losartán 50 mg',
  principioActivo: 'Losartán potásico',
  forma: 'Comprimido',
  concentracion: '50 mg',
}

export const metformina: Producto = {
  gtin: '7802250012405',
  nombre: 'Metformina 850 mg',
  principioActivo: 'Metformina clorhidrato',
  forma: 'Comprimido',
  concentracion: '850 mg',
}

/** Válido, pero ausente de cualquier catálogo de prueba. */
export const CODIGO_FUERA_DEL_CATALOGO = '7802250099901'

export const cadaDoceHoras: Plantilla = {
  id: 'plantilla-1',
  cantidad: 1,
  unidad: 'comprimido',
  frecuencia: 'PT12H',
  duracionDias: 30,
  indicaciones: 'Con alimentos',
}

/**
 * Core en memoria: catálogo y plantillas por GTIN. Anota qué códigos se consultaron y qué
 * plantillas se crearon.
 */
export function catalogoFalso(
  productos: Producto[],
  plantillas: Record<string, Plantilla[]> = {},
): ApiDelMeson & { consultados: string[]; creadas: { gtin: string; posologia: Posologia }[] } {
  const consultados: string[] = []
  const creadas: { gtin: string; posologia: Posologia }[] = []
  return {
    consultados,
    creadas,
    async buscarProducto(gtin) {
      consultados.push(gtin)
      return productos.find((p) => p.gtin === gtin) ?? null
    },
    async buscarPorTexto(texto) {
      const termino = texto.toLowerCase()
      return productos.filter((p) => p.nombre.toLowerCase().includes(termino) || p.gtin.startsWith(termino))
    },
    async listarPlantillas(gtin) {
      return [...(plantillas[gtin] ?? [])]
    },
    async crearPlantilla(gtin, posologia) {
      creadas.push({ gtin, posologia })
      const creada = { ...posologia, id: `creada-${creadas.length}` }
      plantillas[gtin] = [...(plantillas[gtin] ?? []), creada]
      return creada
    },
  }
}

export function sesionFalsa(perfil: Perfil | null): ServicioDeSesion {
  return {
    observar(alCambiar) {
      alCambiar(perfil)
      return () => {}
    },
    iniciar: async () => {},
    cerrar: async () => {},
    token: async () => 'token-de-prueba',
  }
}
