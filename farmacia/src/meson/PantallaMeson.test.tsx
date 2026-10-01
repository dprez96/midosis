import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { ErrorDeApi } from '../api/cliente'
import { catalogoFalso, CODIGO_FUERA_DEL_CATALOGO, losartan, metformina } from '../test/datos'
import { PantallaMeson } from './PantallaMeson'

/** El lector HID «escribe» el código y termina con Enter en el campo que tenga el foco. */
async function escanear(usuario: ReturnType<typeof userEvent.setup>, codigo: string) {
  await usuario.keyboard(`${codigo}{Enter}`)
}

const lector = () => screen.getByRole('textbox', { name: 'Código del producto' })
const lineas = () => within(screen.getByRole('region', { name: 'Productos de esta atención' }))

describe('PantallaMeson (HU-02)', () => {
  it('un producto escaneado queda identificado con su principio activo, forma y concentración', async () => {
    const usuario = userEvent.setup()
    render(<PantallaMeson puedeGuardarPlantillas={false} api={catalogoFalso([losartan])} />)

    expect(lector()).toHaveFocus()
    await escanear(usuario, losartan.gtin)

    expect(await lineas().findByText('Losartán 50 mg')).toBeInTheDocument()
    expect(lineas().getByText('Losartán potásico · Comprimido · 50 mg')).toBeInTheDocument()
    // Listo para el siguiente envase, sin tocar el mouse.
    expect(lector()).toHaveValue('')
    expect(lector()).toHaveFocus()
  })

  it('un código fuera del catálogo ofrece la búsqueda manual sin perder lo ya capturado', async () => {
    const usuario = userEvent.setup()
    render(<PantallaMeson puedeGuardarPlantillas={false} api={catalogoFalso([losartan, metformina])} />)

    await escanear(usuario, losartan.gtin)
    await lineas().findByText('Losartán 50 mg')

    await escanear(usuario, CODIGO_FUERA_DEL_CATALOGO)
    const busqueda = await screen.findByRole('region', { name: 'Búsqueda manual' })
    expect(within(busqueda).getByText(CODIGO_FUERA_DEL_CATALOGO)).toBeInTheDocument()
    expect(lineas().getByText('Losartán 50 mg')).toBeInTheDocument()

    const campo = within(busqueda).getByRole('searchbox')
    expect(campo).toHaveFocus()
    await usuario.type(campo, 'metformina')
    await usuario.click(await within(busqueda).findByRole('button', { name: /Metformina 850 mg/ }))

    expect(lineas().getAllByRole('listitem')).toHaveLength(2)
    expect(lineas().getByText('Losartán 50 mg')).toBeInTheDocument()
    expect(lineas().getByText('Metformina 850 mg')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Búsqueda manual' })).not.toBeInTheDocument()
    expect(lector()).toHaveFocus()
  })

  it('un código mal leído no llega al catálogo y pide volver a escanear', async () => {
    const usuario = userEvent.setup()
    const catalogo = catalogoFalso([losartan])
    render(<PantallaMeson puedeGuardarPlantillas={false} api={catalogo} />)

    await escanear(usuario, '7802250012345')

    expect(await screen.findByText(/El dígito verificador no corresponde/)).toBeInTheDocument()
    expect(catalogo.consultados).toEqual([])
  })

  it('escanear dos veces el mismo envase no lo duplica', async () => {
    const usuario = userEvent.setup()
    render(<PantallaMeson puedeGuardarPlantillas={false} api={catalogoFalso([losartan])} />)

    await escanear(usuario, losartan.gtin)
    await lineas().findByText('Losartán 50 mg')
    await escanear(usuario, losartan.gtin)

    expect(await screen.findByText('Losartán 50 mg ya está en esta atención.')).toBeInTheDocument()
    expect(lineas().getAllByRole('listitem')).toHaveLength(1)
  })

  it('un código escaneado con la búsqueda abierta se procesa como una lectura', async () => {
    const usuario = userEvent.setup()
    render(<PantallaMeson puedeGuardarPlantillas={false} api={catalogoFalso([losartan])} />)

    await usuario.click(screen.getByRole('button', { name: 'Buscar por nombre' }))
    expect(screen.getByRole('searchbox')).toHaveFocus()
    await escanear(usuario, losartan.gtin)

    expect(await lineas().findByText('Losartán 50 mg')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Búsqueda manual' })).not.toBeInTheDocument()
  })

  it('si core no responde, lo dice y conserva lo capturado', async () => {
    const usuario = userEvent.setup()
    const catalogo = catalogoFalso([losartan])
    render(<PantallaMeson puedeGuardarPlantillas={false} api={catalogo} />)
    await escanear(usuario, losartan.gtin)
    await lineas().findByText('Losartán 50 mg')

    catalogo.buscarProducto = async () => {
      throw new ErrorDeApi(0, 'No hay conexión con el servidor de MiDosis.')
    }
    await escanear(usuario, metformina.gtin)

    expect(await screen.findByText('No hay conexión con el servidor de MiDosis.')).toBeInTheDocument()
    expect(lineas().getByText('Losartán 50 mg')).toBeInTheDocument()
  })

  it('un producto se puede quitar de la atención', async () => {
    const usuario = userEvent.setup()
    render(<PantallaMeson puedeGuardarPlantillas={false} api={catalogoFalso([losartan])} />)
    await escanear(usuario, losartan.gtin)
    await lineas().findByText('Losartán 50 mg')

    await usuario.click(screen.getByRole('button', { name: 'Quitar Losartán 50 mg' }))

    expect(lineas().queryByText('Losartán 50 mg')).not.toBeInTheDocument()
    expect(screen.getByText('Se quitó Losartán 50 mg de la atención.')).toBeInTheDocument()
  })
})
