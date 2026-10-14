package cl.midosis.core.catalogo

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.ControlLegal
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Productos sujetos a control legal (HU-06): el catálogo los marca para que el comprobante
 * impreso no muestre su nombre.
 */
@AutoConfigureMockMvc
class ControlLegalTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos

    private val providencia = CodigoComuna.de("13123")
    private val clonazepam = gtin("780225001330")
    private val morfina = gtin("780225001340")
    private val losartan = gtin("780225001234")

    @BeforeEach
    fun datos() {
        limpiarDatos()
        contexto.en(providencia) {
            catalogo.guardar(producto(clonazepam, "Clonazepam 0,5 mg", ControlLegal.PSICOTROPICO))
            catalogo.guardar(producto(morfina, "Morfina 10 mg", ControlLegal.ESTUPEFACIENTE))
            catalogo.guardar(producto(losartan, "Losartán 50 mg", null))
        }
    }

    @Test
    fun `el catalogo conserva el regimen de cada producto`() {
        contexto.en(providencia) {
            assertEquals(ControlLegal.PSICOTROPICO, catalogo.buscar(clonazepam)?.controlLegal)
            assertEquals(ControlLegal.ESTUPEFACIENTE, catalogo.buscar(morfina)?.controlLegal)
            assertNull(catalogo.buscar(losartan)?.controlLegal)
        }
    }

    @Test
    fun `la consulta del mesón informa el control legal`() {
        consultar(clonazepam).andExpect {
            status { isOk() }
            jsonPath("$.controlLegal") { value("DS405") }
        }
        consultar(losartan).andExpect {
            status { isOk() }
            jsonPath("$.controlLegal") { doesNotExist() }
        }
    }

    @Test
    fun `quitar el control legal tambien se guarda`() {
        contexto.en(providencia) {
            catalogo.guardar(producto(clonazepam, "Clonazepam 0,5 mg", null))
            assertNull(catalogo.buscar(clonazepam)?.controlLegal)
        }
    }

    @Test
    fun `la base rechaza un regimen que no existe`() {
        // La restricción de la tabla es la última barrera si alguien escribe sin pasar por el dominio.
        val error = runCatching {
            administrador.update(
                "INSERT INTO producto (comuna, gtin, nombre, principio_activo, forma, concentracion, control_legal) " +
                    "VALUES ('13123', '7802250099918', 'X', 'X', 'X', 'X', 'DS999')",
            )
        }.exceptionOrNull()
        assertEquals(true, error?.message?.contains("producto_control_legal_valido"), "se esperaba el rechazo de la restricción: $error")
    }

    private fun consultar(gtin: Gtin) =
        mvc.get("/api/catalogo/productos/${gtin.valor}") {
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }

    private fun producto(gtin: Gtin, nombre: String, control: ControlLegal?) = Producto(
        comuna = providencia,
        gtin = gtin,
        nombre = nombre,
        principioActivo = nombre.substringBefore(" "),
        forma = "Comprimido",
        concentracion = nombre.substringAfter(" "),
        controlLegal = control,
    )

    /** GTIN-13 sintético con su dígito verificador calculado. */
    private fun gtin(cuerpo: String): Gtin {
        val suma = cuerpo.reversed().withIndex().sumOf { (i, c) -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }
        return Gtin.de(cuerpo + (10 - suma % 10) % 10)
    }
}
