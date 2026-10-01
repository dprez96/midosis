import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import { leerConfiguracionFirebase } from './configuracion'
import { perfilDesdeClaims } from './sesion/sesion'
import { catalogoFalso, sesionFalsa } from './test/datos'

describe('App', () => {
  it('sin sesión pide ingresar', () => {
    render(<App servicio={sesionFalsa(null)} api={catalogoFalso([])} />)

    expect(screen.getByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
  })

  it('con el perfil completo muestra el mesón, el rol y la comuna', () => {
    const perfil = { correo: 'aux@midosis.cl', comuna: '13123', rol: 'auxiliar' as const }
    render(<App servicio={sesionFalsa(perfil)} api={catalogoFalso([])} />)

    expect(screen.getByRole('textbox', { name: 'Código del producto' })).toBeInTheDocument()
    expect(screen.getByText(/Auxiliar de farmacia · Comuna 13123/)).toBeInTheDocument()
  })

  it('sin comuna o rol en el token no muestra el mesón', () => {
    const perfil = { correo: 'nuevo@midosis.cl', comuna: null, rol: null }
    render(<App servicio={sesionFalsa(perfil)} api={catalogoFalso([])} />)

    expect(screen.getByRole('alert')).toHaveTextContent('Falta el perfil de este usuario')
    expect(screen.queryByRole('textbox', { name: 'Código del producto' })).not.toBeInTheDocument()
  })
})

describe('perfilDesdeClaims', () => {
  it('toma la comuna y el rol del token', () => {
    expect(perfilDesdeClaims('qf@midosis.cl', { comuna: '13123', rol: 'qf' })).toEqual({
      correo: 'qf@midosis.cl',
      comuna: '13123',
      rol: 'qf',
    })
  })

  it('descarta una comuna mal formada o un rol desconocido', () => {
    expect(perfilDesdeClaims('x@midosis.cl', { comuna: 'Providencia', rol: 'superusuario' })).toEqual({
      correo: 'x@midosis.cl',
      comuna: null,
      rol: null,
    })
  })
})

describe('leerConfiguracionFirebase', () => {
  const completa = {
    VITE_FIREBASE_API_KEY: 'clave-publica-de-prueba',
    VITE_FIREBASE_AUTH_DOMAIN: 'midosis-desarrollo.firebaseapp.com',
    VITE_FIREBASE_PROJECT_ID: 'midosis-desarrollo',
    VITE_FIREBASE_APP_ID: '1:000000000000:web:prueba',
  }

  it('entrega la configuración cuando está completa', () => {
    expect(leerConfiguracionFirebase(completa)?.projectId).toBe('midosis-desarrollo')
  })

  it('devuelve null si falta un valor, para explicar cómo completarlo', () => {
    expect(leerConfiguracionFirebase({ ...completa, VITE_FIREBASE_API_KEY: ' ' })).toBeNull()
  })
})
