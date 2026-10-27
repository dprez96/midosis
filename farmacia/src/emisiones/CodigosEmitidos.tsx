import { useEffect, useState } from 'react'
import type { ApiDeRevocacion, EmisionReciente } from '../api/cliente'
import { intentosDelNavegador, type RegistroDeIntentos } from '../dominio/intentos'

interface Props {
  api: ApiDeRevocacion
  /** Las emisiones que fallaron en este navegador; las pruebas lo reemplazan. */
  intentos?: RegistroDeIntentos
}

type Carga = { tipo: 'cargando' } | { tipo: 'lista'; emisiones: EmisionReciente[] } | { tipo: 'fallo'; mensaje: string }

const HORA = new Intl.DateTimeFormat('es-CL', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

export const MOTIVO_MINIMO = 3
export const MOTIVO_MAXIMO = 200

/**
 * Los códigos que emitió la farmacia y que todavía se pueden canjear, para revocar uno emitido
 * por error (HU-07, RF-F-13). La revocación invalida el canje en línea con el código corto y
 * queda registrada con quién, cuándo y por qué.
 */
export function CodigosEmitidos({ api, intentos = intentosDelNavegador }: Props) {
  const [carga, setCarga] = useState<Carga>({ tipo: 'cargando' })
  const [aviso, setAviso] = useState<string | null>(null)

  // Cada cambio vuelve a pedir la lista a core.
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let vigente = true
    api.recientes().then(
      (emisiones) => {
        if (vigente) setCarga({ tipo: 'lista', emisiones })
      },
      (error: unknown) => {
        if (vigente) {
          setCarga({
            tipo: 'fallo',
            mensaje: error instanceof Error ? error.message : 'No se pudieron cargar los códigos.',
          })
        }
      },
    )
    return () => {
      vigente = false
    }
  }, [api, version])

  function cargar() {
    setVersion((v) => v + 1)
  }

  async function alRevocar() {
    setAviso('El código quedó revocado.')
    cargar()
  }

  return (
    <>
      <section className="tarjeta" aria-labelledby="titulo-emitidos">
        <div className="fila espaciada">
          <h1 id="titulo-emitidos">Códigos emitidos</h1>
          <button type="button" onClick={cargar}>
            Actualizar
          </button>
        </div>
        <p className="secundario">
          Revoque un código emitido por error mientras no venza. El código corto deja de funcionar. Si el paciente ya
          escaneó el QR, el tratamiento quedó en su teléfono: corríjalo con un código nuevo.
        </p>
        <p className="aviso aviso-exito estado" role="status">
          {aviso}
        </p>
        {carga.tipo === 'cargando' && <p className="secundario">Cargando los códigos…</p>}
        {carga.tipo === 'fallo' && (
          <p className="aviso aviso-error" role="alert">
            {carga.mensaje}
          </p>
        )}
        {carga.tipo === 'lista' && carga.emisiones.length === 0 && (
          <p className="secundario">No hay códigos vigentes en las últimas 72 horas.</p>
        )}
        {carga.tipo === 'lista' && carga.emisiones.length > 0 && (
          <ul className="lineas">
            {carga.emisiones.map((e) => (
              <Emitida key={e.id} emision={e} api={api} alRevocar={alRevocar} limpiarAviso={() => setAviso(null)} />
            ))}
          </ul>
        )}
      </section>
      <IntentosFallidos registro={intentos} />
    </>
  )
}

/**
 * Las emisiones que fallaron porque MiDosis no respondió (HU-01): a esas atenciones les faltó el
 * código. Cuando el paciente vuelva, se emite en una atención nueva y el intento se descarta.
 */
function IntentosFallidos({ registro }: { registro: RegistroDeIntentos }) {
  const [intentos, setIntentos] = useState(() => registro.listar())
  if (intentos.length === 0) return null

  function descartar(id: string) {
    registro.descartar(id)
    setIntentos(registro.listar())
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-intentos">
      <h2 id="titulo-intentos">Emisiones que fallaron</h2>
      <p className="secundario">
        MiDosis no respondió y estas atenciones se cerraron sin código. Cuando el paciente vuelva, emítalo en una
        atención nueva y descarte el intento.
      </p>
      <ul className="lineas">
        {intentos.map((i) => (
          <li key={i.id} className="linea">
            <div className="fila espaciada">
              <div>
                <p className="nombre">{i.productos.join(', ')}</p>
                <p className="secundario">
                  {HORA.format(new Date(i.en))} · {i.motivo}
                </p>
              </div>
              <button type="button" onClick={() => descartar(i.id)}>
                Descartar
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}

function Emitida({
  emision,
  api,
  alRevocar,
  limpiarAviso,
}: {
  emision: EmisionReciente
  api: ApiDeRevocacion
  alRevocar: () => Promise<void>
  limpiarAviso: () => void
}) {
  const [abierta, setAbierta] = useState(false)
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const limpio = motivo.trim()
  const valido = limpio.length >= MOTIVO_MINIMO && limpio.length <= MOTIVO_MAXIMO
  const titulo = emision.productos.join(', ')

  async function revocar() {
    setEnviando(true)
    setError(null)
    try {
      await api.revocar(emision.id, limpio)
      await alRevocar()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo revocar el código.')
      setEnviando(false)
    }
  }

  return (
    <li className="linea">
      <div className="fila espaciada">
        <div>
          <p className="nombre">{titulo}</p>
          <p className="secundario">
            Emitido el {HORA.format(new Date(emision.emitidaEn))}
            {emision.revocadaEn
              ? ` · Revocado el ${HORA.format(new Date(emision.revocadaEn))}`
              : ` · Vence el ${HORA.format(new Date(emision.expiraEn))}`}
          </p>
        </div>
        {!emision.revocadaEn && !abierta && (
          <button
            type="button"
            onClick={() => {
              limpiarAviso()
              setAbierta(true)
            }}
          >
            Revocar
          </button>
        )}
      </div>
      {abierta && (
        <div role="group" aria-label={`Revocar el código de ${titulo}`} className="revocacion">
          <label htmlFor={`motivo-${emision.id}`}>Motivo de la revocación</label>
          <input
            id={`motivo-${emision.id}`}
            value={motivo}
            maxLength={MOTIVO_MAXIMO}
            placeholder="Por ejemplo: posología mal registrada"
            onChange={(e) => setMotivo(e.target.value)}
          />
          {error && (
            <p className="aviso aviso-error" role="alert">
              {error}
            </p>
          )}
          <div className="fila">
            <button type="button" className="primario" disabled={!valido || enviando} onClick={() => void revocar()}>
              {enviando ? 'Revocando…' : 'Confirmar la revocación'}
            </button>
            <button type="button" disabled={enviando} onClick={() => setAbierta(false)}>
              Cancelar
            </button>
          </div>
        </div>
      )}
    </li>
  )
}
