import { useState, type FormEvent } from 'react'

interface Props {
  iniciar: (correo: string, clave: string) => Promise<void>
}

export function PantallaIngreso({ iniciar }: Props) {
  const [correo, setCorreo] = useState('')
  const [clave, setClave] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await iniciar(correo.trim(), clave)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión.')
      setClave('')
    } finally {
      setEnviando(false)
    }
  }

  return (
    <main className="ingreso">
      <form className="tarjeta" onSubmit={enviar} aria-labelledby="titulo-ingreso">
        <h1 id="titulo-ingreso">MiDosis · Farmacia</h1>
        <p className="secundario">Ingrese con su cuenta del punto de dispensación.</p>

        <label htmlFor="correo">Correo</label>
        <input
          id="correo"
          type="email"
          autoComplete="username"
          value={correo}
          onChange={(e) => setCorreo(e.target.value)}
          required
          autoFocus
        />

        <label htmlFor="clave">Contraseña</label>
        <input
          id="clave"
          type="password"
          autoComplete="current-password"
          value={clave}
          onChange={(e) => setClave(e.target.value)}
          required
        />

        {error && (
          <p className="aviso aviso-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="primario" disabled={enviando}>
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </main>
  )
}
