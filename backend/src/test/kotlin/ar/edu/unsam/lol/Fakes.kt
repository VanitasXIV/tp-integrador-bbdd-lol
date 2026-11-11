package ar.edu.unsam.lol

import ar.edu.unsam.lol.dominio.ObjetivosEquipo
import ar.edu.unsam.lol.dominio.PartidaInfo
import ar.edu.unsam.lol.dominio.PartidaResumen
import ar.edu.unsam.lol.dominio.ParticipanteResumen
import ar.edu.unsam.lol.dominio.PuntoCurvaOro
import ar.edu.unsam.lol.dominio.Snapshot
import ar.edu.unsam.lol.dominio.SnapshotEquipo
import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.repositorio.PartidaInfoRepository
import ar.edu.unsam.lol.repositorio.PartidaRepository
import ar.edu.unsam.lol.repositorio.ParticipanteRepository
import ar.edu.unsam.lol.repositorio.SnapshotRepository
import java.time.LocalDateTime

// Repositorios en memoria para probar los servicios sin bases de datos.

val INICIO: LocalDateTime = LocalDateTime.of(2026, 1, 23, 8, 21, 47)
const val PARTIDA = "EUW1_7697601501"

val PARTIDA_INFO = PartidaInfo(PARTIDA, INICIO, 1528, "16.2.740.1491", 200, "MASTER")
val TOP_AZUL = ParticipanteResumen(5021, PARTIDA, 100, "TOP", "Kennen")
val OTRO = ParticipanteResumen(9999, "EUW1_OTRA", 100, "TOP", "Garen")

fun snapshot(minuto: Int, oroActual: Long = 300, oroTotal: Long = 1500, nivel: Long = 5) =
    Snapshot(minuto, oroActual, oroTotal, 30, 0, 2000, nivel, 1, 0, 2)

class FakePartidaRepository(override val motor: Motor, private val partidas: List<PartidaResumen>) : PartidaRepository {
    override fun listar() = partidas
}

class FakeParticipanteRepository(override val motor: Motor, private val participantes: List<ParticipanteResumen>) :
    ParticipanteRepository {
    override fun listarPorPartida(partidaId: String) = participantes.filter { it.partidaId == partidaId }
}

class FakePartidaInfoRepository(
    private val partidas: List<PartidaInfo> = listOf(PARTIDA_INFO),
    private val participantes: List<ParticipanteResumen> = listOf(TOP_AZUL, OTRO),
) : PartidaInfoRepository {
    override fun listar() = partidas
    override fun buscar(partidaId: String) = partidas.find { it.id == partidaId }
    override fun buscarParticipante(participanteId: Long) = participantes.find { it.id == participanteId }
    override fun nombresDeRoles() = mapOf("TOP" to "Top", "JUNGLE" to "Jungla", "MIDDLE" to "Medio", "BOTTOM" to "Tirador", "UTILITY" to "Soporte")
}

class FakeSnapshotRepository : SnapshotRepository {
    /** (id participante, minuto) -> snapshot */
    val datos = mutableMapOf<Pair<Long, Int>, Snapshot>()

    override fun listar(participante: ParticipanteResumen, inicio: LocalDateTime) =
        datos.filterKeys { it.first == participante.id }.values.sortedBy { it.minuto }

    override fun buscar(participante: ParticipanteResumen, inicio: LocalDateTime, minuto: Int) =
        datos[participante.id to minuto]

    override fun guardar(participante: ParticipanteResumen, inicio: LocalDateTime, snapshot: Snapshot) {
        datos[participante.id to snapshot.minuto] = snapshot
    }

    override fun eliminar(participante: ParticipanteResumen, inicio: LocalDateTime, minuto: Int) {
        datos.remove(participante.id to minuto)
    }

    override fun ultimosPorParticipante(partidaId: String, inicio: LocalDateTime) =
        datos.entries.groupBy { it.key.first }.mapValues { (_, v) -> v.maxBy { it.key.second }.value }

    /** Todos los snapshots del fake son de PARTIDA. */
    override fun oroTotalPorPartida() =
        if (datos.isEmpty()) emptyMap() else mapOf(PARTIDA to ultimosPorParticipante(PARTIDA, INICIO).values.sumOf { it.oroTotal })

    override fun curvaOro(partidaId: String, inicio: LocalDateTime) = listOf(PuntoCurvaOro(5, 8229, 10080, -1851))
    override fun objetivos(partidaId: String, inicio: LocalDateTime) = emptyList<ObjetivosEquipo>()
    override fun guardarEquipo(partidaId: String, snapshot: SnapshotEquipo, inicio: LocalDateTime) = Unit
}
