import { useEffect, useReducer, useRef, useState } from 'react'
import type { ApiDelMeson } from '../api/cliente'
import { detalleDeProducto, type Producto } from '../dominio/catalogo'
import { leerCodigo } from '../dominio/gtin'
import type { Posologia } from '../dominio/posologia'
import { ATENCION_NUEVA, reducirAtencion, type Linea } from './atencion'
import { BusquedaManual } from './BusquedaManual'
import { LectorDeCodigo } from './LectorDeCodigo'
import { PosologiaDeLinea } from './PosologiaDeLinea'

interface Props {
  api: ApiDelMeson
  /** Solo el químico farmacéutico guarda plantillas; core lo vuelve a verificar. */
  puedeGuardarPlantillas: boolean
}

/**
 * Atención en el mesón: identificar los productos que se entregan (HU-02) y registrar su
 * posología (HU-03).
 */
export function PantallaMeson({ api, puedeGuardarPlantillas }: Props) {
  const [atencion, despachar] = useReducer(reducirAtencion, ATENCION_NUEVA)
  const [busqueda, setBusqueda] = useState<{ codigoNoEncontrado: string | null } | null>(null)
  const [consultas, setConsultas] = useState(0)
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
            {atencion.lineas.map((linea) => (
              <li key={linea.id} className="linea">
                <div className="fila espaciada">
                  <div>
                    <p className="nombre">{linea.producto.nombre}</p>
                    <p>{detalleDeProducto(linea.producto)}</p>
                    <p className="codigo">{linea.producto.gtin}</p>
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
                <PosologiaDeLinea
                  linea={linea}
                  api={api}
                  puedeGuardarPlantilla={puedeGuardarPlantillas}
                  alRegistrar={(posologia, plantillaId) => registrarPosologia(linea, posologia, plantillaId)}
                />
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  )
}
