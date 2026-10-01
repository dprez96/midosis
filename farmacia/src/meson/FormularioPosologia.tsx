import { useState, type FormEvent } from 'react'
import {
  CAMPOS_VACIOS,
  LIMITES,
  UNIDADES,
  validarPosologia,
  type CamposDePosologia,
  type CodigoUnidad,
  type Posologia,
  type UnidadDeIntervalo,
} from '../dominio/posologia'

interface Props {
  /** Prefijo para los id de los campos: hay un formulario por producto. */
  idBase: string
  puedeGuardarPlantilla: boolean
  alUsar: (posologia: Posologia) => void
  /** Falla con un mensaje que se puede mostrar, por ejemplo si la plantilla ya existe. */
  alGuardarPlantilla: (posologia: Posologia) => Promise<void>
  alCancelar: () => void
}

/**
 * Posología en campos estructurados, para cuando ninguna plantilla corresponde (HU-03).
 * Se puede usar solo en esta atención o, si es químico farmacéutico, guardar como
 * plantilla comunal.
 */
export function FormularioPosologia({ idBase, puedeGuardarPlantilla, alUsar, alGuardarPlantilla, alCancelar }: Props) {
  const [campos, setCampos] = useState<CamposDePosologia>(CAMPOS_VACIOS)
  const [error, setError] = useState<string | null>(null)
  const [guardando, setGuardando] = useState(false)

  function cambiar<K extends keyof CamposDePosologia>(campo: K, valor: CamposDePosologia[K]) {
    setCampos((actuales) => ({ ...actuales, [campo]: valor }))
  }

  function validar(): Posologia | null {
    const resultado = validarPosologia(campos)
    if ('error' in resultado) {
      setError(resultado.error)
      return null
    }
    setError(null)
    return resultado.posologia
  }

  function usar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const posologia = validar()
    if (posologia) alUsar(posologia)
  }

  async function guardar() {
    const posologia = validar()
    if (!posologia) return
    setGuardando(true)
    try {
      await alGuardarPlantilla(posologia)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo guardar la plantilla.')
    } finally {
      setGuardando(false)
    }
  }

  const id = (campo: string) => `${idBase}-${campo}`

  return (
    <form className="formulario-posologia" onSubmit={usar} noValidate aria-label="Otra posología">
      <div className="campos">
        <div>
          <label htmlFor={id('cantidad')}>Cantidad por toma</label>
          <input
            id={id('cantidad')}
            inputMode="decimal"
            autoComplete="off"
            value={campos.cantidad}
            onChange={(e) => cambiar('cantidad', e.target.value)}
            autoFocus
          />
        </div>
        <div>
          <label htmlFor={id('unidad')}>Unidad</label>
          <select
            id={id('unidad')}
            value={campos.unidad}
            onChange={(e) => cambiar('unidad', e.target.value as CodigoUnidad | '')}
          >
            <option value="">Elija…</option>
            {Object.entries(UNIDADES).map(([codigo, nombres]) => (
              <option key={codigo} value={codigo}>
                {nombres.singular}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={id('intervalo')}>Cada</label>
          <div className="fila">
            <input
              id={id('intervalo')}
              inputMode="numeric"
              autoComplete="off"
              value={campos.intervalo}
              onChange={(e) => cambiar('intervalo', e.target.value)}
            />
            <select
              aria-label="Unidad del intervalo"
              value={campos.unidadDeIntervalo}
              onChange={(e) => cambiar('unidadDeIntervalo', e.target.value as UnidadDeIntervalo)}
            >
              <option value="horas">horas</option>
              <option value="dias">días</option>
            </select>
          </div>
        </div>
        <div>
          <label htmlFor={id('duracion')}>Duración en días (opcional)</label>
          <input
            id={id('duracion')}
            inputMode="numeric"
            autoComplete="off"
            value={campos.duracionDias}
            onChange={(e) => cambiar('duracionDias', e.target.value)}
          />
        </div>
      </div>
      <div>
        <label htmlFor={id('indicaciones')}>Indicaciones (opcional)</label>
        <input
          id={id('indicaciones')}
          maxLength={LIMITES.largoIndicaciones}
          placeholder="Por ejemplo: con alimentos"
          value={campos.indicaciones}
          onChange={(e) => cambiar('indicaciones', e.target.value)}
        />
      </div>

      {error && (
        <p className="aviso aviso-error" role="alert">
          {error}
        </p>
      )}

      <div className="fila">
        <button type="submit" className="primario">
          Usar en esta atención
        </button>
        {puedeGuardarPlantilla && (
          <button type="button" onClick={() => void guardar()} disabled={guardando}>
            {guardando ? 'Guardando…' : 'Guardar como plantilla comunal'}
          </button>
        )}
        <button type="button" className="enlace" onClick={alCancelar}>
          Cancelar
        </button>
      </div>
      {!puedeGuardarPlantilla && (
        <p className="secundario">Solo el químico farmacéutico puede guardarla como plantilla de la comuna.</p>
      )}
    </form>
  )
}
