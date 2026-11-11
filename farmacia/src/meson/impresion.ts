/**
 * Preparación de la hoja para la impresora térmica del mesón (HU-01).
 *
 * Mientras se imprime, el documento lleva la clase «imprimiendo»: los estilos dejan a la vista
 * solo el comprobante y ocultan el resto con display (no con visibility, que conserva el espacio
 * y agregaba hojas en blanco). Además, la hoja se ajusta al largo real del comprobante, para que
 * el controlador de la impresora no alimente papel de más.
 */

/**
 * Ancho de la hoja. Es el del papel «82(80) mm» del controlador de la Xprinter del mesón: con la
 * hoja de 80 mm el navegador no encontraba ese papel y usaba el rollo completo (unos 3 m).
 */
export const ANCHO_ROLLO_MM = 82
/** Papel que se deja bajo el comprobante para cortarlo sin tocar el texto. */
export const MARGEN_INFERIOR_MM = 10
/** Por debajo de este largo la medición no es confiable y se deja la hoja del controlador. */
const ALTO_MINIMO_MM = 40
const PX_POR_MM = 96 / 25.4

export const CLASE_IMPRIMIENDO = 'imprimiendo'
const ID_ESTILO = 'hoja-del-comprobante'

/**
 * Deja el documento listo para imprimir el comprobante. Devuelve el largo de la hoja en
 * milímetros, o null si no se pudo medir y se usa la del controlador.
 */
export function prepararImpresion(comprobante: HTMLElement, documento: Document = document): number | null {
  documento.documentElement.classList.add(CLASE_IMPRIMIENDO)
  // Se mide con la clase ya puesta: el comprobante tiene el ancho y la letra de la impresión.
  const alto = Math.ceil(comprobante.getBoundingClientRect().height / PX_POR_MM) + MARGEN_INFERIOR_MM
  documento.getElementById(ID_ESTILO)?.remove()
  if (alto < ALTO_MINIMO_MM) return null
  const estilo = documento.createElement('style')
  estilo.id = ID_ESTILO
  estilo.textContent = `@page { size: ${ANCHO_ROLLO_MM}mm ${alto}mm; margin: 0; }`
  documento.head.appendChild(estilo)
  return alto
}

/** Devuelve la pantalla a su estado normal después de imprimir. */
export function terminarImpresion(documento: Document = document) {
  documento.documentElement.classList.remove(CLASE_IMPRIMIENDO)
  documento.getElementById(ID_ESTILO)?.remove()
}
