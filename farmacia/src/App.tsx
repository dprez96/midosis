import { useState } from 'react'
import type { ApiDelMeson } from './api/cliente'
import { CodigosEmitidos } from './emisiones/CodigosEmitidos'
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
      <Encabezado perfil={perfil} cerrar={servicio.cerrar} />
      <main className="contenido">
        {perfil.comuna && perfil.rol ? (
          <Secciones api={api} puedeGuardarPlantillas={perfil.rol === 'qf'} />
        ) : (
          <PerfilIncompleto />
        )}
      </main>
    </>
  )
}

type Seccion = 'meson' | 'emitidos'

/**
 * El mesón y los códigos emitidos. El mesón sigue montado mientras se revisan los códigos:
 * volver a él no pierde la atención en curso.
 */
function Secciones({ api, puedeGuardarPlantillas }: { api: ApiDelMeson; puedeGuardarPlantillas: boolean }) {
  const [seccion, setSeccion] = useState<Seccion>('meson')
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
      </nav>
      <div hidden={seccion !== 'meson'}>
        <PantallaMeson api={api} puedeGuardarPlantillas={puedeGuardarPlantillas} />
      </div>
      {seccion === 'emitidos' && <CodigosEmitidos api={api} />}
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
