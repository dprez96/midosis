import type { ApiDeCatalogo } from '../api/cliente'
import type { Producto } from '../dominio/catalogo'
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

/** Catálogo en memoria que además anota qué códigos se consultaron. */
export function catalogoFalso(productos: Producto[]): ApiDeCatalogo & { consultados: string[] } {
  const consultados: string[] = []
  return {
    consultados,
    async buscarProducto(gtin) {
      consultados.push(gtin)
      return productos.find((p) => p.gtin === gtin) ?? null
    },
    async buscarPorTexto(texto) {
      const termino = texto.toLowerCase()
      return productos.filter((p) => p.nombre.toLowerCase().includes(termino) || p.gtin.startsWith(termino))
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
