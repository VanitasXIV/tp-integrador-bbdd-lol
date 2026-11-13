package ar.edu.unsam.lol

import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.motor.MotorInvalidoException
import ar.edu.unsam.lol.motor.MotorResolver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class MotorTest {

    @Test
    fun `reconoce los codigos sin importar mayusculas ni espacios`() {
        assertEquals(Motor.POSTGRES, Motor.desdeCodigo("postgres"))
        assertEquals(Motor.INFLUX, Motor.desdeCodigo(" INFLUX "))
    }

    @Test
    fun `un codigo desconocido es un error`() {
        assertThrows<MotorInvalidoException> { Motor.desdeCodigo("mongo") }
    }

    @Test
    fun `el parametro tiene prioridad sobre el header y el header sobre el default`() {
        val resolver = MotorResolver("postgres")
        assertEquals(Motor.INFLUX, resolver.resolver("influx", "postgres"))
        assertEquals(Motor.INFLUX, resolver.resolver(null, "influx"))
        assertEquals(Motor.INFLUX, resolver.resolver("", "influx"))
        assertEquals(Motor.POSTGRES, resolver.resolver(null, null))
    }

    @Test
    fun `el motor por defecto sale de la configuracion`() {
        assertEquals(Motor.INFLUX, MotorResolver("influx").resolver(null, null))
    }
}
