/**
 * Las emisiones que fallaron porque MiDosis no respondió (HU-01, segundo criterio). Si core no
 * contesta, no hay dónde guardar el intento en el servidor: queda en este navegador, para que el
 * mesón sepa a qué atenciones les faltó el código y pueda emitirlo cuando el paciente vuelva.
 *
 * Se guarda la hora, los productos y el motivo; nunca un dato del paciente, que el mesón no
 * registra (RN-01).
 */
export interface IntentoFallido {
  id: string
  /** ISO 8601. */
  en: string
  productos: string[]
  motivo: string
}

export interface RegistroDeIntentos {
  listar(): IntentoFallido[]
  anotar(intento: Omit<IntentoFallido, 'id'>): void
  descartar(id: string): void
}

const CLAVE = 'midosis.intentos-fallidos'
/** Basta para un turno largo sin conexión; los más antiguos se descartan. */
export const MAXIMO_DE_INTENTOS = 50

/**
 * Registro en el almacenamiento del navegador. Si el navegador no lo permite (modo privado,
 * datos bloqueados), funciona igual en memoria: perder el registro no puede impedir la
 * atención.
 */
export function registroLocal(
  almacen: () => Storage | null = () => globalThis.localStorage ?? null,
): RegistroDeIntentos {
  let enMemoria: IntentoFallido[] = []

  function leer(): IntentoFallido[] {
    try {
      const almacenamiento = almacen()
      // La memoria es solo el respaldo de un navegador que no deja guardar.
      if (!almacenamiento) return enMemoria
      const texto = almacenamiento.getItem(CLAVE)
      return texto ? (JSON.parse(texto) as IntentoFallido[]) : []
    } catch {
      return enMemoria
    }
  }

  function guardar(intentos: IntentoFallido[]) {
    enMemoria = intentos
    try {
      almacen()?.setItem(CLAVE, JSON.stringify(intentos))
    } catch {
      // Sin almacenamiento queda en memoria hasta que se cierre la pestaña.
    }
  }

  return {
    listar: () => leer(),
    anotar(intento) {
      const nuevo = {
        ...intento,
        id: `${intento.en}-${Math.random().toString(36).slice(2, 8)}`,
      }
      guardar([nuevo, ...leer()].slice(0, MAXIMO_DE_INTENTOS))
    },
    descartar(id) {
      guardar(leer().filter((i) => i.id !== id))
    },
  }
}

/** El registro de este navegador, compartido por el mesón y «Códigos emitidos». */
export const intentosDelNavegador = registroLocal()
