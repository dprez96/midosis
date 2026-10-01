import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { crearApi } from './api/cliente'
import { App, FaltaConfiguracion } from './App'
import { leerConfiguracionFirebase } from './configuracion'
import { crearSesionFirebase } from './sesion/firebase'
import './estilos.css'

const raiz = createRoot(document.getElementById('raiz') as HTMLElement)
const configuracion = leerConfiguracionFirebase()

if (configuracion === null) {
  raiz.render(
    <StrictMode>
      <FaltaConfiguracion />
    </StrictMode>,
  )
} else {
  const servicio = crearSesionFirebase(configuracion)
  const api = crearApi(servicio.token)
  raiz.render(
    <StrictMode>
      <App servicio={servicio} api={api} />
    </StrictMode>,
  )
}
