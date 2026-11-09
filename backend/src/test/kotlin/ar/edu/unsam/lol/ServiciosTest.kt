package ar.edu.unsam.lol

import ar.edu.unsam.lol.api.ConflictoException
import ar.edu.unsam.lol.api.DatosInvalidosException
import ar.edu.unsam.lol.api.NoEncontradoException
import ar.edu.unsam.lol.dominio.PartidaResumen
import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.servicio.PartidaService
import ar.edu.unsam.lol.servicio.RepositoriosPorMotor
import ar.edu.unsam.lol.servicio.SnapshotEntrada
import ar.edu.unsam.lol.servicio.SnapshotService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entrada(minuto: Int? = null, oroActual: Long = 300, oroTotal: Long = 1500, nivel: Long = 5, kills: Long = 1) =
    SnapshotEntrada(minuto, oroActual, oroTotal, 30, 0, 2000, nivel, kills, 0, 2)

class PartidaServiceTest {

    private val snapshots = FakeSnapshotRepository()
    private val repositorios = RepositoriosPorMotor(
        listOf(
            FakePartidaRepository(Motor.POSTGRES, listOf(PartidaResumen(PARTIDA, INICIO))),
            // Una partida que PostgreSQL no conoce: sirve para ver de qué motor salió el listado.
            FakePartidaRepository(Motor.INFLUX, listOf(PartidaResumen(PARTIDA, INICIO), PartidaResumen("EUW1_SOLO_INFLUX", INICIO))),
        ),
        listOf(
            FakeParticipanteRepository(Motor.POSTGRES, listOf(TOP_AZUL)),
            FakeParticipanteRepository(Motor.INFLUX, listOf(TOP_AZUL)),
        ),
    )
    private val servicio = PartidaService(repositorios, FakePartidaInfoRepository(), snapshots)

    @Test
    fun `el listado sale del motor elegido y se completa con datos de PostgreSQL`() {
        assertEquals(1, servicio.listar(Motor.POSTGRES).size)
        val influx = servicio.listar(Motor.INFLUX)
        assertEquals(2, influx.size)
        val conocida = influx.first { it.id == PARTIDA }
        assertEquals("16.2", conocida.parche)
        assertEquals("ROJO", conocida.equipoGanador)
        assertNull(influx.first { it.id == "EUW1_SOLO_INFLUX" }.duracionSegundos)
    }

    @Test
    fun `el detalle trae los participantes con sus estadisticas finales`() {
        snapshots.datos[TOP_AZUL.id to 5] = snapshot(5)
        snapshots.datos[TOP_AZUL.id to 10] = snapshot(10, oroTotal = 3000)
        val detalle = servicio.detalle(PARTIDA, Motor.INFLUX)
        assertEquals("25:28", detalle.duracion)
        assertEquals(3000, detalle.participantes.single().estadisticasFinales?.oroTotal)
        assertEquals("AZUL", detalle.participantes.single().equipo)
    }

    @Test
    fun `una partida inexistente da 404`() {
        assertThrows<NoEncontradoException> { servicio.detalle("EUW1_NO_EXISTE", Motor.POSTGRES) }
        assertThrows<NoEncontradoException> { servicio.curvaOro("EUW1_NO_EXISTE") }
    }
}

class SnapshotServiceTest {

    private val snapshots = FakeSnapshotRepository()
    private val servicio = SnapshotService(FakePartidaInfoRepository(), snapshots)

    @Test
    fun `alta, consulta, modificacion y baja`() {
        servicio.crear(PARTIDA, TOP_AZUL.id, entrada(minuto = 7))
        assertEquals(1L, servicio.obtener(PARTIDA, TOP_AZUL.id, 7).kills)

        servicio.modificar(PARTIDA, TOP_AZUL.id, 7, entrada(kills = 4))
        assertEquals(4L, servicio.obtener(PARTIDA, TOP_AZUL.id, 7).kills)
        assertEquals(listOf(7), servicio.listar(PARTIDA, TOP_AZUL.id).map { it.minuto })

        servicio.eliminar(PARTIDA, TOP_AZUL.id, 7)
        assertTrue(servicio.listar(PARTIDA, TOP_AZUL.id).isEmpty())
    }

    @Test
    fun `no se puede dar de alta dos veces el mismo minuto`() {
        servicio.crear(PARTIDA, TOP_AZUL.id, entrada(minuto = 7))
        assertThrows<ConflictoException> { servicio.crear(PARTIDA, TOP_AZUL.id, entrada(minuto = 7)) }
    }

    @Test
    fun `modificar o borrar un minuto sin snapshot da 404`() {
        assertThrows<NoEncontradoException> { servicio.modificar(PARTIDA, TOP_AZUL.id, 8, entrada()) }
        assertThrows<NoEncontradoException> { servicio.eliminar(PARTIDA, TOP_AZUL.id, 8) }
        assertThrows<NoEncontradoException> { servicio.obtener(PARTIDA, TOP_AZUL.id, 8) }
    }

    @Test
    fun `el participante tiene que pertenecer a la partida`() {
        assertThrows<NoEncontradoException> { servicio.listar(PARTIDA, OTRO.id) }
        assertThrows<NoEncontradoException> { servicio.listar(PARTIDA, 123456) }
        assertThrows<NoEncontradoException> { servicio.listar("EUW1_NO_EXISTE", TOP_AZUL.id) }
    }

    @Test
    fun `junta todos los errores de validacion`() {
        val error = assertThrows<DatosInvalidosException> {
            servicio.crear(PARTIDA, TOP_AZUL.id, entrada(minuto = 40, oroActual = 2000, oroTotal = 1500, nivel = 25, kills = -1))
        }
        assertEquals(4, error.errores.size, error.errores.toString())
        assertTrue(snapshots.datos.isEmpty())
    }

    @Test
    fun `el alta exige minuto y la modificacion no permite cambiarlo`() {
        assertThrows<DatosInvalidosException> { servicio.crear(PARTIDA, TOP_AZUL.id, entrada()) }
        servicio.crear(PARTIDA, TOP_AZUL.id, entrada(minuto = 7))
        assertThrows<DatosInvalidosException> { servicio.modificar(PARTIDA, TOP_AZUL.id, 7, entrada(minuto = 8)) }
    }
}
