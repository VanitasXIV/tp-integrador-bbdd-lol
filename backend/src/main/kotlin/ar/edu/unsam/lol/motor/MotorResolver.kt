package ar.edu.unsam.lol.motor

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Decide qué motor usa cada request:
 * 1. el parámetro `?motor=` de la URL,
 * 2. si no está, el header `X-Motor`,
 * 3. si no está ninguno, el motor por defecto de la configuración.
 */
@Component
class MotorResolver(
    @Value("\${app.motor-por-defecto}") motorPorDefecto: String,
) {
    val porDefecto: Motor = Motor.desdeCodigo(motorPorDefecto)

    fun resolver(parametro: String?, header: String?): Motor {
        val valor = parametro?.takeIf { it.isNotBlank() } ?: header?.takeIf { it.isNotBlank() }
        return valor?.let { Motor.desdeCodigo(it) } ?: porDefecto
    }

    companion object {
        const val HEADER = "X-Motor"
        const val PARAMETRO = "motor"
    }
}
