import { initializeApp } from '@firebase/app'
import {
  browserSessionPersistence,
  initializeAuth,
  onIdTokenChanged,
  signInWithEmailAndPassword,
  signOut,
} from '@firebase/auth'
import type { ConfiguracionFirebase } from '../configuracion'
import { perfilDesdeClaims, type ServicioDeSesion } from './sesion'

const MENSAJES: Record<string, string> = {
  'auth/invalid-credential': 'Correo o contraseña incorrectos.',
  'auth/invalid-email': 'El correo no tiene un formato válido.',
  'auth/missing-password': 'Escriba la contraseña.',
  'auth/user-disabled': 'Este usuario está deshabilitado. Consulte al administrador comunal.',
  'auth/too-many-requests': 'Demasiados intentos fallidos. Espere unos minutos antes de volver a intentar.',
  'auth/network-request-failed': 'No hay conexión con el servicio de autenticación.',
}

export function mensajeDeError(error: unknown): string {
  const codigo = typeof error === 'object' && error !== null && 'code' in error ? String(error.code) : ''
  return MENSAJES[codigo] ?? 'No se pudo iniciar sesión. Intente de nuevo.'
}

/** Sesión con Firebase Authentication, correo y contraseña del personal de la farmacia. */
export function crearSesionFirebase(configuracion: ConfiguracionFirebase): ServicioDeSesion {
  // El computador del mesón se comparte entre turnos: la sesión termina al cerrar la
  // pestaña y no queda guardada para el siguiente.
  const auth = initializeAuth(initializeApp(configuracion), { persistence: browserSessionPersistence })

  return {
    observar(alCambiar) {
      // También avisa cuando el token se renueva, así un cambio de perfil llega solo.
      return onIdTokenChanged(auth, (usuario) => {
        if (!usuario) {
          alCambiar(null)
          return
        }
        usuario.getIdTokenResult().then(
          (resultado) => alCambiar(perfilDesdeClaims(usuario.email ?? '', resultado.claims)),
          () => alCambiar(null),
        )
      })
    },

    async iniciar(correo, clave) {
      try {
        await signInWithEmailAndPassword(auth, correo, clave)
      } catch (error) {
        throw new Error(mensajeDeError(error), { cause: error })
      }
    },

    cerrar: () => signOut(auth),

    async token() {
      const usuario = auth.currentUser
      if (!usuario) throw new Error('No hay una sesión iniciada.')
      return usuario.getIdToken()
    },
  }
}
