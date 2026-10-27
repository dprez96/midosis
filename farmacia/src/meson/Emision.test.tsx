import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ErrorDeApi, type ApiDeEmision } from '../api/cliente'
import { CONSENTIMIENTO, peticionDeEmision, viaSugerida, type CodigoEmitido } from '../dominio/emision'
import type { Plantilla } from '../dominio/posologia'
import { catalogoFalso, clonazepam, losartan } from '../test/datos'
import { PantallaMeson } from './PantallaMeson'
import { intentosDelNavegador } from '../dominio/intentos'

type Usuario = ReturnType<typeof userEvent.setup>

const unoDiario: Plantilla = {
  id: 'uno-diario',
  cantidad: 1,
  unidad: 'comprimido',
  frecuencia: 'PT24H',
  duracionDias: 180,
  indicaciones: 'Con alimentos',
}

const EMITIDO: CodigoEmitido = {
  id: '01M0877000WQNC8FHDYAA6Y83C',
  codigo: 'MD1:6BFNL0OA3+QKQ2Q8E',
  codigoCorto: 'B1AW2-3MM77',
  pin: '4936',
  expiraEn: '2026-10-18T15:00:00Z',
}

function preparar(emitir = vi.fn<ApiDeEmision['emitir']>(async () => EMITIDO), productos = [losartan]) {
  const api = catalogoFalso(productos, Object.fromEntries(productos.map((p) => [p.gtin, [unoDiario]])))
  api.emitir = emitir
  render(<PantallaMeson api={api} puedeGuardarPlantillas={false} hoy={() => '2026-10-15'} />)
  return emitir
}

async function registrarLinea(usuario: Usuario, gtin: string, nombre: string) {
  await usuario.keyboard(`${gtin}{Enter}`)
  const linea = within((await screen.findByText(nombre)).closest('li')!)
  await usuario.click(await linea.findByRole('button', { name: /1 comprimido cada 24 horas/ }))
  await usuario.type(linea.getByLabelText('Cantidad entregada (comprimidos)'), '60')
  await usuario.click(linea.getByRole('button', { name: 'Registrar entrega' }))
}

