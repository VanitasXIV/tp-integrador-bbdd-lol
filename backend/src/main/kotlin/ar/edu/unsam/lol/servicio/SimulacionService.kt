package ar.edu.unsam.lol.servicio

import ar.edu.unsam.lol.dominio.Lados
import ar.edu.unsam.lol.dominio.ParticipanteResumen
import ar.edu.unsam.lol.dominio.Snapshot
import ar.edu.unsam.lol.dominio.SnapshotEquipo
import ar.edu.unsam.lol.repositorio.SnapshotRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.ThreadLocalRandom

/** Genera una partida sintética coherente con el modelo existente. */
@Service
class SimulacionService(
    private val jdbc: JdbcTemplate,
    private val snapshots: SnapshotRepository,
) {
    private val random = ThreadLocalRandom.current()
    private val rangos = listOf("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND", "MASTER", "GRANDMASTER", "CHALLENGER", "UNRANKED")

    @Transactional
    fun crear(): PartidaListado {
        val inicio = LocalDateTime.now(ZoneOffset.UTC).withNano(0)
        // Mismo formato que los IDs cargados desde el dataset: EUW1_<10 dígitos>.
        var numeroId = System.currentTimeMillis() / 1_000
        var id = "EUW1_$numeroId"
        while (jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM partidas WHERE id = ?)", Boolean::class.java, id) == true) {
            numeroId++
            id = "EUW1_$numeroId"
        }
        val duracion = random.nextInt(25, 41) * 60
        val version = "16.2.740.1491"
        val ganador = if (random.nextBoolean()) Lados.AZUL else Lados.ROJO
        val rango = rangos[random.nextInt(rangos.size)]

        jdbc.update(
            """INSERT INTO partidas (id, fecha_inicio, duracion_segundos, version_juego, lado_ganador, rango_promedio)
               VALUES (?, ?, ?, ?, ?, ?)""".trimIndent(),
            id, inicio, duracion, version, ganador, rango,
        )

        val campeones = jdbc.query("SELECT id, nombre FROM campeones ORDER BY random() LIMIT 10") { rs, _ ->
            rs.getInt("id") to rs.getString("nombre")
        }
        check(campeones.size == 10) { "Se necesitan al menos 10 campeones para simular una partida" }

        val roles = listOf("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY")
        val jugadores = mutableListOf<ParticipanteResumen>()
        val primerId = (jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM participantes", Long::class.java) ?: 0L) + 1L
        var jugadorId = primerId
        for (lado in listOf(Lados.AZUL, Lados.ROJO)) {
            for (rol in roles) {
                val campeon = campeones[jugadores.size]
                jdbc.update(
                    "INSERT INTO participantes (id, partida_id, lado, rol, campeon_id) VALUES (?, ?, ?, ?, ?)",
                    jugadorId, id, lado, rol, campeon.first,
                )
                jugadores += ParticipanteResumen(jugadorId, id, lado, rol, campeon.second)
                jugadorId++
            }
        }

        val minutos = (5..(duracion / 60) step 5).toList()
        val oroFinalPorJugador = mutableMapOf<Long, Long>()
        for (minuto in minutos) {
            jugadores.forEach { jugador ->
                val snapshot = snapshotJugador(minuto)
                snapshots.guardar(jugador, inicio, snapshot)
                oroFinalPorJugador[jugador.id] = snapshot.oroTotal
            }
            listOf(Lados.AZUL, Lados.ROJO).forEach { lado ->
                snapshots.guardarEquipo(id, snapshotEquipo(minuto, lado), inicio)
            }
        }

        return PartidaListado(
            id = id,
            fecha = inicio,
            duracionSegundos = duracion,
            equipoGanador = Lados.nombre(ganador),
            parche = parche(version),
            rangoPromedio = rango,
            oroTotal = oroFinalPorJugador.values.sum(),
        )
    }

    private fun snapshotJugador(minuto: Int) = Snapshot(
        minuto = minuto,
        oroActual = random.nextLong(100, 1_500),
        oroTotal = random.nextLong(1_000 + minuto * 250L, 2_000 + minuto * 500L),
        cs = random.nextLong(10, minuto * 10L + 30),
        csJungla = random.nextLong(0, minuto * 4L + 10),
        xp = 500L + minuto * 600L + random.nextLong(0, 500),
        nivel = (1 + minuto / 3).coerceAtMost(18).toLong(),
        kills = random.nextLong(0, (minuto / 5 + 2).toLong()),
        deaths = random.nextLong(0, (minuto / 6 + 2).toLong()),
        assists = random.nextLong(0, (minuto / 3 + 3).toLong()),
    )

    private fun snapshotEquipo(minuto: Int, lado: Int) = SnapshotEquipo(
        minuto = minuto,
        lado = lado,
        torres = random.nextLong(0, (minuto / 8 + 1).toLong()),
        inhibidores = random.nextLong(0, (minuto / 22 + 1).toLong()),
        dragonesFuego = random.nextLong(0, (minuto / 15 + 1).toLong()),
        dragonesAgua = random.nextLong(0, (minuto / 18 + 1).toLong()),
        dragonesTierra = random.nextLong(0, (minuto / 20 + 1).toLong()),
        dragonesAire = random.nextLong(0, (minuto / 20 + 1).toLong()),
        dragonesQuimtec = 0,
        dragonesHextech = 0,
        dragonesElder = 0,
        barones = random.nextLong(0, (minuto / 30 + 1).toLong()),
        larvasVacio = random.nextLong(0, (minuto / 10 + 1).toLong()),
        heraldos = random.nextLong(0, (minuto / 14 + 1).toLong()),
    )
}
