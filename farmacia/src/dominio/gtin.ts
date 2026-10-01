/**
 * Lectura del código del envase (HU-02).
 *
 * El lector se conecta como teclado (HID): «escribe» el código y termina con Enter. La
 * aplicación no distingue si lo escribió el lector o una persona, así que valida igual las
 * dos cosas. El dígito verificador se comprueba antes de consultar el catálogo: un código
 * mal leído no debe llegar a convertirse en un tratamiento.
 */

const LARGOS_VALIDOS = new Set([8, 12, 13, 14])

/** Prefijo de simbología que algunos lectores anteponen, como ]E0 (EAN) o ]d2 (DataMatrix). */
const PREFIJO_DE_SIMBOLOGIA = /^\][A-Za-z][0-9]/

/** Separador de campos GS1 (FNC1), que el lector transmite como el carácter 29. */
const SEPARADOR_GS1 = String.fromCharCode(29)

export type LecturaDeCodigo =
  | { tipo: 'gtin'; gtin: string }
  | { tipo: 'invalido'; motivo: string }

/** Módulo 10 con pesos 3 y 1 alternados desde la derecha, sin contar el verificador. */
export function verificadorCorrecto(codigo: string): boolean {
  const digitos = [...codigo].map(Number)
  const declarado = digitos.pop()
  const suma = digitos.reverse().reduce((total, d, i) => total + d * (i % 2 === 0 ? 3 : 1), 0)
  return declarado === (10 - (suma % 10)) % 10
}

/**
 * Interpreta lo que llegó del lector. Acepta el GTIN tal cual (8, 12, 13 o 14 dígitos), y
 * también la cadena GS1 de un DataMatrix, de la que toma el GTIN que sigue al
 * identificador de aplicación 01.
 *
 * Un GTIN-14 que empieza con cero corresponde a la unidad de venta de un EAN-13 y se
 * entrega con 13 dígitos, que es como se carga el catálogo.
 */
export function leerCodigo(entrada: string): LecturaDeCodigo {
  let codigo = entrada.trim().replace(PREFIJO_DE_SIMBOLOGIA, '').replaceAll(SEPARADOR_GS1, '').replace(/\s+/g, '')

  if (codigo.length >= 16 && /^01\d{14}/.test(codigo)) {
    codigo = codigo.slice(2, 16)
  }

  if (codigo === '') {
    return { tipo: 'invalido', motivo: 'No llegó ningún código. Vuelva a escanear el envase.' }
  }
  if (!/^\d+$/.test(codigo)) {
    return { tipo: 'invalido', motivo: 'El código leído tiene caracteres que no son dígitos. Vuelva a escanear.' }
  }
  if (!LARGOS_VALIDOS.has(codigo.length)) {
    return {
      tipo: 'invalido',
      motivo: `Un código de producto tiene 8, 12, 13 o 14 dígitos y se leyeron ${codigo.length}. Vuelva a escanear.`,
    }
  }
  if (!verificadorCorrecto(codigo)) {
    return { tipo: 'invalido', motivo: 'El dígito verificador no corresponde: el código se leyó mal. Vuelva a escanear.' }
  }
  if (codigo.length === 14 && codigo.startsWith('0')) {
    codigo = codigo.slice(1)
  }
  return { tipo: 'gtin', gtin: codigo }
}
