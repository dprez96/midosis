import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ErrorDeApi } from '../api/cliente'
import { cadaDoceHoras, catalogoFalso, losartan } from '../test/datos'
import { PantallaMeson } from './PantallaMeson'

type Usuario = ReturnType<typeof userEvent.setup>

const lector = () => screen.getByRole('textbox', { name: 'Código del producto' })
const lineas = () => within(screen.getByRole('region', { name: 'Productos de esta atención' }))

async function escanear(usuario: Usuario, codigo: string) {
  await usuario.keyboard(`${codigo}{Enter}`)
}

async function escribirInhalador(usuario: Usuario) {
  await usuario.type(screen.getByLabelText('Cantidad por toma'), '2')
  await usuario.selectOptions(screen.getByLabelText('Unidad'), 'inhalacion')
  await usuario.type(screen.getByLabelText('Cada'), '6')
}

describe('Posología en el mesón (HU-03)', () => {
  it('con plantillas de la comuna, elegir una deja la posología registrada sin escribir', async () => {
    const usuario = userEvent.setup()
    render(
      <PantallaMeson api={catalogoFalso([losartan], { [losartan.gtin]: [cadaDoceHoras] })} puedeGuardarPlantillas={false} />,
    )

    await escanear(usuario, losartan.gtin)
    await usuario.click(await screen.findByRole('button', { name: '1 comprimido cada 12 horas por 30 días. Con alimentos' }))

    expect(lineas().getByText('1 comprimido cada 12 horas por 30 días. Con alimentos')).toBeInTheDocument()
    expect(lineas().getByText(/plantilla de la comuna/)).toBeInTheDocument()
    expect(screen.getByText('Posología registrada para Losartán 50 mg.')).toBeInTheDocument()
    // Listo para el siguiente envase.
    expect(lector()).toHaveFocus()
  })

  it('sin plantilla que corresponda, el químico farmacéutico ingresa la posología y la guarda como plantilla comunal', async () => {
    const usuario = userEvent.setup()
    const api = catalogoFalso([losartan])
    render(<PantallaMeson api={api} puedeGuardarPlantillas />)

    await escanear(usuario, losartan.gtin)
    expect(await screen.findByText('Este producto no tiene plantillas en la comuna.')).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Ingresar otra posología' }))
    await escribirInhalador(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Guardar como plantilla comunal' }))

    expect(api.creadas).toEqual([
      {
        gtin: losartan.gtin,
        posologia: { cantidad: 2, unidad: 'inhalacion', frecuencia: 'PT6H', duracionDias: null, indicaciones: null },
      },
    ])
    expect(await lineas().findByText('2 inhalaciones cada 6 horas')).toBeInTheDocument()
    expect(lineas().getByText(/plantilla de la comuna/)).toBeInTheDocument()
  })

  it('el auxiliar puede usar una posología propia en la atención, pero no guardarla como plantilla', async () => {
    const usuario = userEvent.setup()
    const api = catalogoFalso([losartan])
    render(<PantallaMeson api={api} puedeGuardarPlantillas={false} />)

    await escanear(usuario, losartan.gtin)
    await usuario.click(await screen.findByRole('button', { name: 'Ingresar otra posología' }))

    expect(screen.queryByRole('button', { name: 'Guardar como plantilla comunal' })).not.toBeInTheDocument()
    expect(screen.getByText(/Solo el químico farmacéutico puede guardarla/)).toBeInTheDocument()

    await escribirInhalador(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Usar en esta atención' }))

    expect(lineas().getByText('2 inhalaciones cada 6 horas')).toBeInTheDocument()
    expect(lineas().queryByText(/plantilla de la comuna/)).not.toBeInTheDocument()
    expect(api.creadas).toEqual([])
  })

  it('el formulario dice qué falta antes de enviar nada', async () => {
    const usuario = userEvent.setup()
    render(<PantallaMeson api={catalogoFalso([losartan])} puedeGuardarPlantillas />)

    await escanear(usuario, losartan.gtin)
    await usuario.click(await screen.findByRole('button', { name: 'Ingresar otra posología' }))
    await usuario.click(screen.getByRole('button', { name: 'Usar en esta atención' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Indique la cantidad por toma.')
  })

  it('si core rechaza la plantilla, lo dice y conserva lo escrito', async () => {
    const usuario = userEvent.setup()
    const api = catalogoFalso([losartan])
    api.crearPlantilla = async () => {
      throw new ErrorDeApi(409, 'ya existe una plantilla con esa misma posología para este producto')
    }
    render(<PantallaMeson api={api} puedeGuardarPlantillas />)

    await escanear(usuario, losartan.gtin)
    await usuario.click(await screen.findByRole('button', { name: 'Ingresar otra posología' }))
    await escribirInhalador(usuario)
    await usuario.click(screen.getByRole('button', { name: 'Guardar como plantilla comunal' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('ya existe una plantilla con esa misma posología')
    expect(screen.getByLabelText('Cantidad por toma')).toHaveValue('2')
  })

  it('cambiar la posología vuelve a mostrar las plantillas', async () => {
    const usuario = userEvent.setup()
    render(
      <PantallaMeson api={catalogoFalso([losartan], { [losartan.gtin]: [cadaDoceHoras] })} puedeGuardarPlantillas={false} />,
    )
    await escanear(usuario, losartan.gtin)
    await usuario.click(await screen.findByRole('button', { name: /1 comprimido cada 12 horas/ }))

    await usuario.click(screen.getByRole('button', { name: 'Cambiar la posología de Losartán 50 mg' }))

    expect(screen.getByRole('button', { name: /1 comprimido cada 12 horas/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('si el foco quedó en la página, lo que escanea el lector igual llega al campo del código', async () => {
    const usuario = userEvent.setup()
    render(<PantallaMeson api={catalogoFalso([losartan])} puedeGuardarPlantillas={false} />)

    lector().blur()
    expect(document.body).toHaveFocus()
    await escanear(usuario, losartan.gtin)

    expect(await lineas().findByText('Losartán 50 mg')).toBeInTheDocument()
  })
})
