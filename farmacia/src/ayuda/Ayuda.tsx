/**
 * Ayuda y glosario del mesón (WCAG 2.1 AAA: 3.1.3 palabras poco comunes, 3.1.4 abreviaturas y
 * 3.3.5 ayuda). Explica en lenguaje simple los términos y siglas que aparecen en las pantallas.
 */
const TERMINOS: { termino: string; definicion: string }[] = [
  { termino: 'Posología', definicion: 'Cuánto tomar, cada cuánto y por cuántos días. Por ejemplo: 1 comprimido cada 12 horas por 30 días.' },
  {
    termino: 'Plantilla de posología',
    definicion:
      'Una posología frecuente que guardó el químico farmacéutico para un producto. Usarla evita escribirla y no necesita su validación.',
  },
  { termino: 'Duración indicada', definicion: 'Los días de tratamiento que indicó el médico en la receta.' },
  { termino: 'Tramo entregado', definicion: 'Lo que la farmacia entrega en este retiro. Puede alcanzar para menos días que lo indicado.' },
  { termino: 'Cobertura', definicion: 'Los días que alcanza lo entregado en este retiro.' },
  {
    termino: 'Código QR',
    definicion: 'El cuadro impreso en el comprobante. El paciente lo escanea con la aplicación MiDosis para cargar su tratamiento.',
  },
  {
    termino: 'Código corto y PIN',
    definicion:
      'El respaldo del código QR: un código de 10 caracteres y una clave de 4 números (PIN, número de identificación personal) para cargarlo a mano.',
  },
  {
    termino: 'Referencia',
    definicion:
      'Los 8 caracteres que identifican un tratamiento, impresos en el comprobante y visibles en la aplicación. Sirve para buscarlo y corregirlo.',
  },
  {
    termino: 'Revocar',
    definicion: 'Anular un código emitido por error antes de que el paciente lo cargue. El código corto deja de funcionar.',
  },
  {
    termino: 'Corregir',
    definicion: 'Reemplazar un tratamiento que el paciente ya cargó, con un código nuevo ligado al anterior. Solo el químico farmacéutico.',
  },
  {
    termino: 'Validación del químico farmacéutico (QF)',
    definicion:
      'Revisión de la posología antes de emitir, cuando no viene de una plantilla, el producto tiene control legal o lo entregado supera lo indicado.',
  },
  {
    termino: 'Control legal (DS 404 y DS 405)',
    definicion:
      'Estupefacientes (Decreto Supremo 404) y psicotrópicos (Decreto Supremo 405). En el comprobante figuran sin su nombre.',
  },
  { termino: 'GTIN', definicion: 'El número del código de barras del envase (número mundial de artículo comercial).' },
  {
    termino: 'Tasa de retorno',
    definicion: 'De los pacientes que debían volver a retirar, cuántos volvieron. Corrige la demanda proyectada.',
  },
  { termino: 'CENABAST', definicion: 'Central de Abastecimiento del Sistema Nacional de Servicios de Salud, a quien se compran los medicamentos.' },
  {
    termino: 'Archivo ZIP y CSV',
    definicion: 'El ZIP es un archivo que agrupa otros. Los CSV son tablas de texto que abre cualquier planilla, como Excel.',
  },
]

export function Ayuda() {
  return (
    <section className="tarjeta" aria-labelledby="titulo-ayuda">
      <h1 id="titulo-ayuda">Ayuda</h1>
      <h2>Pasos de una atención</h2>
      <ol>
        <li>Escanee cada envase o búsquelo por nombre.</li>
        <li>Registre la posología (elija una plantilla si la hay) y lo que entrega en este retiro.</li>
        <li>Lea el consentimiento al paciente y emita el código.</li>
        <li>Imprima el comprobante y escriba a mano el nombre del paciente en «Para:».</li>
      </ol>
      <p>Si MiDosis no responde o el paciente no acepta, entregue los medicamentos igual: el código es opcional.</p>
      <h2>Glosario</h2>
      <dl className="glosario">
        {TERMINOS.map(({ termino, definicion }) => (
          <div key={termino}>
            <dt>{termino}</dt>
            <dd>{definicion}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
