import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import { catalogoFalso, losartan, sesionFalsa } from './test/datos'
import type { Rol } from './sesion/sesion'

/**
 * Auditoría automática con axe de las pantallas de la web, con las reglas de WCAG 2.1 niveles
 * A, AA y AAA (RNF-19). El contraste de color no se puede medir en jsdom: se verifica con los
 * valores de la hoja de estilos (comentario al inicio de estilos.css) y en el navegador.
 */
async function auditar() {
  const resultado = await axe.run(document.body, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag2aaa', 'wcag21a', 'wcag21aa', 'best-practice'] },
    rules: { 'color-contrast': { enabled: false }, 'color-contrast-enhanced': { enabled: false } },
  })
  return resultado.violations.map((v) => `${v.id}: ${v.help} (${v.nodes.map((n) => n.target.join(' ')).join(', ')})`)
}

function entrar(rol: Rol) {
  render(<App servicio={sesionFalsa({ correo: 'persona@midosis.cl', comuna: '13123', rol })} api={catalogoFalso([losartan])} />)
}

describe('Accesibilidad WCAG 2.1 AAA (axe)', () => {
  it('la pantalla de ingreso', async () => {
    render(<App servicio={sesionFalsa(null)} api={catalogoFalso([])} />)
    expect(await auditar()).toEqual([])
  })

  it('el mesón con un producto escaneado', async () => {
    const usuario = userEvent.setup()
    entrar('auxiliar')
    await usuario.type(screen.getByRole('textbox', { name: 'Código del producto' }), `${losartan.gtin}{Enter}`)
    await screen.findByText('Losartán 50 mg')
    expect(await auditar()).toEqual([])
  })

  for (const [rol, seccion] of [
    ['auxiliar', 'Códigos emitidos'],
    ['qf', 'Corregir un tratamiento'],
    ['qf', 'Demanda proyectada'],
    ['administrador', 'Exportar datos'],
    ['auxiliar', 'Ayuda'],
  ] as const) {
    it(`la sección «${seccion}»`, async () => {
      const usuario = userEvent.setup()
      entrar(rol)
      await usuario.click(screen.getByRole('button', { name: seccion }))
      expect(await auditar()).toEqual([])
    })
  }

  it('el enlace para saltar al contenido lleva al principal', () => {
    entrar('auxiliar')
    const enlace = screen.getByRole('link', { name: 'Saltar al contenido' })
    expect(enlace).toHaveAttribute('href', '#contenido')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'contenido')
  })
})
