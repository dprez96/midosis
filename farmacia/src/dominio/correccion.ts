/**
 * La corrección de un tratamiento ya cargado (HU-33, RN-25). El QR impreso no se puede retirar
 * del teléfono: se emite un código de reemplazo ligado al original, y la aplicación del
 * paciente, al consultar el estado, sabe si debe seguir o suspender el anterior hasta cargarlo.
 */

/** Qué hace el paciente con el tratamiento anterior hasta cargar el código de reemplazo. */
export type Instruccion = 'continuar' | 'suspender'

export const INSTRUCCIONES: Record<Instruccion, string> = {
  continuar: 'Seguir con el tratamiento anterior hasta cargar el nuevo código',
  suspender: 'Suspender el tratamiento anterior desde ya',
}

/** La corrección elegida en «Corregir un tratamiento», a la espera de los productos corregidos. */
export interface CorreccionEnCurso {
  /** El jti del código que se corrige. */
  original: string
  /** Los productos del código original, para reconocerlo en el mesón. */
  productos: string[]
  motivo: string
  instruccion: Instruccion
}

export const MOTIVO_MINIMO = 3
export const MOTIVO_MAXIMO = 200

const ALFABETO = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

/**
 * La referencia de un código: los últimos 8 caracteres de su jti, en dos grupos de 4 para
 * dictarla o copiarla sin errores. Va impresa en el comprobante y a la vista en la aplicación.
 */
export function referencia(id: string): string {
  const ref = id.slice(-8)
  return `${ref.slice(0, 4)}-${ref.slice(4)}`
}

/**
 * La referencia como la escribió alguien, en mayúsculas y sin guiones ni espacios. Como el
 * alfabeto del ULID, lee la I y la L como 1 y la O como 0. Null si no son 8 caracteres válidos.
 */
export function normalizarReferencia(texto: string): string | null {
  const limpia = texto
    .toUpperCase()
    .replace(/[\s-]/g, '')
    .replace(/[IL]/g, '1')
    .replace(/O/g, '0')
  return limpia.length === 8 && [...limpia].every((c) => ALFABETO.includes(c)) ? limpia : null
}
