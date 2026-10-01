import { useEffect, useState } from 'react'
import type { Perfil, ServicioDeSesion } from './sesion'

export type EstadoDeSesion =
  | { tipo: 'cargando' }
  | { tipo: 'sin-sesion' }
  | { tipo: 'con-sesion'; perfil: Perfil }

export function useSesion(servicio: ServicioDeSesion): EstadoDeSesion {
  const [estado, setEstado] = useState<EstadoDeSesion>({ tipo: 'cargando' })

  useEffect(
    () => servicio.observar((perfil) => setEstado(perfil ? { tipo: 'con-sesion', perfil } : { tipo: 'sin-sesion' })),
    [servicio],
  )

  return estado
}
