import qrcode from 'qrcode-generator'

interface Props {
  texto: string
  /** Lo que lee un lector de pantalla en vez del dibujo. */
  descripcion: string
}

/**
 * El QR del comprobante, dibujado como SVG para que se imprima nítido a cualquier tamaño.
 * Corrección de errores M y zona tranquila de 4 módulos, como pide la tabla 87 del informe.
 * El texto del contrato es alfanumérico («MD1:» y Base45), el modo más compacto del QR.
 */
export function CodigoQr({ texto, descripcion }: Props) {
  const qr = qrcode(0, 'M')
  qr.addData(texto, 'Alphanumeric')
  qr.make()
  const n = qr.getModuleCount()
  const margen = 4
  const lado = n + 2 * margen
  let camino = ''
  for (let fila = 0; fila < n; fila++) {
    for (let col = 0; col < n; col++) {
      if (qr.isDark(fila, col)) camino += `M${col + margen} ${fila + margen}h1v1h-1z`
    }
  }
  return (
    <svg
      className="codigo-qr"
      viewBox={`0 0 ${lado} ${lado}`}
      role="img"
      aria-label={descripcion}
      shapeRendering="crispEdges"
    >
      <rect width={lado} height={lado} fill="#fff" />
      <path d={camino} fill="#000" />
    </svg>
  )
}
