package ar.edu.unsam.lol.dominio

import java.time.LocalDateTime

/** Lados del mapa con la convención de Riot. */
object Lados {
    const val AZUL = 100
    const val ROJO = 200
    fun nombre(lado: Int) = if (lado == AZUL) "AZUL" else "ROJO"
}

/** Orden en que se muestran los roles (el mismo que la columna `roles.orden`). */
val ORDEN_ROLES = listOf("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY")

// --- Resultados de las operaciones polimórficas (iguales en ambos motores) ---

/** Lo que ambos motores saben de una partida. */
data class PartidaResumen(
    val id: String,
    val fechaInicio: LocalDateTime,
)

/** Lo que ambos motores saben de un participante. */
data class ParticipanteResumen(
    val id: Long,
    val partidaId: String,
    val lado: Int,
    val rol: String,
    val campeon: String,
)

// --- Datos que existen solo en PostgreSQL ---

data class PartidaInfo(
    val id: String,
    val fechaInicio: LocalDateTime,
    val duracionSegundos: Int,
    val versionJuego: String,
    val ladoGanador: Int,
    val rangoPromedio: String,
)

// --- Datos que existen solo en InfluxDB ---

/** Estado de un participante en un minuto de la partida. */
data class Snapshot(
    val minuto: Int,
    val oroActual: Long,
    val oroTotal: Long,
    val cs: Long,
    val csJungla: Long,
    val xp: Long,
    val nivel: Long,
    val kills: Long,
    val deaths: Long,
    val assists: Long,
)

data class PuntoCurvaOro(
    val minuto: Int,
    val oroAzul: Long,
    val oroRojo: Long,
    /** Positivo = ventaja del lado azul. */
    val diferencia: Long,
)

data class ObjetivosEquipo(
    val minuto: Int,
    val lado: Int,
    val torres: Long,
    val inhibidores: Long,
    val dragonesFuego: Long,
    val dragonesAgua: Long,
    val dragonesTierra: Long,
    val dragonesAire: Long,
    val dragonesQuimtec: Long,
    val dragonesHextech: Long,
    val dragonesElder: Long,
    val barones: Long,
    val larvasVacio: Long,
    val heraldos: Long,
)

/** Punto sintético de un equipo usado por el simulador de partidas. */
data class SnapshotEquipo(
    val minuto: Int,
    val lado: Int,
    val torres: Long,
    val inhibidores: Long,
    val dragonesFuego: Long,
    val dragonesAgua: Long,
    val dragonesTierra: Long,
    val dragonesAire: Long,
    val dragonesQuimtec: Long,
    val dragonesHextech: Long,
    val dragonesElder: Long,
    val barones: Long,
    val larvasVacio: Long,
    val heraldos: Long,
)
