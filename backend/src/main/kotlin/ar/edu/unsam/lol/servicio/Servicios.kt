package ar.edu.unsam.lol.servicio

import ar.edu.unsam.lol.api.ConflictoException
import ar.edu.unsam.lol.api.DatosInvalidosException
import ar.edu.unsam.lol.api.NoEncontradoException
import ar.edu.unsam.lol.dominio.Lados
import ar.edu.unsam.lol.dominio.ObjetivosEquipo
import ar.edu.unsam.lol.dominio.PartidaInfo
import ar.edu.unsam.lol.dominio.ParticipanteResumen
import ar.edu.unsam.lol.dominio.PuntoCurvaOro
import ar.edu.unsam.lol.dominio.Snapshot
import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.repositorio.PartidaInfoRepository
import ar.edu.unsam.lol.repositorio.PartidaRepository
import ar.edu.unsam.lol.repositorio.ParticipanteRepository
import ar.edu.unsam.lol.repositorio.SnapshotRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

// =====================================================================
//  Resultados que devuelven los servicios (los controladores los
//  serializan tal cual a JSON).
// =====================================================================

data class PartidaListado(
    val id: String,
    val fechaInicio: LocalDateTime,
    // Datos que solo existen en PostgreSQL (null si la partida no está ahí).
    val duracionSegundos: Int?,
    val parche: String?,
    val equipoGanador: String?,
    val rangoPromedio: String?,
)

data class ParticipanteDetalle(
    val id: Long,
    val lado: Int,
    val equipo: String,
    val rol: String,
    val campeon: String,
    /** Estadísticas del último snapshot (InfluxDB); null si no tiene snapshots. */
    val estadisticasFinales: Snapshot?,
)

data class PartidaDetalle(
    val id: String,
    val fechaInicio: LocalDateTime,
    val duracionSegundos: Int,
    val duracion: String,
    val versionJuego: String,
    val parche: String,
    val ladoGanador: Int,
    val equipoGanador: String,
    val rangoPromedio: String,
    val participantes: List<ParticipanteDetalle>,
)

/** Cuerpo de alta y modificación de un snapshot. */
data class SnapshotEntrada(
    /** Obligatorio en el alta; en la modificación sale de la URL. */
    val minuto: Int? = null,
    val oroActual: Long,
    val oroTotal: Long,
    val cs: Long,
    val csJungla: Long,
    val xp: Long,
    val nivel: Long,
    val kills: Long,
    val deaths: Long,
    val assists: Long,
) {
    fun aSnapshot(minuto: Int) =
        Snapshot(minuto, oroActual, oroTotal, cs, csJungla, xp, nivel, kills, deaths, assists)
}

internal fun parche(versionJuego: String) = versionJuego.split(".").take(2).joinToString(".")
internal fun duracionTexto(segundos: Int) = "%02d:%02d".format(segundos / 60, segundos % 60)

// =====================================================================
//  Registro de implementaciones: Spring inyecta todas las implementaciones
//  de cada interfaz y acá se indexan por motor.
// =====================================================================

@Service
class RepositoriosPorMotor(
    partidas: List<PartidaRepository>,
    participantes: List<ParticipanteRepository>,
) {
    private val partidasPorMotor = partidas.associateBy { it.motor }
    private val participantesPorMotor = participantes.associateBy { it.motor }

    fun partidas(motor: Motor): PartidaRepository =
        partidasPorMotor[motor] ?: error("No hay PartidaRepository para $motor")

    fun participantes(motor: Motor): ParticipanteRepository =
        participantesPorMotor[motor] ?: error("No hay ParticipanteRepository para $motor")
}

// =====================================================================
//  Partidas
// =====================================================================

@Service
class PartidaService(
    private val repositorios: RepositoriosPorMotor,
    private val info: PartidaInfoRepository,
    private val snapshots: SnapshotRepository,
) {

    /** Listado polimórfico: id y fecha salen del motor elegido; el resto, de PostgreSQL. */
    fun listar(motor: Motor): List<PartidaListado> {
        val infoPorId = info.listar().associateBy { it.id }
        return repositorios.partidas(motor).listar().map { p ->
            val i = infoPorId[p.id]
            PartidaListado(
                id = p.id,
                fechaInicio = p.fechaInicio,
                duracionSegundos = i?.duracionSegundos,
                parche = i?.let { parche(it.versionJuego) },
                equipoGanador = i?.let { Lados.nombre(it.ladoGanador) },
                rangoPromedio = i?.rangoPromedio,
            )
        }
    }

    /** Detalle: participantes del motor elegido + estadísticas finales de InfluxDB. */
    fun detalle(partidaId: String, motor: Motor): PartidaDetalle {
        val partida = buscarPartida(partidaId)
        val finales = snapshots.ultimosPorParticipante(partidaId, partida.fechaInicio)
        val participantes = repositorios.participantes(motor).listarPorPartida(partidaId).map { p ->
            ParticipanteDetalle(p.id, p.lado, Lados.nombre(p.lado), p.rol, p.campeon, finales[p.id])
        }
        return PartidaDetalle(
            id = partida.id,
            fechaInicio = partida.fechaInicio,
            duracionSegundos = partida.duracionSegundos,
            duracion = duracionTexto(partida.duracionSegundos),
            versionJuego = partida.versionJuego,
            parche = parche(partida.versionJuego),
            ladoGanador = partida.ladoGanador,
            equipoGanador = Lados.nombre(partida.ladoGanador),
            rangoPromedio = partida.rangoPromedio,
            participantes = participantes,
        )
    }

    fun curvaOro(partidaId: String): List<PuntoCurvaOro> {
        val partida = buscarPartida(partidaId)
        return snapshots.curvaOro(partidaId, partida.fechaInicio)
    }

    fun objetivos(partidaId: String): List<ObjetivosEquipo> {
        val partida = buscarPartida(partidaId)
        return snapshots.objetivos(partidaId, partida.fechaInicio)
    }

    private fun buscarPartida(partidaId: String): PartidaInfo =
        info.buscar(partidaId) ?: throw NoEncontradoException("No existe la partida $partidaId")
}

