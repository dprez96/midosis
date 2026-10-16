import { useEffect, useReducer, useRef, useState } from 'react'
import type { ApiDelMeson } from '../api/cliente'
import { describirControlLegal, detalleDeProducto, type Producto } from '../dominio/catalogo'
import { referenciaNeutra } from '../dominio/comprobante'
import { leerCodigo } from '../dominio/gtin'
import type { Posologia } from '../dominio/posologia'
import { ATENCION_NUEVA, reducirAtencion, type Linea } from './atencion'
import { BusquedaManual } from './BusquedaManual'
import { LectorDeCodigo } from './LectorDeCodigo'
import { PosologiaDeLinea } from './PosologiaDeLinea'
import { TramoDeLinea } from './TramoDeLinea'
import { Emision } from './Emision'
import { VIAS, type CodigoVia, type LineaParaEmitir } from '../dominio/emision'
import { hoy as fechaDeHoy, type Tramo } from '../dominio/tramo'

interface Props {
  api: ApiDelMeson
  /** Solo el químico farmacéutico guarda plantillas; core lo vuelve a verificar. */
  puedeGuardarPlantillas: boolean
  /** La fecha del retiro, AAAA-MM-DD. Por omisión, la de hoy; las pruebas la fijan. */
  hoy?: () => string
}

/**
 * Atención en el mesón: identificar los productos que se entregan (HU-02), registrar su
 * posología (HU-03) y lo que se entrega en este retiro (HU-04).
 */
