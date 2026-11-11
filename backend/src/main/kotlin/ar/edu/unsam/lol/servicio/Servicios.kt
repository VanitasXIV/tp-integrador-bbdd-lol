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
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// =====================================================================
//  Resultados que devuelven los servicios (los controladores los
//  serializan tal cual a JSON).
// =====================================================================

// Los nombres de los campos siguen los tipos del frontend (frontend/src/types.ts).

/** Una fila del listado de partidas. */
data class PartidaListado(
    // Del motor elegido:
    val id: String,
    val fecha: LocalDateTime,
    // Solo en PostgreSQL (null si la partida no está ahí):
    val duracionSegundos: Int?,
    val equipoGanador: String?,
    val parche: String?,
    val rangoPromedio: String,
    // Solo en InfluxDB: oro total que juntaron los 10 jugadores en la partida.
    val oroTotal: Long,
)

/** Un participante con sus estadísticas al final de la partida. */
data class Jugador(
    val id: Long,
    /** El dataset no tiene nombres de invocador: se muestra el rol ("Top", "Jungla"…). */
    val nombre: String,
    val lado: Int,
    val equipo: String,
    val rol: String,
    val campeon: String,
    // Del último snapshot en InfluxDB (0 si no tiene snapshots).
    val kills: Long,
    val deaths: Long,
    val assists: Long,
    val oroTotal: Long,
    val cs: Long,
    val nivel: Long,
)

data class PartidaDetalle(
    val id: String,
    val fecha: LocalDateTime,
    val duracionSegundos: Int,
    val duracion: String,
    val equipoGanador: String,
    val ladoGanador: Int,
    val oroTotal: Long,
    val versionJuego: String,
    val parche: String,
    val rangoPromedio: String,
    val curvaOro: List<PuntoCurvaOro>,
    val jugadores: List<Jugador>,
)

/** Snapshot con el formato de "medición" del frontend: `id` es el minuto y `oro` el oro total. */
data class Medicion(
    val id: Int,
    val minuto: Int,
    val oro: Long,
    val oroActual: Long,
    val cs: Long,
    val csJungla: Long,
    val xp: Long,
    val nivel: Long,
    val kills: Long,
    val deaths: Long,
    val assists: Long,
) {
    companion object {
        fun de(s: Snapshot) =
            Medicion(s.minuto, s.minuto, s.oroTotal, s.oroActual, s.cs, s.csJungla, s.xp, s.nivel, s.kills, s.deaths, s.assists)
    }
}

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

    /**
     * Listado polimórfico: id y fecha salen del motor elegido; duración, ganador,
     * parche y rango, de PostgreSQL; el oro total, de InfluxDB.
     */
    fun listar(motor: Motor): List<PartidaListado> {
        val infoPorId = info.listar().associateBy { it.id }
        val oroPorPartida = snapshots.oroTotalPorPartida()
        return repositorios.partidas(motor).listar().map { p ->
            val i = infoPorId[p.id]
            PartidaListado(
                id = p.id,
                fecha = p.fechaInicio,
                duracionSegundos = i?.duracionSegundos,
                equipoGanador = i?.let { Lados.nombre(it.ladoGanador) },
                parche = i?.let { parche(it.versionJuego) },
                // Las partidas simuladas siempre se crean en PostgreSQL; el valor
                // por defecto mantiene la respuesta completa si se consulta una
                // partida histórica que solo existe en InfluxDB.
                rangoPromedio = i?.rangoPromedio ?: "UNRANKED",
                // Una partida simulada siempre tiene snapshots en InfluxDB. El 0
                // evita propagar null a clientes mientras se consulta una partida
                // histórica incompleta.
                oroTotal = oroPorPartida[p.id] ?: 0L,
            )
        }
    }

    /** Detalle: jugadores del motor elegido + estadísticas finales y curva de oro de InfluxDB. */
    fun detalle(partidaId: String, motor: Motor): PartidaDetalle {
        val partida = buscarPartida(partidaId)
        val finales = snapshots.ultimosPorParticipante(partidaId, partida.fechaInicio)
        val nombresRoles = info.nombresDeRoles()
        val jugadores = repositorios.participantes(motor).listarPorPartida(partidaId).map { p ->
            val f = finales[p.id]
            Jugador(
                id = p.id,
                nombre = nombresRoles[p.rol] ?: p.rol,
                lado = p.lado,
                equipo = Lados.nombre(p.lado),
                rol = p.rol,
                campeon = p.campeon,
                kills = f?.kills ?: 0,
                deaths = f?.deaths ?: 0,
                assists = f?.assists ?: 0,
                oroTotal = f?.oroTotal ?: 0,
                cs = f?.cs ?: 0,
                nivel = f?.nivel ?: 0,
            )
        }
        return PartidaDetalle(
            id = partida.id,
            fecha = partida.fechaInicio,
            duracionSegundos = partida.duracionSegundos,
            duracion = duracionTexto(partida.duracionSegundos),
            equipoGanador = Lados.nombre(partida.ladoGanador),
            ladoGanador = partida.ladoGanador,
            // Mismo cálculo que el listado: suma del último oro total de cada jugador.
            oroTotal = finales.values.sumOf { it.oroTotal },
            versionJuego = partida.versionJuego,
            parche = parche(partida.versionJuego),
            rangoPromedio = partida.rangoPromedio,
            curvaOro = snapshots.curvaOro(partidaId, partida.fechaInicio),
            jugadores = jugadores,
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

    @Transactional
    fun eliminar(partidaId: String) {
        val partida = buscarPartida(partidaId)
        if (!info.eliminar(partidaId)) throw NoEncontradoException("No existe la partida $partidaId")
        snapshots.eliminarPartida(partidaId, partida.fechaInicio)
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

    /** Los mismos snapshots con el formato de "medición" que usa el frontend. */
    fun mediciones(partidaId: String, participanteId: Long): List<Medicion> =
        listar(partidaId, participanteId).map(Medicion::de)

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
