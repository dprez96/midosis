import type { Producto } from './catalogo'
import type { Posologia } from './posologia'
import type { Tramo } from './tramo'

/** Vías de administración del contrato (carga-v1.cddl, clave «r»). */
export const VIAS = {
  VO: 'Oral',
  SL: 'Sublingual',
  TOP: 'Tópica (piel)',
  TD: 'Transdérmica (parche)',
  OFT: 'Oftálmica',
  OTI: 'Ótica',
  NAS: 'Nasal',
  INH: 'Inhalatoria',
  REC: 'Rectal',
  VAG: 'Vaginal',
  SC: 'Subcutánea',
  IM: 'Intramuscular',
} as const

export type CodigoVia = keyof typeof VIAS

/**
 * La vía que sugiere la forma farmacéutica del catálogo. El auxiliar la confirma o la cambia:
 * es una propuesta para no escribir, no una regla.
 */
export function viaSugerida(forma: string): CodigoVia {
  const f = forma.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
  if (/inhala|aerosol/.test(f)) return 'INH'
  if (/oftalm|colirio/.test(f)) return 'OFT'
  if (/otic/.test(f)) return 'OTI'
  if (/nasal/.test(f)) return 'NAS'
  if (/parche|transderm/.test(f)) return 'TD'
  if (/supositorio/.test(f)) return 'REC'
  if (/ovulo|vaginal/.test(f)) return 'VAG'
  if (/crema|unguento|gel|locion/.test(f)) return 'TOP'
  if (/sublingual/.test(f)) return 'SL'
  if (/inyect|jeringa|lapiz|insulina/.test(f)) return 'SC'
  return 'VO'
}

/**
 * El texto de consentimiento que se lee al paciente antes de emitir (HU-05, RN-11). Cambiar el
 * texto obliga a cambiar la versión, y core solo acepta la vigente.
 */
export const CONSENTIMIENTO = {
  version: '2026-10-v1',
  parrafos: [
    'MiDosis le recuerda en su teléfono cuándo tomar sus medicamentos, con un código que se imprime en este comprobante.',
    'No necesita dar su nombre, RUT, teléfono ni correo. El código no contiene ningún dato suyo y su tratamiento queda guardado solo en su teléfono.',
    'Usar MiDosis es voluntario. Si no acepta, recibe sus medicamentos exactamente igual.',
    'Puede borrar todos sus datos desde la aplicación cuando quiera, sin dar explicaciones.',
  ],
} as const

export interface Consentimiento {
  version: string
  /** Momento de la aceptación, en ISO 8601. */
  aceptadoEn: string
}

/** Una línea lista para emitir: tiene posología y tramo registrados. */
export interface LineaParaEmitir {
  producto: Producto
  posologia: Posologia
  tramo: Tramo
  via: CodigoVia
}

/** El cuerpo que espera POST /api/emisiones. */
export function peticionDeEmision(lineas: LineaParaEmitir[], consentimiento: Consentimiento) {
  return {
    consentimiento,
    lineas: lineas.map(({ producto, posologia, tramo, via }) => ({
      gtin: producto.gtin,
      cantidad: posologia.cantidad,
      unidad: posologia.unidad,
      frecuencia: posologia.frecuencia,
      duracionIndicadaDias: tramo.duracionIndicadaDias,
      unidadesEntregadas: tramo.unidadesEntregadas,
      via,
      indicaciones: posologia.indicaciones,
    })),
  }
}

/** Lo que responde core: el código y su respaldo para cargarlo a mano. */
export interface CodigoEmitido {
  id: string
  codigo: string
  codigoCorto: string
  pin: string
  expiraEn: string
}
