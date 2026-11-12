package ar.edu.unsam.lol.api

import ar.edu.unsam.lol.dominio.ObjetivosEquipo
import ar.edu.unsam.lol.dominio.PuntoCurvaOro
import ar.edu.unsam.lol.dominio.Snapshot
import ar.edu.unsam.lol.motor.Motor
import ar.edu.unsam.lol.motor.MotorResolver
import ar.edu.unsam.lol.servicio.Medicion
import ar.edu.unsam.lol.servicio.PartidaDetalle
import ar.edu.unsam.lol.servicio.PartidaListado
import ar.edu.unsam.lol.servicio.PartidaService
import ar.edu.unsam.lol.servicio.SnapshotEntrada
import ar.edu.unsam.lol.servicio.SnapshotService
import ar.edu.unsam.lol.servicio.SimulacionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Respuesta con el header X-Motor, para que el frontend sepa qué motor resolvió el listado. */
private fun <T> conMotor(motor: Motor, cuerpo: T): ResponseEntity<T> =
    ResponseEntity.ok().header(MotorResolver.HEADER, motor.codigo).body(cuerpo)

data class MotorInfo(val porDefecto: String, val disponibles: List<String>)

@RestController
class MotorController(private val resolver: MotorResolver) {

    @GetMapping("/motor")
    fun motor() = MotorInfo(resolver.porDefecto.codigo, Motor.entries.map { it.codigo })
}

@RestController
@RequestMapping("/partidas")
class PartidaController(
    private val servicio: PartidaService,
    private val snapshots: SnapshotService,
    private val resolver: MotorResolver,
    private val simulacion: SimulacionService,
) {

    @PostMapping("/simulada")
    fun crearSimulada(): ResponseEntity<PartidaListado> =
        ResponseEntity.status(HttpStatus.CREATED).body(simulacion.crear())

    @GetMapping
    fun listar(
        @RequestParam(MotorResolver.PARAMETRO, required = false) parametro: String?,
        @RequestHeader(MotorResolver.HEADER, required = false) header: String?,
    ): ResponseEntity<List<PartidaListado>> {
        val motor = resolver.resolver(parametro, header)
        return conMotor(motor, servicio.listar(motor))
    }

    @GetMapping("/{partidaId}")
    fun detalle(
        @PathVariable partidaId: String,
        @RequestParam(MotorResolver.PARAMETRO, required = false) parametro: String?,
        @RequestHeader(MotorResolver.HEADER, required = false) header: String?,
    ): ResponseEntity<PartidaDetalle> {
        val motor = resolver.resolver(parametro, header)
        return conMotor(motor, servicio.detalle(partidaId, motor))
    }

    @DeleteMapping("/{partidaId}")
    fun eliminar(@PathVariable partidaId: String): ResponseEntity<Void> {
        servicio.eliminar(partidaId)
        return ResponseEntity.noContent().build()
    }

    /** Snapshots de un jugador con el formato que usa el frontend (solo lectura). */
    @GetMapping("/{partidaId}/jugadores/{jugadorId}/mediciones")
    fun mediciones(@PathVariable partidaId: String, @PathVariable jugadorId: Long): List<Medicion> =
        snapshots.mediciones(partidaId, jugadorId)

    @GetMapping("/{partidaId}/curva-oro")
    fun curvaOro(@PathVariable partidaId: String): List<PuntoCurvaOro> = servicio.curvaOro(partidaId)

    @GetMapping("/{partidaId}/objetivos")
    fun objetivos(@PathVariable partidaId: String): List<ObjetivosEquipo> = servicio.objetivos(partidaId)
}

@RestController
@RequestMapping("/partidas/{partidaId}/participantes/{participanteId}/snapshots")
class SnapshotController(private val servicio: SnapshotService) {

    @GetMapping
    fun listar(@PathVariable partidaId: String, @PathVariable participanteId: Long): List<Snapshot> =
        servicio.listar(partidaId, participanteId)

    @GetMapping("/{minuto}")
    fun obtener(
        @PathVariable partidaId: String,
        @PathVariable participanteId: Long,
        @PathVariable minuto: Int,
    ): Snapshot = servicio.obtener(partidaId, participanteId, minuto)

    @PostMapping
    fun crear(
        @PathVariable partidaId: String,
        @PathVariable participanteId: Long,
        @RequestBody entrada: SnapshotEntrada,
    ): ResponseEntity<Snapshot> =
        ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(partidaId, participanteId, entrada))

    @PutMapping("/{minuto}")
    fun modificar(
        @PathVariable partidaId: String,
        @PathVariable participanteId: Long,
        @PathVariable minuto: Int,
        @RequestBody entrada: SnapshotEntrada,
    ): Snapshot = servicio.modificar(partidaId, participanteId, minuto, entrada)

    @DeleteMapping("/{minuto}")
    fun eliminar(
        @PathVariable partidaId: String,
        @PathVariable participanteId: Long,
        @PathVariable minuto: Int,
    ): ResponseEntity<Void> {
        servicio.eliminar(partidaId, participanteId, minuto)
        return ResponseEntity.noContent().build()
    }
}
