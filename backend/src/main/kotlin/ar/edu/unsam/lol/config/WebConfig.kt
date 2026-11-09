package ar.edu.unsam.lol.config

import ar.edu.unsam.lol.motor.MotorResolver
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Permite que el frontend (Vite, otro puerto) llame al backend desde el navegador. */
@Configuration
class WebConfig(
    @Value("\${app.cors-origenes}") private val origenes: String,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(*origenes.split(",").map { it.trim() }.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders(MotorResolver.HEADER)
    }
}
