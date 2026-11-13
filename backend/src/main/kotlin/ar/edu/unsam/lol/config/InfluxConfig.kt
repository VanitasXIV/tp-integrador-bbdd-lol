package ar.edu.unsam.lol.config

import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "influx")
data class InfluxProperties(
    val url: String,
    val token: String,
    val org: String,
    val bucket: String,
)

@Configuration
class InfluxConfig {

    @Bean(destroyMethod = "close")
    fun influxDBClient(props: InfluxProperties): InfluxDBClient =
        InfluxDBClientFactory.create(props.url, props.token.toCharArray(), props.org, props.bucket)
}
