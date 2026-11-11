import { useState } from 'react'
import type { ApiDeEmision } from '../api/cliente'
import { CONSENTIMIENTO, type CodigoEmitido, type LineaParaEmitir } from '../dominio/emision'
import { intentosDelNavegador, type RegistroDeIntentos } from '../dominio/intentos'
import { Comprobante } from './Comprobante'

interface Props {
  api: ApiDeEmision
  /** Null mientras falte registrar la posología o la entrega de algún producto. */
  lineas: LineaParaEmitir[] | null
  retiro: string
  /** Hora de la aceptación del consentimiento; las pruebas la fijan. */
  ahora?: () => Date
  /** Dónde queda cada emisión fallida; por omisión, este navegador. */
  intentos?: RegistroDeIntentos
  alTerminar: () => void
}

type Paso =
  | { tipo: 'listo' }
  | { tipo: 'consentimiento' }
  | { tipo: 'emitiendo' }
  | { tipo: 'emitido'; emitido: CodigoEmitido }
  | { tipo: 'rechazado' }
  | { tipo: 'fallo'; mensaje: string }

/**
 * Cierre de la atención: el consentimiento del paciente (HU-05) y la emisión del código con su
 * comprobante (HU-01). Ninguna rama impide entregar el medicamento: si el paciente no acepta o
 * MiDosis no responde, la atención se cierra igual, sin código (RN-11).
 */
export function Emision({
  api,
  lineas,
  retiro,
  ahora = () => new Date(),
  intentos = intentosDelNavegador,
  alTerminar,
}: Props) {
  const [paso, setPaso] = useState<Paso>({ tipo: 'listo' })

  async function emitir(aEmitir: LineaParaEmitir[]) {
    setPaso({ tipo: 'emitiendo' })
    try {
      const emitido = await api.emitir(aEmitir, {
        version: CONSENTIMIENTO.version,
        aceptadoEn: ahora().toISOString(),
      })
      setPaso({ tipo: 'emitido', emitido })
    } catch (error) {
      const mensaje = error instanceof Error ? error.message : 'No se pudo emitir el código.'
      intentos.anotar({
        en: ahora().toISOString(),
        productos: aEmitir.map((l) => l.producto.nombre),
        motivo: mensaje,
      })
      setPaso({ tipo: 'fallo', mensaje })
    }
  }

  if (paso.tipo === 'emitido' && lineas) {
    return <Comprobante emitido={paso.emitido} lineas={lineas} retiro={retiro} alTerminar={alTerminar} />
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-emision">
      <h2 id="titulo-emision">Código de tratamiento</h2>

      {paso.tipo === 'listo' && (
        <>
          {lineas === null && (
            <p className="secundario">Registre la posología y la entrega de cada producto para emitir el código.</p>
          )}
          <button
            type="button"
            className="primario"
            disabled={lineas === null}
            onClick={() => setPaso({ tipo: 'consentimiento' })}
          >
            Emitir código
          </button>
        </>
      )}

      {paso.tipo === 'consentimiento' && lineas && (
        <div role="group" aria-labelledby="titulo-consentimiento" className="consentimiento">
          <h3 id="titulo-consentimiento">Lea al paciente</h3>
          {CONSENTIMIENTO.parrafos.map((p) => (
            <p key={p}>{p}</p>
          ))}
          <p className="secundario">Versión del texto: {CONSENTIMIENTO.version}</p>
          <div className="fila">
            <button type="button" className="primario" onClick={() => void emitir(lineas)}>
              El paciente acepta
            </button>
            <button type="button" onClick={() => setPaso({ tipo: 'rechazado' })}>
              El paciente no acepta
            </button>
          </div>
        </div>
      )}

      {paso.tipo === 'emitiendo' && <p className="secundario">Emitiendo el código…</p>}

      {paso.tipo === 'rechazado' && (
        <>
          <p className="aviso aviso-info" role="status">
            No se emitirá código. Entregue los medicamentos con normalidad.
          </p>
          <button type="button" onClick={alTerminar}>
            Cerrar la atención
          </button>
        </>
      )}

      {paso.tipo === 'fallo' && (
        <>
          <p className="aviso aviso-error" role="alert">
            {paso.mensaje} Entregue los medicamentos con normalidad; el código se puede emitir después. El intento quedó
            anotado en «Códigos emitidos».
          </p>
          <div className="fila">
            {lineas && (
              <button type="button" className="primario" onClick={() => void emitir(lineas)}>
                Intentar de nuevo
              </button>
            )}
            <button type="button" onClick={alTerminar}>
              Cerrar la atención sin código
            </button>
          </div>
        </>
      )}
    </section>
  )
}
