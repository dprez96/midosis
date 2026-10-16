import { nombreEnComprobante } from '../dominio/comprobante'
import type { CodigoEmitido, LineaParaEmitir } from '../dominio/emision'
import { describirPosologia, UNIDADES } from '../dominio/posologia'
import { calcularCobertura, fechaLegible } from '../dominio/tramo'
import { CodigoQr } from './CodigoQr'

interface Props {
  emitido: CodigoEmitido
  lineas: LineaParaEmitir[]
  /** Fecha del retiro, AAAA-MM-DD. */
  retiro: string
  alTerminar: () => void
}

const VENCE = new Intl.DateTimeFormat('es-CL', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

/**
 * El comprobante que se imprime y se entrega con el medicamento (HU-01, tabla 87 del informe):
 * el QR, el código corto y el PIN de respaldo (HU-10), y cada producto con su posología en texto
 * legible. Un producto con control legal figura con una referencia neutra (HU-06). El nombre
 * del paciente se escribe a mano en «Para:» y nunca se digita (RN-01).
 */
export function Comprobante({ emitido, lineas, retiro, alTerminar }: Props) {
  return (
    <section className="tarjeta comprobante" aria-labelledby="titulo-comprobante">
      <h2 id="titulo-comprobante">Comprobante de tratamiento · MiDosis</h2>
      <p className="para">Para: ______________________________</p>
      <div className="qr-y-respaldo">
        <CodigoQr texto={emitido.codigo} descripcion="Código QR del tratamiento" />
        <div>
          <p>Escanee este código con la aplicación MiDosis.</p>
          <p>
            Si no se puede leer, ingrese el código <strong className="codigo-corto">{emitido.codigoCorto}</strong> y el
            PIN <strong className="codigo-corto">{emitido.pin}</strong>.
          </p>
          <p className="secundario">Cárguelo antes del {VENCE.format(new Date(emitido.expiraEn))}.</p>
        </div>
      </div>
      <ol className="lineas-comprobante">
        {lineas.map((linea, i) => {
          const c = calcularCobertura(linea.posologia, linea.tramo, retiro)
          const unidades = UNIDADES[linea.posologia.unidad]
          return (
            <li key={linea.producto.gtin}>
              <p className="nombre">{nombreEnComprobante(linea.producto, i + 1)}</p>
              <p>{describirPosologia(linea.posologia)}</p>
              <p className="secundario">
                Entregado: {linea.tramo.unidadesEntregadas.toLocaleString('es-CL')}{' '}
                {linea.tramo.unidadesEntregadas === 1 ? unidades.singular : unidades.plural}, alcanza hasta el{' '}
                {fechaLegible(c.agotamiento)}.
              </p>
            </li>
          )
        })}
      </ol>
      <p className="leyenda">Ante cualquier duda, consulte a su médico o químico farmacéutico.</p>
      <div className="fila no-imprimir">
        <button type="button" className="primario" onClick={() => window.print()}>
          Imprimir comprobante
        </button>
        <button type="button" onClick={alTerminar}>
          Nueva atención
        </button>
      </div>
    </section>
  )
}
