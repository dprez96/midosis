/**
 * Régimen de control legal (RN-10): estupefacientes del DS 404 y psicotrópicos del DS 405,
 * ambos de 1983. El comprobante impreso no muestra el nombre de estos productos (HU-06).
 */
export type ControlLegal = 'DS404' | 'DS405'

/** Un producto del catálogo de la comuna, tal como lo entrega core. */
export interface Producto {
  gtin: string
  nombre: string
  principioActivo: string
  forma: string
  concentracion: string
  /** Null si no está sujeto a control legal. */
  controlLegal: ControlLegal | null
}

/** Lo que el auxiliar confirma al identificar el producto (HU-02). */
export function detalleDeProducto(producto: Producto): string {
  return `${producto.principioActivo} · ${producto.forma} · ${producto.concentracion}`
}

export function describirControlLegal(control: ControlLegal): string {
  return control === 'DS404' ? 'Estupefaciente, DS 404 de 1983' : 'Psicotrópico, DS 405 de 1983'
}
