export type Rol = 'auxiliar' | 'qf' | 'administrador'

export const NOMBRE_DEL_ROL: Record<Rol, string> = {
  auxiliar: 'Auxiliar de farmacia',
  qf: 'Químico farmacéutico',
  administrador: 'Administrador comunal',
}

/**
 * Quién está en el mesón. La comuna y el rol vienen del token (los asigna
 * infra/firebase/asignar-perfil.mjs); si falta alguno, el perfil está incompleto.
 *
 * La aplicación los usa solo para mostrar u ocultar opciones. Quien decide es core, que
 * vuelve a leerlos del token firmado en cada llamada.
 */
export interface Perfil {
  correo: string
  comuna: string | null
  rol: Rol | null
}

export interface ServicioDeSesion {
  /** Avisa cada vez que cambia la sesión, también al empezar. Devuelve cómo dejar de observar. */
  observar(alCambiar: (perfil: Perfil | null) => void): () => void
  /** Falla con un mensaje en español que se puede mostrar tal cual. */
  iniciar(correo: string, clave: string): Promise<void>
  cerrar(): Promise<void>
  /** Token vigente para llamar a core. */
  token(): Promise<string>
}

export function esRol(valor: unknown): valor is Rol {
  return valor === 'auxiliar' || valor === 'qf' || valor === 'administrador'
}

export function perfilDesdeClaims(correo: string, claims: Record<string, unknown>): Perfil {
  const comuna = typeof claims.comuna === 'string' && /^\d{5}$/.test(claims.comuna) ? claims.comuna : null
  return { correo, comuna, rol: esRol(claims.rol) ? claims.rol : null }
}
