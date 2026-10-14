import type { Producto } from './catalogo'

/**
 * Cómo se nombra cada producto en el comprobante impreso (HU-06, RN-10).
 *
 * El comprobante es un papel que circula: queda en la billetera, en la cocina, en manos
 * de otros. Para un producto sujeto a control legal se imprime una referencia neutra en
 * vez del nombre, que no revela el tratamiento. El contenido cifrado del código sí
 * conserva el nombre, y la aplicación del paciente lo muestra.
 *
 * La referencia lleva el número de la línea, para que el paciente sepa a cuál de sus
 * medicamentos corresponde cada posología impresa.
 */
export function nombreEnComprobante(producto: Producto, numeroDeLinea: number): string {
  return producto.controlLegal === null ? producto.nombre : referenciaNeutra(numeroDeLinea)
}

export function referenciaNeutra(numeroDeLinea: number): string {
  return `Medicamento ${numeroDeLinea} (ver detalle en la aplicación MiDosis)`
}
