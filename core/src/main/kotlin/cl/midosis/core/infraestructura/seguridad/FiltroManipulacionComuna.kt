package cl.midosis.core.infraestructura.seguridad

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Capa de resolución de inquilino (HU-28). Si la petición trae una comuna, en el
 * parámetro «comuna» o en la cabecera X-Comuna, y no es la del token, se rechaza y se
 * registra como evento de seguridad antes de llegar a ningún dato.
 *
 * La API no necesita que el cliente indique su comuna: la sabe por el token. Quien la
 * manda distinta está intentando otra cosa.
 */
class FiltroManipulacionComuna(
    private val registro: RegistroEventosSeguridad,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        peticion: HttpServletRequest,
        respuesta: HttpServletResponse,
        cadena: FilterChain,
    ) {
        val declaradas = listOfNotNull(
            peticion.getParameter(PARAMETRO),
            peticion.getHeader(CABECERA),
        )
        val autenticacion = SecurityContextHolder.getContext().authentication
        if (declaradas.isEmpty() || autenticacion == null) {
            cadena.doFilter(peticion, respuesta)
            return
        }

        val delToken = try {
            ResolutorDeComuna.desde(autenticacion)
        } catch (e: ComunaNoResuelta) {
            rechazar(respuesta)
            return
        }

        val ajenas = declaradas.filter { it.trim() != delToken.valor }
        if (ajenas.isEmpty()) {
            cadena.doFilter(peticion, respuesta)
            return
        }

        registro.registrar(
            delToken,
            TipoEventoSeguridad.COMUNA_MANIPULADA,
            "${peticion.method} ${peticion.requestURI} declaró comuna ${ajenas.joinToString(",")}",
        )
        rechazar(respuesta)
    }

    private fun rechazar(respuesta: HttpServletResponse) {
        respuesta.status = HttpStatus.FORBIDDEN.value()
        respuesta.contentType = MediaType.APPLICATION_JSON_VALUE
        respuesta.characterEncoding = "UTF-8"
        respuesta.writer.write("""{"error":"acceso denegado"}""")
    }

    companion object {
        const val PARAMETRO = "comuna"
        const val CABECERA = "X-Comuna"
    }
}
