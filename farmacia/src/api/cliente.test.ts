import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { crearApi, ErrorDeApi } from './cliente'

const losartan = {
  gtin: '7802250012344',
  nombre: 'Losartán 50 mg',
  principioActivo: 'Losartán potásico',
  forma: 'Comprimido',
  concentracion: '50 mg',
}

function respuesta(estado: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status: estado,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('crearApi', () => {
  const fetchSimulado = vi.fn<typeof fetch>()
  const api = crearApi(async () => 'token-de-prueba')

  beforeEach(() => vi.stubGlobal('fetch', fetchSimulado))
  afterEach(() => {
    vi.unstubAllGlobals()
    fetchSimulado.mockReset()
  })

  it('envía el token de la sesión y nunca la comuna', async () => {
    fetchSimulado.mockResolvedValue(respuesta(200, losartan))

    await api.buscarProducto(losartan.gtin)

    const [url, opciones] = fetchSimulado.mock.calls[0]!
    expect(url).toBe('/api/catalogo/productos/7802250012344')
    expect(new Headers(opciones?.headers).get('Authorization')).toBe('Bearer token-de-prueba')
    expect(String(url)).not.toContain('comuna')
  })

  it('un producto que no está en el catálogo es null, no un error', async () => {
    fetchSimulado.mockResolvedValue(respuesta(404))

    await expect(api.buscarProducto(losartan.gtin)).resolves.toBeNull()
  })

  it('codifica el texto de la búsqueda', async () => {
    fetchSimulado.mockResolvedValue(respuesta(200, []))

    await api.buscarPorTexto('ácido & sal')

    expect(fetchSimulado.mock.calls[0]![0]).toBe('/api/catalogo/productos?texto=%C3%A1cido%20%26%20sal')
  })

  it('muestra el motivo que entrega core', async () => {
    fetchSimulado.mockResolvedValue(respuesta(400, { error: 'escribe al menos 2 caracteres para buscar' }))

    await expect(api.buscarPorTexto('l')).rejects.toThrow('escribe al menos 2 caracteres para buscar')
  })

  it('traduce los estados sin cuerpo a un mensaje comprensible', async () => {
    fetchSimulado.mockResolvedValue(respuesta(401))

    await expect(api.buscarProducto(losartan.gtin)).rejects.toThrow('La sesión expiró')
  })

  it('crea una plantilla enviando la posología como JSON', async () => {
    const posologia = { cantidad: 1, unidad: 'comprimido' as const, frecuencia: 'PT12H', duracionDias: 30, indicaciones: null }
    fetchSimulado.mockResolvedValue(respuesta(201, { ...posologia, id: 'nueva' }))

    await expect(api.crearPlantilla(losartan.gtin, posologia)).resolves.toMatchObject({ id: 'nueva' })

    const [url, opciones] = fetchSimulado.mock.calls[0]!
    expect(url).toBe('/api/catalogo/productos/7802250012344/plantillas')
    expect(opciones?.method).toBe('POST')
    expect(new Headers(opciones?.headers).get('Content-Type')).toBe('application/json')
    expect(JSON.parse(String(opciones?.body))).toEqual(posologia)
  })

  it('distingue la falta de conexión', async () => {
    fetchSimulado.mockRejectedValue(new TypeError('Failed to fetch'))

    const error = await api.buscarProducto(losartan.gtin).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ErrorDeApi)
    expect(error).toMatchObject({ estado: 0, message: expect.stringContaining('No hay conexión') })
  })
})
