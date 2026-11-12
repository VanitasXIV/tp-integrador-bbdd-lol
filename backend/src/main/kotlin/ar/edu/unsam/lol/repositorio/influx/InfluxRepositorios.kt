package ar.edu.unsam.lol.repositorio.influx

import ar.edu.unsam.lol.config.InfluxProperties
import ar.edu.unsam.lol.dominio.Lados
import ar.edu.unsam.lol.dominio.ORDEN_ROLES
import ar.edu.unsam.lol.dominio.ObjetivosEquipo
import ar.edu.unsam.lol.dominio.PartidaResumen
import ar.edu.unsam.lol.dominio.ParticipanteResumen
import ar.edu.unsam.lol.dominio.PuntoCurvaOro
import ar.edu.unsam.lol.dominio.Snapshot
import ar.edu.unsam.lol.dominio.SnapshotEquipo
import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.repositorio.PartidaRepository
import ar.edu.unsam.lol.repositorio.ParticipanteRepository
import ar.edu.unsam.lol.repositorio.SnapshotRepository
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import com.influxdb.query.FluxRecord
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

// Nombres de measurements, tags y fields: los de database/scripts_nosql/generar_lp.py.
private const val M_PARTICIPANTE = "participant_snapshot"
private const val M_EQUIPO = "team_snapshot"

/** Rango que cubre todos los datos cuando no se conoce la fecha de la partida. */
private const val DESDE_SIEMPRE = "1970-01-01T00:00:00Z"

/** El primer snapshot de cada partida es el del minuto 5 (así viene el dataset). */
private const val MINUTO_PRIMER_SNAPSHOT = 5L

/** Los minutos válidos van de 1 a 90: el rango de una partida cubre 91 minutos. */
private val DURACION_RANGO: Duration = Duration.ofMinutes(91)

/** Incluye snapshots futuros que se escriben al simular una partida completa. */
private fun rangoCompleto() = "range(start: $DESDE_SIEMPRE, stop: ${Instant.now().plus(DURACION_RANGO)})"

/** Escapa un valor para usarlo dentro de un string de Flux (evita inyección). */
internal fun flux(valor: String): String = "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

internal fun LocalDateTime.utc(): Instant = toInstant(ZoneOffset.UTC)

private fun FluxRecord.long(campo: String): Long = (getValueByKey(campo) as Number?)?.toLong() ?: 0L
private fun FluxRecord.texto(campo: String): String = getValueByKey(campo).toString()
private fun minutoDe(instante: Instant, inicio: Instant): Int = Duration.between(inicio, instante).toMinutes().toInt()

/** Base común: acceso al cliente y al bucket. */
abstract class InfluxBase(protected val client: InfluxDBClient, protected val props: InfluxProperties) {
    protected fun consultar(query: String): List<FluxRecord> =
        client.queryApi.query(query, props.org).flatMap { it.records }

    protected val desde get() = "from(bucket: ${flux(props.bucket)})"
}

// =====================================================================
//  Implementaciones polimórficas (InfluxDB)
// =====================================================================

/**
 * Las partidas se obtienen de los snapshots de equipo (el CRUD no los toca).
 * InfluxDB no guarda la fecha de inicio: se calcula como el primer snapshot
 * menos 5 minutos.
 */
@Repository
class InfluxPartidaRepository(client: InfluxDBClient, props: InfluxProperties) :
    InfluxBase(client, props), PartidaRepository {

    override val motor = Motor.INFLUX

    override fun listar(): List<PartidaResumen> {
        val query = """
            $desde
              |> ${rangoCompleto()}
              |> filter(fn: (r) => r._measurement == "$M_EQUIPO" and r._field == "towers" and r.side == "${Lados.AZUL}")
              |> group(columns: ["match_id"])
              |> sort(columns: ["_time"])
              |> first()
              |> group()
        """.trimIndent()
        return consultar(query)
            .map { r ->
                val inicio = r.time!!.minus(Duration.ofMinutes(MINUTO_PRIMER_SNAPSHOT))
                PartidaResumen(r.texto("match_id"), LocalDateTime.ofInstant(inicio, ZoneOffset.UTC))
            }
            .sortedWith(compareByDescending<PartidaResumen> { it.fechaInicio }.thenBy { it.id })
    }
}

