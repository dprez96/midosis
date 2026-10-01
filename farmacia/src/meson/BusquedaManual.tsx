import { useEffect, useState } from 'react'
import type { ApiDeCatalogo } from '../api/cliente'
import { detalleDeProducto, type Producto } from '../dominio/catalogo'
import { leerCodigo } from '../dominio/gtin'

/** Lo mismo que exige core para buscar. */
const LARGO_MINIMO = 2

/** Pausa tras la última tecla antes de consultar, para no pedir una búsqueda por letra. */
const ESPERA_MS = 250

interface Props {
  api: ApiDeCatalogo
  /** El código que no se encontró en el catálogo, si la búsqueda se abrió por eso. */
  codigoNoEncontrado: string | null
  alElegir: (producto: Producto) => void
  /** Un código completo escrito o escaneado aquí se procesa como una lectura del lector. */
  alLeerCodigo: (texto: string) => void
  alCerrar: () => void
}

type Resultado = { termino: string; productos: Producto[] } | { termino: string; error: string }

/**
 * Búsqueda manual asistida (HU-02): cuando el código no está en el catálogo o el envase
 * no se puede leer, el producto se busca por nombre, principio activo o comienzo del
 * código. Abrirla o cerrarla no toca lo que ya se capturó en la atención.
 */
export function BusquedaManual({ api, codigoNoEncontrado, alElegir, alLeerCodigo, alCerrar }: Props) {
  const [texto, setTexto] = useState('')
  const [resultado, setResultado] = useState<Resultado | null>(null)

  const termino = texto.trim()
  const buscable = termino.length >= LARGO_MINIMO

  useEffect(() => {
    if (!buscable) return
    const cancelacion = new AbortController()
    const espera = setTimeout(() => {
      api.buscarPorTexto(termino, cancelacion.signal).then(
        (productos) => setResultado({ termino, productos }),
        (error: unknown) => {
          if (cancelacion.signal.aborted) return
          setResultado({ termino, error: error instanceof Error ? error.message : 'La búsqueda falló.' })
        },
      )
    }, ESPERA_MS)
    return () => {
      clearTimeout(espera)
      cancelacion.abort()
    }
  }, [api, termino, buscable])

  const vigente = buscable && resultado?.termino === termino ? resultado : null

  return (
    <section className="tarjeta busqueda" aria-labelledby="titulo-busqueda">
      <div className="fila espaciada">
        <h2 id="titulo-busqueda">Búsqueda manual</h2>
        <button type="button" className="enlace" onClick={alCerrar}>
          Cerrar búsqueda
        </button>
      </div>
      {codigoNoEncontrado && (
        <p className="secundario">
          El código <span className="codigo">{codigoNoEncontrado}</span> no está en el catálogo de la comuna.
        </p>
      )}
      <form
        role="search"
        onSubmit={(evento) => {
          evento.preventDefault()
          if (leerCodigo(texto).tipo === 'gtin') {
            alLeerCodigo(texto)
            setTexto('')
          }
        }}
      >
        <label htmlFor="busqueda">Nombre, principio activo o comienzo del código</label>
        <input
          id="busqueda"
          type="search"
          value={texto}
          onChange={(evento) => setTexto(evento.target.value)}
          onKeyDown={(evento) => {
            if (evento.key === 'Escape') alCerrar()
          }}
          autoFocus
          autoComplete="off"
          spellCheck={false}
        />
      </form>

      <div aria-live="polite">
        {!buscable ? (
          <p className="secundario">Escriba al menos {LARGO_MINIMO} letras o dígitos.</p>
        ) : vigente === null ? (
          <p className="secundario">Buscando…</p>
        ) : 'error' in vigente ? (
          <p className="aviso aviso-error">{vigente.error}</p>
        ) : vigente.productos.length === 0 ? (
          <p className="secundario">No hay productos que coincidan con «{termino}».</p>
        ) : (
          <>
            <p className="secundario">
              {vigente.productos.length === 1 ? '1 producto encontrado' : `${vigente.productos.length} productos encontrados`}
            </p>
            <ul className="resultados">
              {vigente.productos.map((producto) => (
                <li key={producto.gtin}>
                  <button type="button" onClick={() => alElegir(producto)}>
                    <span className="nombre">{producto.nombre}</span>
                    <span className="secundario">{detalleDeProducto(producto)}</span>
                    <span className="codigo">{producto.gtin}</span>
                  </button>
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </section>
  )
}
