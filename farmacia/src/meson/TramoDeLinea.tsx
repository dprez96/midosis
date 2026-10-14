import { useState, type FormEvent } from 'react'
import { UNIDADES, type Posologia } from '../dominio/posologia'
import { calcularCobertura, fechaLegible, validarTramo, type CamposDelTramo, type Tramo } from '../dominio/tramo'
import type { Linea } from './atencion'

interface Props {
  linea: Linea & { posologia: Posologia }
  /** Fecha del retiro, AAAA-MM-DD: la de hoy en el mesón. */
  retiro: string
  alRegistrar: (tramo: Tramo) => void
}

function unidades(posologia: Posologia, cantidad: number): string {
  const nombres = UNIDADES[posologia.unidad]
  return `${cantidad.toLocaleString('es-CL')} ${cantidad === 1 ? nombres.singular : nombres.plural}`
}

function dias(n: number): string {
  return n === 1 ? '1 día' : `${n} días`
}

/**
 * Lo que se entrega en este retiro, distinto de la duración total indicada (HU-04, ADR-010).
 * Muestra hasta cuándo alcanza, para que el paciente sepa cuándo volver.
 */
export function TramoDeLinea({ linea, retiro, alRegistrar }: Props) {
  const { posologia, tramo } = linea
  const [editando, setEditando] = useState(tramo === null)
  const [campos, setCampos] = useState<CamposDelTramo>({
    duracionIndicadaDias: String(tramo?.duracionIndicadaDias ?? posologia.duracionDias ?? ''),
    unidadesEntregadas: tramo ? String(tramo.unidadesEntregadas) : '',
  })
  const [error, setError] = useState<string | null>(null)
  const id = (campo: string) => `linea-${linea.id}-${campo}`
  const nombre = linea.producto.nombre

  function registrar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    const resultado = validarTramo(campos, posologia)
    if ('error' in resultado) {
      setError(resultado.error)
      return
    }
    setError(null)
    setEditando(false)
    alRegistrar(resultado.tramo)
  }

  if (tramo !== null && !editando) {
    const c = calcularCobertura(posologia, tramo, retiro)
    return (
      <div className="tramo" role="group" aria-label={`Entrega de ${nombre}`}>
        <div className="fila espaciada">
          <p>
            <strong>Este retiro cubre {dias(c.coberturaDias)}:</strong> alcanza hasta el {fechaLegible(c.agotamiento)}.
          </p>
          <button type="button" className="enlace" aria-label={`Cambiar la entrega de ${nombre}`} onClick={() => setEditando(true)}>
            Cambiar
          </button>
        </div>
        <p className="secundario">
          Se entregan {unidades(posologia, tramo.unidadesEntregadas)}: {c.dosisEntregadas}{' '}
          {c.dosisEntregadas === 1 ? 'toma' : 'tomas'}.
        </p>
        <p>
          Tratamiento indicado: {dias(tramo.duracionIndicadaDias)}.{' '}
          {c.completaLoIndicado ? 'Este retiro completa lo indicado.' : `Quedan ${dias(c.diasPendientes)} por retirar.`}
        </p>
        {c.excedeLoIndicado && (
          <p className="aviso aviso-advertencia" role="alert">
            Lo entregado cubre más de lo indicado ({c.coberturaDias} de {tramo.duracionIndicadaDias} días). Revise la receta:
            suele ser un error de transcripción.
          </p>
        )}
        {c.unidadesSobrantes > 0 && (
          <p className="aviso aviso-advertencia">
            Sobra {unidades(posologia, c.unidadesSobrantes)}, que no completa una toma.
          </p>
        )}
      </div>
    )
  }

  return (
    <form className="tramo formulario-posologia" onSubmit={registrar} noValidate aria-label={`Entrega de ${nombre}`}>
      <div className="campos">
        <div>
          <label htmlFor={id('duracion-indicada')}>Duración indicada en la receta (días)</label>
          <input
            id={id('duracion-indicada')}
            inputMode="numeric"
            autoComplete="off"
            value={campos.duracionIndicadaDias}
            onChange={(e) => setCampos((c) => ({ ...c, duracionIndicadaDias: e.target.value }))}
          />
        </div>
        <div>
          <label htmlFor={id('entregado')}>Cantidad entregada ({UNIDADES[posologia.unidad].plural})</label>
          <input
            id={id('entregado')}
            inputMode="decimal"
            autoComplete="off"
            value={campos.unidadesEntregadas}
            onChange={(e) => setCampos((c) => ({ ...c, unidadesEntregadas: e.target.value }))}
          />
        </div>
      </div>
      {error && (
        <p className="aviso aviso-error" role="alert">
          {error}
        </p>
      )}
      <div className="fila">
        <button type="submit" className="primario">
          Registrar entrega
        </button>
        {tramo !== null && (
          <button type="button" className="enlace" onClick={() => setEditando(false)}>
            Mantener la actual
          </button>
        )}
      </div>
    </form>
  )
}
