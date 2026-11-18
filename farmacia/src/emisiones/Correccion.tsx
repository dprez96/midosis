import { useState } from 'react'
import type { ApiDeCorreccion, EmisionEncontrada } from '../api/cliente'
import {
  INSTRUCCIONES,
  MOTIVO_MAXIMO,
  MOTIVO_MINIMO,
  normalizarReferencia,
  referencia,
  type CorreccionEnCurso,
  type Instruccion,
} from '../dominio/correccion'

interface Props {
  api: ApiDeCorreccion
  /** Se llama con la corrección lista; el mesón registra entonces los productos corregidos. */
  alIniciar: (correccion: CorreccionEnCurso) => void
}

type Busqueda =
  | { tipo: 'nada' }
  | { tipo: 'buscando' }
  | { tipo: 'resultado'; encontradas: EmisionEncontrada[] }
  | { tipo: 'fallo'; mensaje: string }

const FECHA = new Intl.DateTimeFormat('es-CL', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

/**
 * Corregir un tratamiento que el paciente ya cargó en su teléfono (HU-33, RN-25). Solo el
 * químico farmacéutico. Se busca el código por la referencia del comprobante o de la aplicación,
 * porque un código cargado suele estar ya vencido y no figura entre los emitidos recientes. Se
 * indican el motivo y qué hace el paciente con el tratamiento anterior, y el mesón emite el
 * código de reemplazo con los productos corregidos.
 */
export function Correccion({ api, alIniciar }: Props) {
  const [texto, setTexto] = useState('')
  const [busqueda, setBusqueda] = useState<Busqueda>({ tipo: 'nada' })
  const [elegida, setElegida] = useState<EmisionEncontrada | null>(null)
  const ref = normalizarReferencia(texto)

  async function buscar() {
    if (!ref) return
    setElegida(null)
    setBusqueda({ tipo: 'buscando' })
    try {
      setBusqueda({ tipo: 'resultado', encontradas: await api.buscarPorReferencia(ref) })
    } catch (error) {
      setBusqueda({ tipo: 'fallo', mensaje: error instanceof Error ? error.message : 'No se pudo buscar el código.' })
    }
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-correccion">
      <h1 id="titulo-correccion">Corregir un tratamiento</h1>
      <p className="secundario">
        Para un tratamiento que el paciente ya cargó con una posología equivocada. Pida la referencia del comprobante o
        la que muestra la aplicación junto al medicamento.
      </p>
      <form
        className="fila"
        onSubmit={(e) => {
          e.preventDefault()
          void buscar()
        }}
      >
        <label htmlFor="referencia">Referencia</label>
        <input
          id="referencia"
          value={texto}
          maxLength={12}
          autoComplete="off"
          placeholder="Por ejemplo: 7KQ2-M9XA"
          onChange={(e) => setTexto(e.target.value)}
        />
        <button type="submit" className="primario" disabled={!ref || busqueda.tipo === 'buscando'}>
          Buscar
        </button>
      </form>
      {texto.trim().length >= 8 && !ref && (
        <p className="aviso aviso-error" role="alert">
          La referencia son 8 letras o números, por ejemplo 7KQ2-M9XA.
        </p>
      )}

      <div aria-live="polite">
        {busqueda.tipo === 'buscando' && <p className="secundario">Buscando…</p>}
        {busqueda.tipo === 'fallo' && (
          <p className="aviso aviso-error" role="alert">
            {busqueda.mensaje}
          </p>
        )}
        {busqueda.tipo === 'resultado' && busqueda.encontradas.length === 0 && (
          <p className="aviso aviso-info">
            No hay ningún código de esta farmacia con esa referencia. Revise que esté bien escrita.
          </p>
        )}
      </div>

      {busqueda.tipo === 'resultado' && busqueda.encontradas.length > 0 && (
        <ul className="lineas">
          {busqueda.encontradas.map((e) => (
            <li key={e.id} className="linea">
              <div className="fila espaciada">
                <div>
                  <p className="nombre">{e.productos.join(', ')}</p>
                  <p className="secundario">
                    Referencia {referencia(e.id)} · Emitido el {FECHA.format(new Date(e.emitidaEn))}
                    {e.revocadaEn && ' · Revocado'}
                    {e.corregidaPor && ` · Ya corregido con el código ${referencia(e.corregidaPor)}`}
                  </p>
                </div>
                {!e.corregidaPor && elegida?.id !== e.id && (
                  <button type="button" onClick={() => setElegida(e)}>
                    Corregir este
                  </button>
                )}
              </div>
              {elegida?.id === e.id && (
                <Detalle
                  emision={e}
                  alCancelar={() => setElegida(null)}
                  alContinuar={(motivo, instruccion) =>
                    alIniciar({ original: e.id, productos: e.productos, motivo, instruccion })
                  }
                />
              )}
            </li>
          ))}
        </ul>
      )}
      {busqueda.tipo === 'resultado' && busqueda.encontradas.some((e) => e.corregidaPor) && (
        <p className="secundario">
          Un código ya corregido no se vuelve a corregir: busque el código de reemplazo con su referencia.
        </p>
      )}
    </section>
  )
}

function Detalle({
  emision,
  alCancelar,
  alContinuar,
}: {
  emision: EmisionEncontrada
  alCancelar: () => void
  alContinuar: (motivo: string, instruccion: Instruccion) => void
}) {
  const [motivo, setMotivo] = useState('')
  const [instruccion, setInstruccion] = useState<Instruccion | null>(null)
  const limpio = motivo.trim()
  const valido = limpio.length >= MOTIVO_MINIMO && limpio.length <= MOTIVO_MAXIMO && instruccion !== null

  return (
    <div role="group" aria-label={`Corregir ${emision.productos.join(', ')}`} className="revocacion">
      <label htmlFor={`motivo-correccion-${emision.id}`}>Motivo de la corrección</label>
      <input
        id={`motivo-correccion-${emision.id}`}
        value={motivo}
        maxLength={MOTIVO_MAXIMO}
        placeholder="Por ejemplo: dosis mal transcrita"
        onChange={(e) => setMotivo(e.target.value)}
      />
      <fieldset>
        <legend>Mientras el paciente no cargue el código nuevo</legend>
        {(Object.keys(INSTRUCCIONES) as Instruccion[]).map((i) => (
          <label key={i} className="opcion">
            <input
              type="radio"
              name={`instruccion-${emision.id}`}
              value={i}
              checked={instruccion === i}
              onChange={() => setInstruccion(i)}
            />
            {INSTRUCCIONES[i]}
          </label>
        ))}
      </fieldset>
      <div className="fila">
        <button
          type="button"
          className="primario"
          disabled={!valido}
          onClick={() => instruccion && alContinuar(limpio, instruccion)}
        >
          Registrar los productos corregidos
        </button>
        <button type="button" onClick={alCancelar}>
          Cancelar
        </button>
      </div>
    </div>
  )
}