// =====================================================================
//  Snapshots de participante (CRUD sobre InfluxDB)
// =====================================================================

@Service
class SnapshotService(
    private val info: PartidaInfoRepository,
    private val snapshots: SnapshotRepository,
) {
    /** Participante + partida a la que pertenece (la fecha de inicio ubica los puntos en el tiempo). */
    private data class Contexto(val participante: ParticipanteResumen, val partida: PartidaInfo)

    private fun contexto(partidaId: String, participanteId: Long): Contexto {
        val partida = info.buscar(partidaId) ?: throw NoEncontradoException("No existe la partida $partidaId")
        val participante = info.buscarParticipante(participanteId)
        if (participante == null || participante.partidaId != partidaId) {
            throw NoEncontradoException("El participante $participanteId no pertenece a la partida $partidaId")
        }
        return Contexto(participante, partida)
    }

    fun listar(partidaId: String, participanteId: Long): List<Snapshot> {
        val c = contexto(partidaId, participanteId)
        return snapshots.listar(c.participante, c.partida.fechaInicio)
    }

    fun obtener(partidaId: String, participanteId: Long, minuto: Int): Snapshot {
        val c = contexto(partidaId, participanteId)
        return snapshots.buscar(c.participante, c.partida.fechaInicio, minuto)
            ?: throw noExiste(participanteId, minuto)
    }

    fun crear(partidaId: String, participanteId: Long, entrada: SnapshotEntrada): Snapshot {
        val c = contexto(partidaId, participanteId)
        val minuto = entrada.minuto ?: throw DatosInvalidosException(listOf("minuto es obligatorio"))
        val snapshot = entrada.aSnapshot(minuto)
        validar(snapshot, c.partida)
        if (snapshots.buscar(c.participante, c.partida.fechaInicio, minuto) != null) {
            throw ConflictoException("El participante $participanteId ya tiene un snapshot en el minuto $minuto")
        }
        snapshots.guardar(c.participante, c.partida.fechaInicio, snapshot)
        return snapshot
    }

    fun modificar(partidaId: String, participanteId: Long, minuto: Int, entrada: SnapshotEntrada): Snapshot {
        val c = contexto(partidaId, participanteId)
        if (entrada.minuto != null && entrada.minuto != minuto) {
            throw DatosInvalidosException(listOf("El minuto no se puede modificar (URL: $minuto, cuerpo: ${entrada.minuto})"))
        }
        val snapshot = entrada.aSnapshot(minuto)
        validar(snapshot, c.partida)
        if (snapshots.buscar(c.participante, c.partida.fechaInicio, minuto) == null) {
            throw noExiste(participanteId, minuto)
        }
        snapshots.guardar(c.participante, c.partida.fechaInicio, snapshot)
        return snapshot
    }

    fun eliminar(partidaId: String, participanteId: Long, minuto: Int) {
        val c = contexto(partidaId, participanteId)
        if (snapshots.buscar(c.participante, c.partida.fechaInicio, minuto) == null) {
            throw noExiste(participanteId, minuto)
        }
        snapshots.eliminar(c.participante, c.partida.fechaInicio, minuto)
    }

    private fun noExiste(participanteId: Long, minuto: Int) =
        NoEncontradoException("El participante $participanteId no tiene snapshot en el minuto $minuto")

    companion object {
        /** En el dataset (parche 16) el nivel llega a 20. */
        const val NIVEL_MAXIMO = 20L

        /** Reglas de negocio de un snapshot; junta todos los errores en vez de cortar en el primero. */
        fun errores(s: Snapshot, partida: PartidaInfo): List<String> {
            val errores = mutableListOf<String>()
            val ultimoMinuto = (partida.duracionSegundos + 59) / 60
            if (s.minuto !in 1..ultimoMinuto) {
                errores += "minuto debe estar entre 1 y $ultimoMinuto (la partida dura ${duracionTexto(partida.duracionSegundos)})"
            }
            mapOf(
                "oroActual" to s.oroActual, "oroTotal" to s.oroTotal, "cs" to s.cs, "csJungla" to s.csJungla,
                "xp" to s.xp, "kills" to s.kills, "deaths" to s.deaths, "assists" to s.assists,
            ).filterValues { it < 0 }.keys.forEach { errores += "$it no puede ser negativo" }
            if (s.oroActual > s.oroTotal) errores += "oroActual no puede superar a oroTotal"
            if (s.nivel !in 1..NIVEL_MAXIMO) errores += "nivel debe estar entre 1 y $NIVEL_MAXIMO"
            return errores
        }
    }

    private fun validar(s: Snapshot, partida: PartidaInfo) {
        val errores = errores(s, partida)
        if (errores.isNotEmpty()) throw DatosInvalidosException(errores)
    }
}
