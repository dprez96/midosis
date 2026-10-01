/** Un producto del catálogo de la comuna, tal como lo entrega core. */
export interface Producto {
  gtin: string
  nombre: string
  principioActivo: string
  forma: string
  concentracion: string
}

/** Lo que el auxiliar confirma al identificar el producto (HU-02). */
export function detalleDeProducto(producto: Producto): string {
  return `${producto.principioActivo} · ${producto.forma} · ${producto.concentracion}`
}