describe('Emisión del código (HU-01) con consentimiento (HU-05)', () => {
  it('no se puede emitir hasta registrar la posología y la entrega', async () => {
    const usuario = userEvent.setup()
    preparar()
    await usuario.keyboard(`${losartan.gtin}{Enter}`)
    expect(await screen.findByRole('button', { name: 'Emitir código' })).toBeDisabled()
  })

  it('con el consentimiento, emite y muestra el comprobante con el QR, el código corto y el PIN', async () => {
    const usuario = userEvent.setup()
    const emitir = preparar()
    await registrarLinea(usuario, losartan.gtin, 'Losartán 50 mg')
    await usuario.click(screen.getByRole('button', { name: 'Emitir código' }))

    for (const p of CONSENTIMIENTO.parrafos) expect(screen.getByText(p)).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'El paciente acepta' }))

    const [lineas, consentimiento] = emitir.mock.calls[0]!
    expect(consentimiento.version).toBe(CONSENTIMIENTO.version)
    expect(lineas[0]).toMatchObject({ via: 'VO', tramo: { duracionIndicadaDias: 180, unidadesEntregadas: 60 } })

    const comprobante = await screen.findByRole('region', { name: 'Comprobante de tratamiento · MiDosis' })
    expect(within(comprobante).getByRole('img', { name: 'Código QR del tratamiento' })).toBeInTheDocument()
    expect(within(comprobante).getByText('B1AW2-3MM77')).toBeInTheDocument()
    expect(within(comprobante).getByText('4936')).toBeInTheDocument()
    expect(within(comprobante).getByText('Losartán 50 mg')).toBeInTheDocument()
    expect(within(comprobante).getByText(/alcanza hasta el 14-12-2026/)).toBeInTheDocument()
    expect(within(comprobante).getByText('Para: ______________________________')).toBeInTheDocument()
  })

  it('un producto con control legal sale en el comprobante sin su nombre', async () => {
    const usuario = userEvent.setup()
    preparar(undefined, [clonazepam])
    await registrarLinea(usuario, clonazepam.gtin, 'Clonazepam 0,5 mg')
    await usuario.click(screen.getByRole('button', { name: 'Emitir código' }))
    await usuario.click(screen.getByRole('button', { name: 'El paciente acepta' }))

    const comprobante = await screen.findByRole('region', { name: 'Comprobante de tratamiento · MiDosis' })
    expect(within(comprobante).queryByText(/Clonazepam/)).not.toBeInTheDocument()
    expect(within(comprobante).getByText('Medicamento 1 (ver detalle en la aplicación MiDosis)')).toBeInTheDocument()
  })

  it('si el paciente no acepta, no se emite y la atención se cierra igual', async () => {
    const usuario = userEvent.setup()
    const emitir = preparar()
    await registrarLinea(usuario, losartan.gtin, 'Losartán 50 mg')
    await usuario.click(screen.getByRole('button', { name: 'Emitir código' }))
    await usuario.click(screen.getByRole('button', { name: 'El paciente no acepta' }))

    expect(emitir).not.toHaveBeenCalled()
    expect(screen.getByRole('status')).toHaveTextContent('No se emitirá código. Entregue los medicamentos con normalidad.')
    await usuario.click(screen.getByRole('button', { name: 'Cerrar la atención' }))
    expect(screen.getByText('Aún no hay productos. Escanee el primer envase.')).toBeInTheDocument()
  })

  it('si MiDosis no responde, lo dice y deja cerrar la atención sin código', async () => {
    const usuario = userEvent.setup()
    preparar(vi.fn<ApiDeEmision['emitir']>(async () => {
      throw new ErrorDeApi(503, 'La emisión de códigos no está disponible en este momento.')
    }))
    await registrarLinea(usuario, losartan.gtin, 'Losartán 50 mg')
    await usuario.click(screen.getByRole('button', { name: 'Emitir código' }))
    await usuario.click(screen.getByRole('button', { name: 'El paciente acepta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('La emisión de códigos no está disponible en este momento.')
    expect(screen.getByRole('button', { name: 'Intentar de nuevo' })).toBeInTheDocument()
    await usuario.click(screen.getByRole('button', { name: 'Cerrar la atención sin código' }))
    expect(screen.getByText('Aún no hay productos. Escanee el primer envase.')).toBeInTheDocument()
  })

  it('si MiDosis no responde, el intento queda registrado con sus productos y el motivo', async () => {
    localStorage.clear()
    const usuario = userEvent.setup()
    preparar(vi.fn<ApiDeEmision['emitir']>(async () => {
      throw new ErrorDeApi(0, 'No hay conexión con el servidor de MiDosis.')
    }))
    await registrarLinea(usuario, losartan.gtin, 'Losartán 50 mg')
    await usuario.click(screen.getByRole('button', { name: 'Emitir código' }))
    await usuario.click(screen.getByRole('button', { name: 'El paciente acepta' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('El intento quedó anotado en «Códigos emitidos».')
    expect(intentosDelNavegador.listar()).toMatchObject([
      { productos: ['Losartán 50 mg'], motivo: 'No hay conexión con el servidor de MiDosis.' },
    ])
    localStorage.clear()
  })
})

describe('datos de la emisión', () => {
  it('sugiere la vía según la forma farmacéutica', () => {
    expect(viaSugerida('Comprimido recubierto')).toBe('VO')
    expect(viaSugerida('Aerosol para inhalación')).toBe('INH')
    expect(viaSugerida('Solución oftálmica')).toBe('OFT')
    expect(viaSugerida('Parche transdérmico')).toBe('TD')
    expect(viaSugerida('Crema')).toBe('TOP')
  })

  it('arma la petición con lo registrado en el mesón', () => {
    const peticion = peticionDeEmision(
      [{ producto: losartan, posologia: unoDiario, tramo: { duracionIndicadaDias: 180, unidadesEntregadas: 60 }, via: 'VO' }],
      { version: CONSENTIMIENTO.version, aceptadoEn: '2026-10-15T14:00:00.000Z' },
    )
    expect(peticion.lineas[0]).toEqual({
      gtin: losartan.gtin,
      cantidad: 1,
      unidad: 'comprimido',
      frecuencia: 'PT24H',
      duracionIndicadaDias: 180,
      unidadesEntregadas: 60,
      via: 'VO',
      indicaciones: 'Con alimentos',
    })
  })
})
