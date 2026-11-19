import { useState } from 'react'
import type { ApiDelMeson } from './api/cliente'
import { CodigosEmitidos } from './emisiones/CodigosEmitidos'
import { Correccion } from './emisiones/Correccion'
import type { CorreccionEnCurso } from './dominio/correccion'
import { Demanda } from './demanda/Demanda'
import { Exportacion } from './exportacion/Exportacion'
import { Ayuda } from './ayuda/Ayuda'
import { PantallaMeson } from './meson/PantallaMeson'
import { PantallaIngreso } from './sesion/PantallaIngreso'
import { NOMBRE_DEL_ROL, type Perfil, type ServicioDeSesion } from './sesion/sesion'
import { useSesion } from './sesion/useSesion'

interface Props {
  servicio: ServicioDeSesion
  api: ApiDelMeson
}

export function App({ servicio, api }: Props) {
  const estado = useSesion(servicio)

  if (estado.tipo === 'cargando') {
    return (
      <p className="cargando" role="status">
        Cargando…
      </p>
    )
  }
  if (estado.tipo === 'sin-sesion') {
    return <PantallaIngreso iniciar={servicio.iniciar} />
  }

  const { perfil } = estado
  return (
    <>
      <a className="saltar" href="#contenido">
        Saltar al contenido
      </a>
      <Encabezado perfil={perfil} cerrar={servicio.cerrar} />
      <main className="contenido" id="contenido" tabIndex={-1}>
        {perfil.comuna && perfil.rol ? (
          <Secciones
            api={api}
            esQf={perfil.rol === 'qf'}
            esAdministrador={perfil.rol === 'administrador'}
            validar={servicio.tokenDeValidacion}
          />
        ) : (
          <PerfilIncompleto />
        )}
      </main>
    </>
  )
}

type Seccion = 'meson' | 'emitidos' | 'correccion' | 'demanda' | 'exportacion' | 'ayuda'

/**
 * El mesón, los códigos emitidos y, para el químico farmacéutico, la corrección de un
 * tratamiento (HU-33) y la demanda proyectada (HU-27). La corrección se completa en el mesón,
 * con los productos corregidos. El mesón sigue montado mientras se revisa lo demás: volver a él no pierde la
 * atención en curso. Core vuelve a verificar el rol de cada consulta.
 */
function Secciones({
  api,
  esQf,
  esAdministrador,
  validar,
}: {
  api: ApiDelMeson
  esQf: boolean
  esAdministrador: boolean
  validar: (correo: string, clave: string) => Promise<string>
}) {
  const [seccion, setSeccion] = useState<Seccion>('meson')
  const [correccion, setCorreccion] = useState<CorreccionEnCurso | null>(null)
  return (
    <>
      <nav className="secciones" aria-label="Secciones">
        <button type="button" aria-current={seccion === 'meson' ? 'page' : undefined} onClick={() => setSeccion('meson')}>
          Atención en el mesón
        </button>
        <button
          type="button"
          aria-current={seccion === 'emitidos' ? 'page' : undefined}
          onClick={() => setSeccion('emitidos')}
        >
          Códigos emitidos
        </button>
        {esQf && (
          <button
            type="button"
            aria-current={seccion === 'correccion' ? 'page' : undefined}
            onClick={() => setSeccion('correccion')}
          >
            Corregir un tratamiento
          </button>
        )}
        {esQf && (
          <button
            type="button"
            aria-current={seccion === 'demanda' ? 'page' : undefined}
            onClick={() => setSeccion('demanda')}
          >
            Demanda proyectada
          </button>
        )}
        {esAdministrador && (
          <button
            type="button"
            aria-current={seccion === 'exportacion' ? 'page' : undefined}
            onClick={() => setSeccion('exportacion')}
          >
            Exportar datos
          </button>
        )}
        <button type="button" aria-current={seccion === 'ayuda' ? 'page' : undefined} onClick={() => setSeccion('ayuda')}>
          Ayuda
        </button>
      </nav>
      <div hidden={seccion !== 'meson'}>
        <PantallaMeson
          api={api}
          puedeGuardarPlantillas={esQf}
          correccion={correccion}
          alTerminarCorreccion={() => setCorreccion(null)}
          validar={validar}
        />
      </div>
      {seccion === 'emitidos' && <CodigosEmitidos api={api} />}
      {seccion === 'correccion' && esQf && (
        <Correccion
          api={api}
          alIniciar={(c) => {
            setCorreccion(c)
            setSeccion('meson')
          }}
        />
      )}
      {seccion === 'demanda' && esQf && <Demanda api={api} />}
      {seccion === 'exportacion' && esAdministrador && <Exportacion api={api} />}
      {seccion === 'ayuda' && <Ayuda />}
    </>
  )
}

function Encabezado({ perfil, cerrar }: { perfil: Perfil; cerrar: () => Promise<void> }) {
  return (
    <header className="encabezado">
      <p className="marca">MiDosis · Farmacia</p>
      <div className="fila">
        <p className="secundario">
          {perfil.correo}
          {perfil.rol && <> · {NOMBRE_DEL_ROL[perfil.rol]}</>}
          {perfil.comuna && <> · Comuna {perfil.comuna}</>}
        </p>
        <button type="button" onClick={() => void cerrar()}>
          Cerrar sesión
        </button>
      </div>
    </header>
  )
}

function PerfilIncompleto() {
  return (
    <section className="tarjeta" role="alert">
      <h1>Falta el perfil de este usuario</h1>
      <p>
        Su cuenta no tiene comuna o rol asignado, y sin ellos MiDosis no puede mostrar el catálogo de su farmacia. Pida
        al administrador comunal que se los asigne; el cambio vale desde el próximo inicio de sesión.
      </p>
    </section>
  )
}

export function FaltaConfiguracion() {
  return (
    <main className="contenido">
      <section className="tarjeta" role="alert">
        <h1>Falta la configuración de Firebase</h1>
        <p>
          Copie <code>farmacia/.env.example</code> como <code>farmacia/.env.local</code> y complete los valores de la app
          web «MiDosis Farmacia» desde la consola de Firebase. Luego reinicie <code>npm run dev</code>.
        </p>
      </section>
    </main>
  )
}
