package cl.midosis.paciente.aviso

/**
 * Una sección del aviso: un título y sus puntos, cada uno una frase corta. Una sección
 * [urgente] pide actuar de inmediato y se muestra destacada.
 */
data class Seccion(val titulo: String, val puntos: List<String>, val urgente: Boolean = false)

/**
 * Lo que el paciente lee al abrir MiDosis por primera vez: para qué sirve, qué no hace, cuándo
 * consultar a la farmacia o al médico, qué hacer ante una urgencia y el descargo de
 * responsabilidad. Se vuelve a mostrar cuando cambia la [VERSION] del texto, y se puede releer
 * desde la pantalla principal.
 *
 * Es un texto clínico y legal: no se cambia sin el visto bueno del químico farmacéutico asesor,
 * y cada cambio sube la [VERSION].
 */
object AvisoDeUso {

    const val VERSION = 1

    const val TITULO = "Antes de empezar"

    const val INTRODUCCION =
        "MiDosis te ayuda a seguir tu tratamiento. Lee esto una vez: explica qué hace la aplicación, " +
            "qué no hace y a quién acudir en cada caso."

    val SECCIONES: List<Seccion> = listOf(
        Seccion(
            "Para qué sirve MiDosis",
            listOf(
                "Te recuerda cada toma a la hora que corresponde, según lo que indicó la farmacia y tus horarios.",
                "Te avisa unos días antes de que se acabe lo que retiraste, para que vuelvas a la farmacia a tiempo.",
                "Guarda en tu teléfono lo que respondes a cada recordatorio: si tomaste o no el medicamento.",
                "Funciona sin internet y sin crear una cuenta. Tus datos no salen de tu teléfono.",
            ),
        ),
        Seccion(
            "Lo que MiDosis no hace",
            listOf(
                "No reemplaza a tu médico ni al químico farmacéutico. No diagnostica ni indica tratamientos.",
                "No cambia tu dosis. Si la aplicación y tu receta dicen algo distinto, sigue la receta y avisa a la farmacia.",
                "No es un servicio de urgencias: nadie recibe tus respuestas ni sabe si tomaste el medicamento.",
                "Las alarmas dependen de tu teléfono: si está apagado, sin batería o en silencio, puede que no suenen.",
            ),
        ),
        Seccion(
            "Consulta en tu farmacia si",
            listOf(
                "El nombre, la dosis o las horas que muestra la aplicación no coinciden con tu receta o con la caja.",
                "Tienes dudas sobre cómo tomar el medicamento: con comida, en ayunas, partido o entero.",
                "Olvidaste una o más dosis y no sabes si tomarla ahora o esperar a la siguiente.",
                "Se te acabó el medicamento, no te alcanza hasta el próximo retiro o no lo había en la farmacia.",
                "Perdiste el comprobante o el código no funciona.",
            ),
        ),
        Seccion(
            "Consulta a tu médico si",
            listOf(
                "Te sientes peor o notas un efecto que no esperabas desde que empezaste el medicamento.",
                "Quieres dejar el tratamiento o cambiarlo. No lo suspendas por tu cuenta.",
                "Sientes que el medicamento no te está haciendo efecto.",
                "Otro profesional te indicó un medicamento nuevo, o estás embarazada o amamantando.",
            ),
        ),
        Seccion(
            "Ante una urgencia",
            listOf(
                "Si te falta el aire, se te hinchan la cara o los labios, tienes dolor en el pecho o te desmayas, " +
                    "llama al SAMU (131) o ve al servicio de urgencia más cercano.",
                "Si tomaste más de la dosis indicada o alguien tomó tu medicamento por error, llama al Centro de " +
                    "Información Toxicológica (CITUC), que atiende las 24 horas: 2 2635 3800.",
            ),
            urgente = true,
        ),
        Seccion(
            "Responsabilidad",
            listOf(
                "MiDosis es una ayuda para recordar. La indicación válida es la de tu receta y la del químico " +
                    "farmacéutico que te atendió.",
                "Seguir el tratamiento sigue siendo decisión tuya y de tu equipo de salud. MiDosis no responde " +
                    "por una toma omitida o tardía, ni por usar la aplicación de una forma distinta a la indicada.",
            ),
        ),
    )

    const val BOTON = "Entiendo, continuar"
}
