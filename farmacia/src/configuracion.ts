/** Datos públicos de la app web de Firebase. No son secretos, pero no se versionan. */
export interface ConfiguracionFirebase {
  apiKey: string
  authDomain: string
  projectId: string
  appId: string
}

/** Devuelve null si falta algún valor, para mostrar cómo completarlos en vez de fallar. */
export function leerConfiguracionFirebase(
  entorno: Record<string, string | undefined> = import.meta.env,
): ConfiguracionFirebase | null {
  const configuracion = {
    apiKey: entorno.VITE_FIREBASE_API_KEY?.trim() ?? '',
    authDomain: entorno.VITE_FIREBASE_AUTH_DOMAIN?.trim() ?? '',
    projectId: entorno.VITE_FIREBASE_PROJECT_ID?.trim() ?? '',
    appId: entorno.VITE_FIREBASE_APP_ID?.trim() ?? '',
  }
  return Object.values(configuracion).every((valor) => valor !== '') ? configuracion : null
}
