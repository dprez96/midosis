package cl.midosis.paciente.codigo

import cl.midosis.credencial.CodigoCorto
import kotlin.test.Test
import kotlin.test.assertEquals

class ClienteDeCanjeTest {

    @Test
    fun `un canje correcto entrega el texto del código para verificarlo`() {
        assertEquals(
            ClienteDeCanje.Respuesta.Codigo("MD1:ABC"),
            ClienteDeCanje.interpretar(200, """{"codigo":"MD1:ABC"}"""),
        )
    }

    @Test
    fun `un PIN equivocado muestra el mensaje de core y los intentos que quedan`() {
        assertEquals(
            ClienteDeCanje.Respuesta.Rechazo("El PIN no corresponde a ese código. Te quedan 9 intentos."),
            ClienteDeCanje.interpretar(422, """{"error":"El PIN no corresponde a ese código.","intentosRestantes":9}"""),
        )
        assertEquals(
            ClienteDeCanje.Respuesta.Rechazo("El PIN no corresponde a ese código. Te queda 1 intento."),
            ClienteDeCanje.interpretar(422, """{"error":"El PIN no corresponde a ese código.","intentosRestantes":1}"""),
        )
    }

    @Test
    fun `los demás rechazos se muestran tal cual y una respuesta extraña no se acepta`() {
        assertEquals(
            ClienteDeCanje.Respuesta.Rechazo("Demasiados intentos. Espera unos minutos y vuelve a probar."),
            ClienteDeCanje.interpretar(429, """{"error":"Demasiados intentos. Espera unos minutos y vuelve a probar."}"""),
        )
        val extraña = ClienteDeCanje.interpretar(502, "<html>error</html>")
        assertEquals(true, extraña is ClienteDeCanje.Respuesta.Rechazo)
        assertEquals(true, ClienteDeCanje.interpretar(200, "{}") is ClienteDeCanje.Respuesta.Rechazo)
    }

    @Test
    fun `sin dirección de core no intenta conectarse`() {
        val cliente = ClienteDeCanje("")
        assertEquals(ClienteDeCanje.Respuesta.NoDisponible, cliente.canjear(CodigoCorto.de("0000000000")!!, "1234"))
    }
}
