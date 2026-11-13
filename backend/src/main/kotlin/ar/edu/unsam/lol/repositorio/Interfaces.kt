package ar.edu.unsam.lol.repositorio

import ar.edu.unsam.lol.dominio.ObjetivosEquipo
import ar.edu.unsam.lol.dominio.PartidaInfo
import ar.edu.unsam.lol.dominio.PartidaResumen
import ar.edu.unsam.lol.dominio.ParticipanteResumen
import ar.edu.unsam.lol.dominio.PuntoCurvaOro
import ar.edu.unsam.lol.dominio.Snapshot
import ar.edu.unsam.lol.dominio.SnapshotEquipo
import ar.edu.unsam.lol.motor.Motor
import java.time.LocalDateTime

// =====================================================================
//  Repositorios polimórficos: una interfaz, una implementación por motor.
//  El servicio elige la implementación según el motor del request y no
//  sabe nada de SQL ni de Flux.
// =====================================================================

interface PartidaRepository {
    val motor: Motor

    /** Todas las partidas, de la más reciente a la más antigua. */
    fun listar(): List<PartidaResumen>
}

interface ParticipanteRepository {
    val motor: Motor

    /** Los participantes de una partida, ordenados por lado y por rol. */
    fun listarPorPartida(partidaId: String): List<ParticipanteResumen>
}

// =====================================================================
//  Repositorios de un solo motor: datos que existen solo en ese motor.
// =====================================================================

/** PostgreSQL: duración, ganador, rango y versión de las partidas. */
interface PartidaInfoRepository {
    fun listar(): List<PartidaInfo>
    fun buscar(partidaId: String): PartidaInfo?
    fun eliminar(partidaId: String): Boolean
    fun buscarParticipante(participanteId: Long): ParticipanteResumen?
    /** Código de rol -> nombre para mostrar ("TOP" -> "Top"). */
    fun nombresDeRoles(): Map<String, String>
}

/**
 * InfluxDB: snapshots de participantes y de equipos.
 * El tiempo de un punto es inicio de la partida + minuto, por eso las
 * operaciones reciben la fecha de inicio (que vive en PostgreSQL).
 */
interface SnapshotRepository {
    fun listar(participante: ParticipanteResumen, inicio: LocalDateTime): List<Snapshot>
    fun buscar(participante: ParticipanteResumen, inicio: LocalDateTime, minuto: Int): Snapshot?
    /** Alta o modificación: en InfluxDB escribir el mismo punto lo sobrescribe. */
    fun guardar(participante: ParticipanteResumen, inicio: LocalDateTime, snapshot: Snapshot)
    fun eliminar(participante: ParticipanteResumen, inicio: LocalDateTime, minuto: Int)

    /** Último snapshot de cada participante de la partida, por id de participante. */
    fun ultimosPorParticipante(partidaId: String, inicio: LocalDateTime): Map<Long, Snapshot>
    /** Por partida, la suma del último oro total de cada jugador. */
    fun oroTotalPorPartida(): Map<String, Long>
    /** Elimina los snapshots de jugadores y equipos de una partida. */
    fun eliminarPartida(partidaId: String, inicio: LocalDateTime)
    fun curvaOro(partidaId: String, inicio: LocalDateTime): List<PuntoCurvaOro>
    fun objetivos(partidaId: String, inicio: LocalDateTime): List<ObjetivosEquipo>

    /** Guarda un punto del measurement de estado de equipos. */
    fun guardarEquipo(partidaId: String, snapshot: SnapshotEquipo, inicio: LocalDateTime)
}
