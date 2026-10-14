import { UNIDADES, type Posologia } from './posologia'

/**
 * El tramo entregado en este retiro (HU-04, ADR-010). Tres valores distintos que no se
 * deben confundir:
 *
 *  - la duración que indicó el médico («du»),
 *  - las dosis que se entregan hoy, contadas en tomas («e»),
 *  - los días que esas dosis alcanzan a cubrir («co»).
 *
 * El cálculo es el mismo del motor y del teléfono: los tres prueban contra
 * contrato/vectores/cobertura.json, para que el mesón y el paciente vean la misma fecha.
 */

/** Lo que registra el auxiliar: la duración de la receta y lo que entrega, en unidades. */
export interface Tramo {
  duracionIndicadaDias: number
  unidadesEntregadas: number
}

export interface Cobertura {
  /** «e»: tomas completas que alcanzan las unidades entregadas. */
  dosisEntregadas: number
  /** Unidades que sobran y no completan una toma. */
  unidadesSobrantes: number
  /** «co» */
  coberturaDias: number
  /** Fecha en que se agota lo entregado, AAAA-MM-DD. */
  agotamiento: string
  completaLoIndicado: boolean
  diasPendientes: number
  /** Cubre más días de los indicados: suele delatar una receta mal transcrita. */
  excedeLoIndicado: boolean
}

/** Los mismos límites del contrato (carga-v1.cddl). */
export const LIMITES_DEL_TRAMO = {
  duracionMaximaDias: 365,
  dosisMaximas: 9999,
} as const

const MINUTOS_POR_DIA = 24 * 60

/** PT12H son 720 minutos; PT1H30M, 90. Null si no es la forma canónica del contrato. */
export function minutosDeIntervalo(iso: string): number | null {
  const partes = /^PT(?:([1-9]\d*)H)?(?:([1-9]|[1-5]\d)M)?$/.exec(iso)
  if (!partes || (partes[1] === undefined && partes[2] === undefined)) return null
  return Number(partes[1] ?? 0) * 60 + Number(partes[2] ?? 0)
}

/** Días completos que cubren las dosis, redondeando hacia abajo. */
export function coberturaDias(dosisEntregadas: number, intervalo: string): number {
  const minutos = minutosDeIntervalo(intervalo)
  if (minutos === null) throw new Error(`Intervalo no válido: ${intervalo}`)
  return Math.floor((dosisEntregadas * minutos) / MINUTOS_POR_DIA)
}

/**
 * Cuántas tomas completas hay en lo entregado. Se trabaja en centésimas, porque la cantidad
 * por toma admite dos decimales y 0,29 × 100 no da exacto en coma flotante.
 */
export function tomasEnLoEntregado(unidades: number, cantidadPorToma: number): { tomas: number; sobrante: number } {
  const unidadesCentesimas = Math.round(unidades * 100)
  const tomaCentesimas = Math.round(cantidadPorToma * 100)
  const tomas = Math.floor(unidadesCentesimas / tomaCentesimas)
  return { tomas, sobrante: (unidadesCentesimas - tomas * tomaCentesimas) / 100 }
}

/** Suma días a una fecha AAAA-MM-DD, sin que la zona horaria cambie el día. */
export function sumarDias(fecha: string, dias: number): string {
  const [anio = NaN, mes = NaN, dia = NaN] = fecha.split('-').map(Number)
  return new Date(Date.UTC(anio, mes - 1, dia + dias)).toISOString().slice(0, 10)
}

export function cobertura(dosisEntregadas: number, intervalo: string, duracionIndicadaDias: number, retiro: string) {
  const co = coberturaDias(dosisEntregadas, intervalo)
  return {
    coberturaDias: co,
    agotamiento: sumarDias(retiro, co),
    completaLoIndicado: co >= duracionIndicadaDias,
    diasPendientes: Math.max(0, duracionIndicadaDias - co),
    excedeLoIndicado: co > duracionIndicadaDias,
  }
}

export function calcularCobertura(posologia: Posologia, tramo: Tramo, retiro: string): Cobertura {
  const { tomas, sobrante } = tomasEnLoEntregado(tramo.unidadesEntregadas, posologia.cantidad)
  return {
    dosisEntregadas: tomas,
    unidadesSobrantes: sobrante,
    ...cobertura(tomas, posologia.frecuencia, tramo.duracionIndicadaDias, retiro),
  }
}

export interface CamposDelTramo {
  duracionIndicadaDias: string
  unidadesEntregadas: string
}

/** Convierte lo escrito en un tramo, o explica qué falta. */
export function validarTramo(campos: CamposDelTramo, posologia: Posologia): { tramo: Tramo } | { error: string } {
  const duracion = Number(campos.duracionIndicadaDias.trim())
  if (campos.duracionIndicadaDias.trim() === '' || !Number.isInteger(duracion) || duracion < 1 || duracion > LIMITES_DEL_TRAMO.duracionMaximaDias) {
    return { error: `Indique la duración del tratamiento según la receta, de 1 a ${LIMITES_DEL_TRAMO.duracionMaximaDias} días.` }
  }
  const texto = campos.unidadesEntregadas.trim().replace(',', '.')
  const unidades = Number(texto)
  const plural = UNIDADES[posologia.unidad].plural
  if (texto === '' || !Number.isFinite(unidades) || unidades <= 0 || !/^\d+(\.\d{1,2})?$/.test(texto)) {
    return { error: `Indique cuántos ${plural} se entregan.` }
  }
  const { tomas } = tomasEnLoEntregado(unidades, posologia.cantidad)
  if (tomas < 1) return { error: `Lo entregado no alcanza para una toma de ${posologia.cantidad} ${plural}.` }
  if (tomas > LIMITES_DEL_TRAMO.dosisMaximas) {
    return { error: `Lo entregado supera las ${LIMITES_DEL_TRAMO.dosisMaximas} tomas que admite un código.` }
  }
  return { tramo: { duracionIndicadaDias: duracion, unidadesEntregadas: unidades } }
}

const FECHA = new Intl.DateTimeFormat('es-CL', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' })

/** 2026-12-14 se lee 14-12-2026 en es-CL. */
export function fechaLegible(fecha: string): string {
  return FECHA.format(new Date(`${fecha}T00:00:00Z`))
}

/** La fecha de hoy en el equipo del mesón, AAAA-MM-DD. */
export function hoy(): string {
  const ahora = new Date()
  const mes = String(ahora.getMonth() + 1).padStart(2, '0')
  const dia = String(ahora.getDate()).padStart(2, '0')
  return `${ahora.getFullYear()}-${mes}-${dia}`
}