export function PantallaMeson({ api, puedeGuardarPlantillas, hoy = fechaDeHoy }: Props) {
  const [atencion, despachar] = useReducer(reducirAtencion, ATENCION_NUEVA)
  const [busqueda, setBusqueda] = useState<{ codigoNoEncontrado: string | null } | null>(null)
  const [consultas, setConsultas] = useState(0)
  // Cambia con cada atención nueva, para que la emisión de la anterior no quede a la vista.
  const [numeroDeAtencion, setNumeroDeAtencion] = useState(1)
  const lector = useRef<HTMLInputElement>(null)

  /** El campo del lector siempre existe; devolverle el foco deja listo el siguiente envase. */
  function volverAlLector() {
    lector.current?.focus()
  }

  // Si el foco quedó en la página, por ejemplo tras un clic en un espacio vacío, lo que
  // escribe el lector se perdería. La primera tecla lo devuelve al campo del código.
  useEffect(() => {
    function redirigir(evento: KeyboardEvent) {
      const enfocado = document.activeElement
      const sinFoco = enfocado === null || enfocado === document.body
      if (sinFoco && evento.key.length === 1 && !evento.ctrlKey && !evento.metaKey && !evento.altKey) {
        lector.current?.focus()
      }
    }
    document.addEventListener('keydown', redirigir, true)
    return () => document.removeEventListener('keydown', redirigir, true)
  }, [])

  function registrarTramo(linea: Linea, tramo: Tramo) {
    despachar({ tipo: 'tramo', id: linea.id, tramo })
    volverAlLector()
  }

  function nuevaAtencion() {
    despachar({ tipo: 'nueva' })
    setNumeroDeAtencion((n) => n + 1)
    volverAlLector()
  }

  /** Las líneas listas para emitir, o null mientras a alguna le falte posología o entrega. */
  const paraEmitir: LineaParaEmitir[] | null = atencion.lineas.every((l) => l.posologia !== null && l.tramo !== null)
    ? atencion.lineas.map((l) => ({ producto: l.producto, posologia: l.posologia!, tramo: l.tramo!, via: l.via }))
    : null

  function registrarPosologia(linea: Linea, posologia: Posologia, plantillaId: string | null) {
    despachar({ tipo: 'posologia', id: linea.id, posologia, plantillaId })
    despachar({ tipo: 'avisar', aviso: { tipo: 'exito', texto: `Posología registrada para ${linea.producto.nombre}.` } })
    volverAlLector()
  }

  async function leer(texto: string) {
    const lectura = leerCodigo(texto)
    if (lectura.tipo === 'invalido') {
      despachar({ tipo: 'avisar', aviso: { tipo: 'error', texto: lectura.motivo } })
      return
    }
    setConsultas((n) => n + 1)
    try {
      const producto = await api.buscarProducto(lectura.gtin)
      if (producto) {
        despachar({ tipo: 'identificado', producto })
        setBusqueda(null)
        volverAlLector()
      } else {
        despachar({
          tipo: 'avisar',
          aviso: {
            tipo: 'error',
            texto: `El código ${lectura.gtin} no está en el catálogo de la comuna. Búsquelo por nombre o principio activo.`,
          },
        })
        setBusqueda({ codigoNoEncontrado: lectura.gtin })
      }
    } catch (error) {
      despachar({
        tipo: 'avisar',
        aviso: { tipo: 'error', texto: error instanceof Error ? error.message : 'No se pudo consultar el catálogo.' },
      })
    } finally {
      setConsultas((n) => n - 1)
    }
  }

  function elegir(producto: Producto) {
    despachar({ tipo: 'identificado', producto })
    setBusqueda(null)
    volverAlLector()
  }

  return (
    <div className="meson">
      <section className="tarjeta" aria-label="Identificar producto">
        <LectorDeCodigo alLeer={leer} ref={lector} />
        <button type="button" className="enlace" onClick={() => setBusqueda({ codigoNoEncontrado: null })}>
          Buscar por nombre
        </button>
      </section>

      <div className="estado" aria-live="polite">
        {consultas > 0 ? (
          <p className="secundario">Consultando el catálogo…</p>
        ) : (
          atencion.aviso && <p className={`aviso aviso-${atencion.aviso.tipo}`}>{atencion.aviso.texto}</p>
        )}
      </div>

      {busqueda && (
        <BusquedaManual
          api={api}
          codigoNoEncontrado={busqueda.codigoNoEncontrado}
          alElegir={elegir}
          alLeerCodigo={leer}
          alCerrar={() => {
            setBusqueda(null)
            volverAlLector()
          }}
        />
      )}

      <section className="tarjeta" aria-labelledby="titulo-lineas">
        <h2 id="titulo-lineas">Productos de esta atención</h2>
        {atencion.lineas.length === 0 ? (
          <p className="secundario">Aún no hay productos. Escanee el primer envase.</p>
        ) : (
          <ol className="lineas">
            {atencion.lineas.map((linea, indice) => (
              <li key={linea.id} className="linea">
                <div className="fila espaciada">
                  <div>
                    <p className="nombre">{linea.producto.nombre}</p>
                    <p>{detalleDeProducto(linea.producto)}</p>
                    <p className="codigo">{linea.producto.gtin}</p>
                    {linea.producto.controlLegal && (
                      <p className="aviso aviso-info">
                        <strong>{describirControlLegal(linea.producto.controlLegal)}.</strong> En el comprobante
                        figurará como «{referenciaNeutra(indice + 1)}», sin su nombre.
                      </p>
                    )}
                  </div>
                  <button
                    type="button"
                    className="enlace"
                    aria-label={`Quitar ${linea.producto.nombre}`}
                    onClick={() => {
                      despachar({ tipo: 'quitar', id: linea.id })
                      volverAlLector()
                    }}
                  >
                    Quitar
                  </button>
                </div>
                <div className="via">
                  <label htmlFor={`linea-${linea.id}-via`}>Vía de administración</label>
                  <select
                    id={`linea-${linea.id}-via`}
                    value={linea.via}
                    onChange={(e) => despachar({ tipo: 'via', id: linea.id, via: e.target.value as CodigoVia })}
                  >
                    {Object.entries(VIAS).map(([codigo, nombre]) => (
                      <option key={codigo} value={codigo}>
                        {nombre}
                      </option>
                    ))}
                  </select>
                </div>
                <PosologiaDeLinea
                  linea={linea}
                  api={api}
                  puedeGuardarPlantilla={puedeGuardarPlantillas}
                  alRegistrar={(posologia, plantillaId) => registrarPosologia(linea, posologia, plantillaId)}
                />
                {linea.posologia !== null && (
                  <TramoDeLinea
                    linea={{ ...linea, posologia: linea.posologia }}
                    retiro={hoy()}
                    alRegistrar={(tramo) => registrarTramo(linea, tramo)}
                  />
                )}
              </li>
            ))}
          </ol>
        )}
      </section>

      {atencion.lineas.length > 0 && (
        <Emision
          key={numeroDeAtencion}
          api={api}
          lineas={paraEmitir}
          retiro={hoy()}
          alTerminar={nuevaAtencion}
        />
      )}
    </div>
  )
}
