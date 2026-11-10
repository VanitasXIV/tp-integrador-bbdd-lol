package ar.edu.unsam.lol.repositorio.postgres

import ar.edu.unsam.lol.dominio.PartidaInfo
import ar.edu.unsam.lol.dominio.PartidaResumen
import ar.edu.unsam.lol.dominio.ParticipanteResumen
import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.repositorio.PartidaInfoRepository
import ar.edu.unsam.lol.repositorio.PartidaRepository
import ar.edu.unsam.lol.repositorio.ParticipanteRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

private val participanteMapper = RowMapper { rs, _ ->
    ParticipanteResumen(
        id = rs.getLong("id"),
        partidaId = rs.getString("partida_id"),
        lado = rs.getInt("lado"),
        rol = rs.getString("rol"),
        campeon = rs.getString("campeon"),
    )
}

private const val SELECT_PARTICIPANTE = """
    SELECT pa.id, pa.partida_id, pa.lado, pa.rol, c.nombre AS campeon
    FROM participantes pa
    JOIN campeones c ON c.id = pa.campeon_id
    JOIN roles r     ON r.codigo = pa.rol
"""

@Repository
class PostgresPartidaRepository(private val jdbc: JdbcTemplate) : PartidaRepository {
    override val motor = Motor.POSTGRES

    override fun listar(): List<PartidaResumen> =
        jdbc.query(
            "SELECT id, fecha_inicio FROM partidas ORDER BY fecha_inicio DESC, id",
        ) { rs, _ ->
            PartidaResumen(rs.getString("id"), rs.getObject("fecha_inicio", LocalDateTime::class.java))
        }
}

@Repository
class PostgresParticipanteRepository(private val jdbc: JdbcTemplate) : ParticipanteRepository {
    override val motor = Motor.POSTGRES

    override fun listarPorPartida(partidaId: String): List<ParticipanteResumen> =
        jdbc.query("$SELECT_PARTICIPANTE WHERE pa.partida_id = ? ORDER BY pa.lado, r.orden", participanteMapper, partidaId)
}

@Repository
class PostgresPartidaInfoRepository(private val jdbc: JdbcTemplate) : PartidaInfoRepository {

    private val mapper = RowMapper { rs, _ ->
        PartidaInfo(
            id = rs.getString("id"),
            fechaInicio = rs.getObject("fecha_inicio", LocalDateTime::class.java),
            duracionSegundos = rs.getInt("duracion_segundos"),
            versionJuego = rs.getString("version_juego"),
            ladoGanador = rs.getInt("lado_ganador"),
            rangoPromedio = rs.getString("rango_promedio"),
        )
    }

    private val select =
        "SELECT id, fecha_inicio, duracion_segundos, version_juego, lado_ganador, rango_promedio FROM partidas"

    override fun listar(): List<PartidaInfo> = jdbc.query("$select ORDER BY fecha_inicio DESC, id", mapper)

    override fun buscar(partidaId: String): PartidaInfo? =
        jdbc.query("$select WHERE id = ?", mapper, partidaId).firstOrNull()

    override fun buscarParticipante(participanteId: Long): ParticipanteResumen? =
        jdbc.query("$SELECT_PARTICIPANTE WHERE pa.id = ?", participanteMapper, participanteId).firstOrNull()

    override fun nombresDeRoles(): Map<String, String> =
        jdbc.query("SELECT codigo, nombre FROM roles") { rs, _ -> rs.getString("codigo") to rs.getString("nombre") }.toMap()
}
