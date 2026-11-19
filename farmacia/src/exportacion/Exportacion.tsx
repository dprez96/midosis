import { useState } from 'react'
import type { ApiDeExportacion, Descarga } from '../api/cliente'

interface Props {
  api: ApiDeExportacion
  /** Entrega el archivo al navegador; las pruebas lo reemplazan. */
  guardar?: (descarga: Descarga) => void
}

/** Descarga el archivo con un enlace temporal, sin abrir otra pestaña. */
function guardarEnElNavegador({ nombre, contenido }: Descarga) {
  const url = URL.createObjectURL(contenido)
  const enlace = document.createElement('a')
  enlace.href = url
  enlace.download = nombre
  enlace.click()
  URL.revokeObjectURL(url)
}

type Estado = { tipo: 'listo' } | { tipo: 'exportando' } | { tipo: 'hecho'; nombre: string } | { tipo: 'fallo'; mensaje: string }

/**
 * La exportación de todos los datos de la comuna en formato abierto (HU-29, RF-B-12, RN-20):
 * un ZIP con archivos CSV y su diccionario de datos. Solo el administrador comunal.
 */
export function Exportacion({ api, guardar = guardarEnElNavegador }: Props) {
  const [estado, setEstado] = useState<Estado>({ tipo: 'listo' })

  async function exportar() {
    setEstado({ tipo: 'exportando' })
    try {
      const descarga = await api.exportar()
      guardar(descarga)
      setEstado({ tipo: 'hecho', nombre: descarga.nombre })
    } catch (error) {
      setEstado({ tipo: 'fallo', mensaje: error instanceof Error ? error.message : 'No se pudo exportar.' })
    }
  }

  return (
    <section className="tarjeta" aria-labelledby="titulo-exportacion">
      <h1 id="titulo-exportacion">Exportar los datos de la comuna</h1>
      <p>
        Descarga en un archivo ZIP todo lo que MiDosis guarda de su comuna: catálogo, plantillas, tratamientos emitidos,
        correcciones, auditoría y métricas mensuales. Son archivos CSV, que abre cualquier planilla, con un diccionario
        que explica cada columna.
      </p>
      <p className="secundario">
        No contiene datos de pacientes: MiDosis no los tiene. Cada exportación queda registrada en la auditoría.
      </p>
      <button type="button" className="primario" disabled={estado.tipo === 'exportando'} onClick={() => void exportar()}>
        {estado.tipo === 'exportando' ? 'Preparando el archivo…' : 'Descargar los datos'}
      </button>
      <div aria-live="polite">
        {estado.tipo === 'hecho' && <p className="aviso aviso-exito">Se descargó {estado.nombre}.</p>}
        {estado.tipo === 'fallo' && (
          <p className="aviso aviso-error" role="alert">
            {estado.mensaje}
          </p>
        )}
      </div>
    </section>
  )
}