/** Los participantes se obtienen de los tags de sus snapshots. */
@Repository
class InfluxParticipanteRepository(client: InfluxDBClient, props: InfluxProperties) :
    InfluxBase(client, props), ParticipanteRepository {

    override val motor = Motor.INFLUX

    override fun listarPorPartida(partidaId: String): List<ParticipanteResumen> {
        val query = """
            $desde
              |> ${rangoCompleto()}
              |> filter(fn: (r) => r._measurement == "$M_PARTICIPANTE" and r._field == "total_gold" and r.match_id == ${flux(partidaId)})
              |> group(columns: ["participant_id", "side", "role", "champion"])
              |> last()
              |> group()
        """.trimIndent()
        return consultar(query)
            .map { r ->
                ParticipanteResumen(
                    id = r.texto("participant_id").toLong(),
                    partidaId = partidaId,
                    lado = r.texto("side").toInt(),
                    rol = r.texto("role"),
                    campeon = r.texto("champion"),
                )
            }
            .sortedWith(compareBy<ParticipanteResumen> { it.lado }.thenBy { ORDEN_ROLES.indexOf(it.rol) })
    }
}

// =====================================================================
//  Snapshots, curva de oro y objetivos (solo InfluxDB)
// =====================================================================

@Repository
class InfluxSnapshotRepository(client: InfluxDBClient, props: InfluxProperties) :
    InfluxBase(client, props), SnapshotRepository {

    private fun rango(inicio: Instant) = "range(start: $inicio, stop: ${inicio.plus(DURACION_RANGO)})"

    private fun aSnapshot(r: FluxRecord, inicio: Instant) = Snapshot(
        minuto = minutoDe(r.time!!, inicio),
        oroActual = r.long("current_gold"),
        oroTotal = r.long("total_gold"),
        cs = r.long("cs_count"),
        csJungla = r.long("jungle_cs"),
        xp = r.long("xp"),
        nivel = r.long("level"),
        kills = r.long("kills"),
        deaths = r.long("deaths"),
        assists = r.long("assists"),
    )

    private fun queryParticipante(participanteId: Long, rango: String) = """
        $desde
          |> $rango
          |> filter(fn: (r) => r._measurement == "$M_PARTICIPANTE" and r.participant_id == "$participanteId")
          |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
          |> group()
          |> sort(columns: ["_time"])
    """.trimIndent()

    override fun listar(participante: ParticipanteResumen, inicio: LocalDateTime): List<Snapshot> {
        val ini = inicio.utc()
        return consultar(queryParticipante(participante.id, rango(ini))).map { aSnapshot(it, ini) }
    }

    override fun buscar(participante: ParticipanteResumen, inicio: LocalDateTime, minuto: Int): Snapshot? {
        val ini = inicio.utc()
        val t = ini.plus(Duration.ofMinutes(minuto.toLong()))
        val rango = "range(start: $t, stop: ${t.plusSeconds(1)})"
        return consultar(queryParticipante(participante.id, rango)).firstOrNull()?.let { aSnapshot(it, ini) }
    }

    override fun guardar(participante: ParticipanteResumen, inicio: LocalDateTime, snapshot: Snapshot) {
        val t = inicio.utc().plus(Duration.ofMinutes(snapshot.minuto.toLong()))
        // Los tags tienen que ser exactamente los de la serie: si no, InfluxDB
        // crea una serie nueva en lugar de sobrescribir el punto.
        val punto = Point.measurement(M_PARTICIPANTE)
            .addTag("match_id", participante.partidaId)
            .addTag("participant_id", participante.id.toString())
            .addTag("side", participante.lado.toString())
            .addTag("role", participante.rol)
            .addTag("champion", participante.campeon)
            .addField("current_gold", snapshot.oroActual)
            .addField("total_gold", snapshot.oroTotal)
            .addField("cs_count", snapshot.cs)
            .addField("jungle_cs", snapshot.csJungla)
            .addField("xp", snapshot.xp)
            .addField("level", snapshot.nivel)
            .addField("kills", snapshot.kills)
            .addField("deaths", snapshot.deaths)
            .addField("assists", snapshot.assists)
            .time(t, WritePrecision.S)
        client.writeApiBlocking.writePoint(props.bucket, props.org, punto)
    }

    override fun guardarEquipo(partidaId: String, snapshot: SnapshotEquipo, inicio: LocalDateTime) {
        val t = inicio.utc().plus(Duration.ofMinutes(snapshot.minuto.toLong()))
        val punto = Point.measurement(M_EQUIPO)
            .addTag("match_id", partidaId)
            .addTag("side", snapshot.lado.toString())
            .addField("towers", snapshot.torres)
            .addField("inhibitors", snapshot.inhibidores)
            .addField("dragons_fire", snapshot.dragonesFuego)
            .addField("dragons_water", snapshot.dragonesAgua)
            .addField("dragons_earth", snapshot.dragonesTierra)
            .addField("dragons_air", snapshot.dragonesAire)
            .addField("dragons_chemtech", snapshot.dragonesQuimtec)
            .addField("dragons_hextech", snapshot.dragonesHextech)
            .addField("dragons_elder", snapshot.dragonesElder)
            .addField("barons", snapshot.barones)
            .addField("void_grubs", snapshot.larvasVacio)
            .addField("heralds", snapshot.heraldos)
            .time(t, WritePrecision.S)
        client.writeApiBlocking.writePoint(props.bucket, props.org, punto)
    }

    override fun eliminar(participante: ParticipanteResumen, inicio: LocalDateTime, minuto: Int) {
        val t = inicio.utc().plus(Duration.ofMinutes(minuto.toLong()))
        // InfluxDB no tiene DELETE por id: se borra por predicado sobre tags
        // en un rango de un segundo (el siguiente snapshot está al menos 1 minuto después).
        client.deleteApi.delete(
            OffsetDateTime.ofInstant(t, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(t.plusSeconds(1), ZoneOffset.UTC),
            "_measurement=\"$M_PARTICIPANTE\" AND participant_id=\"${participante.id}\"",
            props.bucket,
            props.org,
        )
    }

    override fun ultimosPorParticipante(partidaId: String, inicio: LocalDateTime): Map<Long, Snapshot> {
        val ini = inicio.utc()
        val query = """
            $desde
              |> ${rango(ini)}
              |> filter(fn: (r) => r._measurement == "$M_PARTICIPANTE" and r.match_id == ${flux(partidaId)})
              |> last()
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> group()
        """.trimIndent()
        return consultar(query).associate { it.texto("participant_id").toLong() to aSnapshot(it, ini) }
    }

    override fun oroTotalPorPartida(): Map<String, Long> {
        // last() actúa sobre cada serie (un jugador); después se suma por partida.
        val query = """
            $desde
              |> ${rangoCompleto()}
              |> filter(fn: (r) => r._measurement == "$M_PARTICIPANTE" and r._field == "total_gold")
              |> group(columns: ["match_id", "participant_id"])
              |> sort(columns: ["_time"])
              |> last()
              |> group(columns: ["match_id"])
              |> sum()
              |> group()
        """.trimIndent()
        return consultar(query).associate { it.texto("match_id") to it.long("_value") }
    }

    override fun eliminarPartida(partidaId: String, inicio: LocalDateTime) {
        val desde = inicio.utc()
        val hasta = desde.plus(DURACION_RANGO)
        val desdeUtc = OffsetDateTime.ofInstant(desde, ZoneOffset.UTC)
        val hastaUtc = OffsetDateTime.ofInstant(hasta, ZoneOffset.UTC)

        listOf(M_PARTICIPANTE, M_EQUIPO).forEach { medicion ->
            client.deleteApi.delete(
                desdeUtc,
                hastaUtc,
                "_measurement=${flux(medicion)} AND match_id=${flux(partidaId)}",
                props.bucket,
                props.org,
            )
        }
    }

    /** Consulta compleja (punto 7): oro total de cada lado por minuto y su diferencia. */
    override fun curvaOro(partidaId: String, inicio: LocalDateTime): List<PuntoCurvaOro> {
        val ini = inicio.utc()
        val query = """
            $desde
              |> ${rango(ini)}
              |> filter(fn: (r) => r._measurement == "$M_PARTICIPANTE" and r._field == "total_gold" and r.match_id == ${flux(partidaId)})
              |> group(columns: ["side", "_time"])
              |> sum()
              |> group()
              |> pivot(rowKey: ["_time"], columnKey: ["side"], valueColumn: "_value")
              |> sort(columns: ["_time"])
        """.trimIndent()
        return consultar(query).map { r ->
            val azul = r.long(Lados.AZUL.toString())
            val rojo = r.long(Lados.ROJO.toString())
            PuntoCurvaOro(minutoDe(r.time!!, ini), azul, rojo, azul - rojo)
        }
    }

    override fun objetivos(partidaId: String, inicio: LocalDateTime): List<ObjetivosEquipo> {
        val ini = inicio.utc()
        val query = """
            $desde
              |> ${rango(ini)}
              |> filter(fn: (r) => r._measurement == "$M_EQUIPO" and r.match_id == ${flux(partidaId)})
              |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
              |> group()
              |> sort(columns: ["side", "_time"])
        """.trimIndent()
        return consultar(query).map { r ->
            ObjetivosEquipo(
                minuto = minutoDe(r.time!!, ini),
                lado = r.texto("side").toInt(),
                torres = r.long("towers"),
                inhibidores = r.long("inhibitors"),
                dragonesFuego = r.long("dragons_fire"),
                dragonesAgua = r.long("dragons_water"),
                dragonesTierra = r.long("dragons_earth"),
                dragonesAire = r.long("dragons_air"),
                dragonesQuimtec = r.long("dragons_chemtech"),
                dragonesHextech = r.long("dragons_hextech"),
                dragonesElder = r.long("dragons_elder"),
                barones = r.long("barons"),
                larvasVacio = r.long("void_grubs"),
                heraldos = r.long("heralds"),
            )
        }
    }
}
