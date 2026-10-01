package cl.midosis.core.catalogo

import cl.midosis.core.dominio.catalogo.CatalogoDeProductos
import cl.midosis.core.dominio.catalogo.Gtin
import cl.midosis.core.dominio.catalogo.Producto
import cl.midosis.core.dominio.comuna.CodigoComuna
import cl.midosis.core.infraestructura.persistencia.ContextoComuna
import cl.midosis.core.soporte.PruebaIntegracion
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Búsqueda manual asistida (HU-02): cuando el código del envase no está en el catálogo, el
 * auxiliar encuentra el producto por su nombre o principio activo, escriba como escriba.
 */
@AutoConfigureMockMvc
class BusquedaEnCatalogoTest : PruebaIntegracion() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var contexto: ContextoComuna
    @Autowired lateinit var catalogo: CatalogoDeProductos

    private val providencia = CodigoComuna.de("13123")

    @BeforeEach
    fun datos() {
        administrador.execute("TRUNCATE producto, evento_seguridad")
        contexto.en(providencia) {
            catalogo.guardar(producto("780225001234", "Losartán 50 mg", "Losartán potásico", "50 mg"))
            catalogo.guardar(producto("780225001240", "Metformina 850 mg", "Metformina clorhidrato", "850 mg"))
            catalogo.guardar(producto("780225001250", "Aspirina 100 mg", "Ácido acetilsalicílico", "100 mg"))
            catalogo.guardar(producto("780225001260", "Enalapril 10 mg", "Enalapril maleato", "10 mg"))
        }
    }

    @Test
    fun `encuentra por nombre sin tildes ni mayusculas`() {
        buscar("LOSARTAN").andExpect {
            status { isOk() }
            jsonPath("$[*].nombre") { value(contains("Losartán 50 mg")) }
        }
    }

    @Test
    fun `encuentra por el principio activo aunque el nombre sea otro`() {
        buscar("acido acetil").andExpect {
            status { isOk() }
            jsonPath("$[*].nombre") { value(contains("Aspirina 100 mg")) }
        }
    }

    @Test
    fun `encuentra por el comienzo del codigo cuando el envase esta danado`() {
        buscar("78022500124").andExpect {
            status { isOk() }
            jsonPath("$[*].nombre") { value(contains("Metformina 850 mg")) }
        }
    }

    @Test
    fun `devuelve los productos ordenados por nombre`() {
        buscar("mg").andExpect {
            status { isOk() }
            jsonPath("$[*].nombre") {
                value(contains("Aspirina 100 mg", "Enalapril 10 mg", "Losartán 50 mg", "Metformina 850 mg"))
            }
        }
    }

    @Test
    fun `los comodines no traen el catalogo completo`() {
        buscar("%%").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        buscar("__").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `sin coincidencias responde una lista vacia`() {
        buscar("insulina").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `un termino demasiado corto se rechaza con el motivo`() {
        buscar("l").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("escribe al menos 2 caracteres para buscar") }
        }
    }

    private fun buscar(texto: String) =
        mvc.get("/api/catalogo/productos") {
            param("texto", texto)
            header("Authorization", "Bearer ${token(providencia.valor)}")
        }

    private fun producto(cuerpo: String, nombre: String, principio: String, concentracion: String) = Producto(
        comuna = providencia,
        gtin = gtin(cuerpo),
        nombre = nombre,
        principioActivo = principio,
        forma = "Comprimido",
        concentracion = concentracion,
    )

    /** GTIN-13 sintético con su dígito verificador calculado. */
    private fun gtin(cuerpo: String): Gtin {
        val suma = cuerpo.reversed().withIndex().sumOf { (i, c) -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }
        return Gtin.de(cuerpo + (10 - suma % 10) % 10)
    }
}
