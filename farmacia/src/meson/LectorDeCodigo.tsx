import { useState, type Ref } from 'react'

interface Props {
  alLeer: (texto: string) => void
  ref?: Ref<HTMLInputElement>
}

/**
 * Campo donde «escribe» el lector de código de barras conectado como teclado (HID).
 *
 * Nunca se deshabilita, ni siquiera mientras se consulta el catálogo: un campo
 * deshabilitado perdería las teclas del siguiente envase que se escanee.
 */
export function LectorDeCodigo({ alLeer, ref }: Props) {
  const [texto, setTexto] = useState('')

  return (
    <form
      className="lector"
      onSubmit={(evento) => {
        evento.preventDefault()
        const leido = texto
        setTexto('')
        alLeer(leido)
      }}
    >
      <label htmlFor="codigo">Código del producto</label>
      <div className="fila">
        <input
          id="codigo"
          ref={ref}
          value={texto}
          onChange={(evento) => setTexto(evento.target.value)}
          autoFocus
          autoComplete="off"
          spellCheck={false}
          aria-describedby="codigo-ayuda"
        />
        <button type="submit">Identificar</button>
      </div>
      <p id="codigo-ayuda" className="secundario">
        Escanee el envase. Si el lector no puede leerlo, escriba el código y presione Enter.
      </p>
    </form>
  )
}
