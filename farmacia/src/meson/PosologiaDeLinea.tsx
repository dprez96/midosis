import { useEffect, useState } from 'react'
import type { ApiDePlantillas } from '../api/cliente'
import { describirPosologia, type Plantilla, type Posologia } from '../dominio/posologia'
import type { Linea } from './atencion'
import { FormularioPosologia } from './FormularioPosologia'

interface Props {
  linea: Linea
  api: ApiDePlantillas
  puedeGuardarPlantilla: boolean
  alRegistrar: (posologia: Posologia, plantillaId: string | null) => void
}

type Carga = { estado: 'cargando' } | { estado: 'lista'; plantillas: Plantilla[] } | { estado: 'error'; mensaje: string }

function posologiaDe(plantilla: Plantilla): Posologia {
  const { cantidad, unidad, frecuencia, duracionDias, indicaciones } = plantilla
  return { cantidad, unidad, frecuencia, duracionDias, indicaciones }
}

/**
 * Posología de un producto de la atención (HU-03): se elige entre las plantillas de la
 * comuna, sin escribir, o se ingresa en campos estructurados cuando ninguna corresponde.
 */
export function PosologiaDeLinea({ linea, api, puedeGuardarPlantilla, alRegistrar }: Props) {
  const [carga, setCarga] = useState<Carga>({ estado: 'cargando' })
  const [editando, setEditando] = useState(linea.posologia === null)
  const [conFormulario, setConFormulario] = useState(false)
  const { gtin, nombre } = linea.producto

  useEffect(() => {
    let vigente = true
    api.listarPlantillas(gtin).then(
      (plantillas) => {
        if (vigente) setCarga({ estado: 'lista', plantillas })
      },
      (error: unknown) => {
        if (vigente) {
          setCarga({ estado: 'error', mensaje: error instanceof Error ? error.message : 'No se pudieron cargar las plantillas.' })
        }
      },
    )
    return () => {
      vigente = false
    }
  }, [api, gtin])

  function registrar(posologia: Posologia, plantillaId: string | null) {
    alRegistrar(posologia, plantillaId)
    setConFormulario(false)
    setEditando(false)
  }

  async function guardarPlantilla(posologia: Posologia) {
    const creada = await api.crearPlantilla(gtin, posologia)
    setCarga((actual) => ({
      estado: 'lista',
      plantillas: actual.estado === 'lista' ? [...actual.plantillas, creada] : [creada],
    }))
    registrar(posologiaDe(creada), creada.id)
  }

  if (linea.posologia !== null && !editando) {
    return (
      <div className="posologia fila espaciada">
        <p>
          <strong>Posología:</strong> {describirPosologia(linea.posologia)}
          {linea.plantillaId && <span className="secundario"> · plantilla de la comuna</span>}
        </p>
        <button
          type="button"
          className="enlace"
          aria-label={`Cambiar la posología de ${nombre}`}
          onClick={() => setEditando(true)}
        >
          Cambiar
        </button>
      </div>
    )
  }

  if (conFormulario) {
    return (
      <div className="posologia">
        <FormularioPosologia
          idBase={`linea-${linea.id}`}
          puedeGuardarPlantilla={puedeGuardarPlantilla}
          alUsar={(posologia) => registrar(posologia, null)}
          alGuardarPlantilla={guardarPlantilla}
          alCancelar={() => setConFormulario(false)}
        />
      </div>
    )
  }

  return (
    <div className="posologia" role="group" aria-label={`Posología de ${nombre}`}>
      {carga.estado === 'cargando' && <p className="secundario">Cargando plantillas…</p>}
      {carga.estado === 'error' && <p className="aviso aviso-error">{carga.mensaje}</p>}
      {carga.estado === 'lista' &&
        (carga.plantillas.length === 0 ? (
          <p className="secundario">Este producto no tiene plantillas en la comuna.</p>
        ) : (
          <>
            <p className="secundario">Plantillas de la comuna</p>
            <ul className="plantillas">
              {carga.plantillas.map((plantilla) => (
                <li key={plantilla.id}>
                  <button
                    type="button"
                    aria-pressed={linea.plantillaId === plantilla.id}
                    onClick={() => registrar(posologiaDe(plantilla), plantilla.id)}
                  >
                    {describirPosologia(plantilla)}
                  </button>
                </li>
              ))}
            </ul>
          </>
        ))}
      <div className="fila">
        <button type="button" className="enlace" onClick={() => setConFormulario(true)}>
          Ingresar otra posología
        </button>
        {linea.posologia !== null && (
          <button type="button" className="enlace" onClick={() => setEditando(false)}>
            Mantener la actual
          </button>
        )}
      </div>
    </div>
  )
}
