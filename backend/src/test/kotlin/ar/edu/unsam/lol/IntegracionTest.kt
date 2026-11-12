package ar.edu.unsam.lol

import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.dominio.ParticipanteResumen
import ar.edu.unsam.lol.dominio.Snapshot
import ar.edu.unsam.lol.repositorio.SnapshotRepository
import ar.edu.unsam.lol.servicio.PartidaService
import ar.edu.unsam.lol.servicio.RepositoriosPorMotor
import ar.edu.unsam.lol.servicio.SnapshotEntrada
import ar.edu.unsam.lol.servicio.SnapshotService
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Corre contra las bases de Docker Compose con los datos iniciales cargados:
 *   docker compose up -d
 *   ./gradlew testIntegracion
 */
@Tag("integracion")
@SpringBootTest
class IntegracionTest(
    @Autowired private val repositorios: RepositoriosPorMotor,
    @Autowired private val snapshots: SnapshotService,
    @Autowired private val partidas: PartidaService,
    @Autowired private val snapshotRepository: SnapshotRepository,
) {

    @Test
    fun `los dos motores devuelven las mismas 100 partidas`() {
        val postgres = repositorios.partidas(Motor.POSTGRES).listar()
        val influx = repositorios.partidas(Motor.INFLUX).listar()
        assertEquals(100, postgres.size)
        assertEquals(postgres, influx)
    }

    @Test
    fun `los dos motores devuelven los mismos 1000 participantes`() {
        val partidas = repositorios.partidas(Motor.POSTGRES).listar()
        val postgres = partidas.flatMap { repositorios.participantes(Motor.POSTGRES).listarPorPartida(it.id) }
        val influx = partidas.flatMap { repositorios.participantes(Motor.INFLUX).listarPorPartida(it.id) }
        assertEquals(1000, postgres.size)
        assertEquals(postgres, influx)
    }

    @Test
    fun `listado y detalle iguales con los dos motores y con oro total`() {
        val postgres = partidas.listar(Motor.POSTGRES)
        assertEquals(postgres, partidas.listar(Motor.INFLUX))
        assertEquals(100, postgres.count { (it.oroTotal ?: 0) > 0 })

        val detalle = partidas.detalle(PARTIDA, Motor.POSTGRES)
        assertEquals(detalle, partidas.detalle(PARTIDA, Motor.INFLUX))
        assertEquals(10, detalle.jugadores.size)
        assertEquals(postgres.first { it.id == PARTIDA }.oroTotal, detalle.oroTotal)
        assertEquals(snapshots.listar(PARTIDA, TOP_AZUL.id).size, detalle.curvaOro.size)
    }

    @Test
    fun `el oro de snapshots futuros aparece en el listado`() {
        val partidaId = "SIM_TEST_${UUID.randomUUID()}"
        val participante = ParticipanteResumen(9_999_999_999L, partidaId, 100, "TOP", "Test")
        val inicio = LocalDateTime.now(ZoneOffset.UTC).withNano(0)
        val snapshot = Snapshot(5, 300, 12_345, 30, 0, 2_000, 5, 0, 0, 0)

        try {
            snapshotRepository.guardar(participante, inicio, snapshot)
            assertEquals(12_345L, snapshotRepository.oroTotalPorPartida()[partidaId])
        } finally {
            snapshotRepository.eliminar(participante, inicio, snapshot.minuto)
        }
    }

    @Test
    fun `CRUD de un snapshot en InfluxDB`() {
        // Minuto 7: el dataset solo tiene múltiplos de 5, así que no pisa datos reales.
        val partida = PARTIDA
        val participante = TOP_AZUL.id
        val alta = SnapshotEntrada(7, 100, 2000, 40, 0, 2500, 6, 1, 0, 0)
        try {
            snapshots.crear(partida, participante, alta)
            assertEquals(2000, snapshots.obtener(partida, participante, 7).oroTotal)

            snapshots.modificar(partida, participante, 7, alta.copy(minuto = null, kills = 3))
            assertEquals(3, snapshots.obtener(partida, participante, 7).kills)
            assertEquals(1, snapshots.listar(partida, participante).count { it.minuto == 7 })
        } finally {
            runCatching { snapshots.eliminar(partida, participante, 7) }
        }
        assertNull(snapshots.listar(partida, participante).find { it.minuto == 7 })
    }
}
