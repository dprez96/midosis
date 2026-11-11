import { useEffect, useState } from 'react'
import type { ApiDeDemanda, DemandaProyectada } from '../api/cliente'

interface Props {
  api: ApiDeDemanda
}

type Carga = { tipo: 'cargando' } | { tipo: 'lista'; demanda: DemandaProyectada } | { tipo: 'fallo'; mensaje: string }

/**
 * El lunes de una semana, «02-11», a partir de «2026-11-02». Se arma a mano y no con Intl,
 * que da formatos distintos según el navegador.
 */
function semana(iso: string): string {
  const [, mes, dia] = iso.split('-')
  return `${dia}-${mes}`
}

/** «85 %», con el espacio de la norma en español. */
function porcentaje(fraccion: number): string {
  return `${Math.round(fraccion * 100)} %`
}

/**
 * La demanda proyectada de retiros (HU-27, RF-B-08), para programar la compra a CENABAST: por
 * producto y por semana, las próximas 12 semanas, con la tasa de retorno aplicada y el supuesto
 * declarado. Abajo, lo proyectado para las últimas 4 semanas frente a lo retirado.
 */
export function Demanda({ api }: Props) {
  const [carga, setCarga] = useState<Carga>({ tipo: 'cargando' })

  useEffect(() => {
    let vigente = true
    api.demanda().then(
      (demanda) => {
        if (vigente) setCarga({ tipo: 'lista', demanda })
      },
      (error: unknown) => {
        if (vigente) {
          setCarga({ tipo: 'fallo', mensaje: error instanceof Error ? error.message : 'No se pudo calcular la demanda.' })
        }
      },
    )
    return () => {
      vigente = false
    }
  }, [api])

  return (
    <section className="tarjeta" aria-labelledby="titulo-demanda">
      <h1 id="titulo-demanda">Demanda proyectada</h1>
      <p className="secundario">
        Unidades que se estima retirarán los pacientes en las próximas 12 semanas, según los tratamientos en curso.
      </p>
      {carga.tipo === 'cargando' && <p className="secundario">Calculando la demanda…</p>}
      {carga.tipo === 'fallo' && (
        <p className="aviso aviso-error" role="alert">
          {carga.mensaje}
        </p>
      )}
      {carga.tipo === 'lista' && <Tablas demanda={carga.demanda} />}
    </section>
  )
}

function Tablas({ demanda }: { demanda: DemandaProyectada }) {
  if (demanda.productos.length === 0) {
    return <p className="secundario">No hay retiros proyectados: todavía no hay tratamientos en curso que deban renovarse.</p>
  }
  return (
    <>
      <div className="tabla-con-desplazamiento">
        <table className="tabla">
          <caption>Unidades por semana (desde el lunes indicado)</caption>
          <thead>
            <tr>
              <th scope="col">Producto</th>
              {demanda.semanas.map((s) => (
                <th scope="col" key={s}>
                  {semana(s)}
                </th>
              ))}
              <th scope="col">Total</th>
              <th scope="col">Tasa de retorno</th>
            </tr>
          </thead>
          <tbody>
            {demanda.productos.map((p) => (
              <tr key={p.gtin}>
                <th scope="row">{p.nombre}</th>
                {p.unidades.map((u, i) => (
                  <td key={demanda.semanas[i]}>{u || ''}</td>
                ))}
                <td>{p.unidades.reduce((a, b) => a + b, 0)}</td>
                <td>{porcentaje(p.tasaDeRetorno)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="secundario">
        <strong>Supuesto aplicado:</strong> {demanda.supuesto}
      </p>
      {demanda.desviacion.length > 0 && (
        <div className="tabla-con-desplazamiento">
          <table className="tabla">
            <caption>Últimas 4 semanas: proyectado frente a retirado</caption>
            <thead>
              <tr>
                <th scope="col">Semana</th>
                <th scope="col">Producto</th>
                <th scope="col">Proyectado</th>
                <th scope="col">Retirado</th>
                <th scope="col">Desviación</th>
              </tr>
            </thead>
            <tbody>
              {demanda.desviacion.map((d) => (
                <tr key={`${d.semana}-${d.gtin}`}>
                  <td>{semana(d.semana)}</td>
                  <th scope="row">{d.nombre}</th>
                  <td>{d.proyectado}</td>
                  <td>{d.real}</td>
                  <td>{d.real - d.proyectado > 0 ? `+${d.real - d.proyectado}` : d.real - d.proyectado}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
