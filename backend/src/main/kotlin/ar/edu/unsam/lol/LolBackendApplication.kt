package ar.edu.unsam.lol

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LolBackendApplication

fun main(args: Array<String>) {
    runApplication<LolBackendApplication>(*args)
}
