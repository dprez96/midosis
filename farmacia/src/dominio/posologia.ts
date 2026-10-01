/**
 * Posología en campos estructurados (HU-03). Las unidades son la misma lista cerrada que
 * core (UnidadDeDosis); aquí se agregan los nombres para mostrar.
 */

export const UNIDADES = {
  comprimido: { singular: 'comprimido', plural: 'comprimidos' },
  capsula: { singular: 'cápsula', plural: 'cápsulas' },
  ml: { singular: 'ml', plural: 'ml' },
  gota: { singular: 'gota', plural: 'gotas' },
  sobre: { singular: 'sobre', plural: 'sobres' },
  inhalacion: { singular: 'inhalación', plural: 'inhalaciones' },
  aplicacion: { singular: 'aplicación', plural: 'aplicaciones' },
  supositorio: { singular: 'supositorio', plural: 'supositorios' },
  parche: { singular: 'parche', plural: 'parches' },
  ui: { singular: 'UI', plural: 'UI' },
} as const

export type CodigoUnidad = keyof typeof UNIDADES

export function esUnidad(valor: string): valor is CodigoUnidad {
  return Object.hasOwn(UNIDADES, valor)
}

export interface Posologia {
  cantidad: number
  unidad: CodigoUnidad
  /** Intervalo entre tomas en ISO 8601, como lo entrega core: PT8H, PT24H. */
  frecuencia: string
  duracionDias: number | null
  indicaciones: string | null
}

/** Una posología frecuente que el químico farmacéutico dejó configurada en la comuna. */
export interface Plantilla extends Posologia {
  id: string
}

const NUMERO = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 2 })

export function describirDosis(cantidad: number, unidad: CodigoUnidad): string {
  const nombre = cantidad === 1 ? UNIDADES[unidad].singular : UNIDADES[unidad].plural
  return `${NUMERO.format(cantidad)} ${nombre}`
}

/** Horas del intervalo, o null si no tiene la forma PTnH o PTnM que entrega core. */
export function horasDeFrecuencia(iso: string): number | null {
  const partes = /^PT(?:(\d+)H)?(?:(\d+)M)?$/.exec(iso)
  if (!partes || (partes[1] === undefined && partes[2] === undefined)) return null
  return Number(partes[1] ?? 0) + Number(partes[2] ?? 0) / 60
}

export function describirFrecuencia(iso: string): string {
  const horas = horasDeFrecuencia(iso)
  if (horas === null) return `cada ${iso}`
  if (horas >= 48 && horas % 24 === 0) return `cada ${horas / 24} días`
  if (Number.isInteger(horas)) return horas === 1 ? 'cada 1 hora' : `cada ${horas} horas`
  return `cada ${Math.round(horas * 60)} minutos`
}

/** Como se lee en el mesón: «1 comprimido cada 12 horas por 30 días. Con alimentos». */
export function describirPosologia(posologia: Posologia): string {
  let texto = `${describirDosis(posologia.cantidad, posologia.unidad)} ${describirFrecuencia(posologia.frecuencia)}`
  if (posologia.duracionDias !== null) {
    texto += ` por ${posologia.duracionDias} ${posologia.duracionDias === 1 ? 'día' : 'días'}`
  }
  return posologia.indicaciones ? `${texto}. ${posologia.indicaciones}` : texto
}

export type UnidadDeIntervalo = 'horas' | 'dias'

/** El intervalo siempre viaja en horas, como lo normaliza el motor: 1 día es PT24H. */
export function frecuenciaIso(valor: number, unidad: UnidadDeIntervalo): string {
  return `PT${unidad === 'dias' ? valor * 24 : valor}H`
}

/** Lo que se escribe en el formulario, todavía como texto. */
export interface CamposDePosologia {
  cantidad: string
  unidad: CodigoUnidad | ''
  intervalo: string
  unidadDeIntervalo: UnidadDeIntervalo
  duracionDias: string
  indicaciones: string
}

export const CAMPOS_VACIOS: CamposDePosologia = {
  cantidad: '',
  unidad: '',
  intervalo: '',
  unidadDeIntervalo: 'horas',
  duracionDias: '',
  indicaciones: '',
}

/** Mismos límites que core, para avisar antes de enviar. */
export const LIMITES = {
  cantidadMaxima: 999.99,
  horasMaximas: 90 * 24,
  duracionMaximaDias: 365,
  largoIndicaciones: 120,
} as const

function numero(texto: string): number {
  return Number(texto.trim().replace(',', '.'))
}

/** Convierte el formulario en una posología, o explica qué falta. */
export function validarPosologia(campos: CamposDePosologia): { posologia: Posologia } | { error: string } {
  if (campos.cantidad.trim() === '') return { error: 'Indique la cantidad por toma.' }
  const cantidad = numero(campos.cantidad)
  if (!Number.isFinite(cantidad) || cantidad <= 0 || cantidad > LIMITES.cantidadMaxima) {
    return { error: 'La cantidad por toma debe ser mayor que cero y menor que mil.' }
  }
  // Se mira el texto y no el número: 0,29 × 100 no da exacto en coma flotante.
  if (!/^\d+([.,]\d{1,2})?$/.test(campos.cantidad.trim())) {
    return { error: 'La cantidad por toma admite hasta dos decimales.' }
  }
  if (campos.unidad === '') return { error: 'Elija la unidad de la dosis.' }

  if (campos.intervalo.trim() === '') return { error: 'Indique cada cuánto se toma.' }
  const intervalo = numero(campos.intervalo)
  const horas = campos.unidadDeIntervalo === 'dias' ? intervalo * 24 : intervalo
  if (!Number.isInteger(intervalo) || intervalo < 1 || horas > LIMITES.horasMaximas) {
    return { error: 'El intervalo entre tomas va de 1 hora a 90 días, en números enteros.' }
  }

  let duracionDias: number | null = null
  if (campos.duracionDias.trim() !== '') {
    duracionDias = numero(campos.duracionDias)
    if (!Number.isInteger(duracionDias) || duracionDias < 1 || duracionDias > LIMITES.duracionMaximaDias) {
      return { error: `La duración va de 1 a ${LIMITES.duracionMaximaDias} días.` }
    }
  }

  const indicaciones = campos.indicaciones.replace(/\s+/g, ' ').trim()
  if (indicaciones.length > LIMITES.largoIndicaciones) {
    return { error: `Las indicaciones admiten hasta ${LIMITES.largoIndicaciones} caracteres.` }
  }

  return {
    posologia: {
      cantidad,
      unidad: campos.unidad,
      frecuencia: frecuenciaIso(intervalo, campos.unidadDeIntervalo),
      duracionDias,
      indicaciones: indicaciones === '' ? null : indicaciones,
    },
  }
}
