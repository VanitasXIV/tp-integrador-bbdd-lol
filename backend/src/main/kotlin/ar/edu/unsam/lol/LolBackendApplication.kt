package ar.edu.unsam.lol

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
@ConfigurationPropertiesScan
class LolBackendApplication {
    // Se ejecuta apenas se carga la clase, antes de abrir cualquier conexión:
    // cubre también los tests con @SpringBootTest, que no pasan por main().
    companion object {
        init {
            usarUtc()
        }
    }
}

/**
 * Todas las fechas del TP están en UTC (así las cargan PostgreSQL e InfluxDB).
 * Además, el driver de PostgreSQL le manda a la base la zona horaria de la JVM,
 * y algunos nombres viejos (como America/Buenos_Aires) ya no los acepta.
 */
fun usarUtc() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

fun main(args: Array<String>) {
    usarUtc()
    runApplication<LolBackendApplication>(*args)
}
