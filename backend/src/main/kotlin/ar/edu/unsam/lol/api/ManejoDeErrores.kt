package ar.edu.unsam.lol.api

import ar.edu.unsam.lol.motor.MotorInvalidoException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

class NoEncontradoException(mensaje: String) : RuntimeException(mensaje)
class ConflictoException(mensaje: String) : RuntimeException(mensaje)
class DatosInvalidosException(val errores: List<String>) : RuntimeException(errores.joinToString("; "))

data class ErrorRespuesta(val mensaje: String, val errores: List<String> = emptyList())

/** Traduce las excepciones del dominio a códigos HTTP con un cuerpo JSON uniforme. */
@RestControllerAdvice
class ManejoDeErrores {

    @ExceptionHandler(NoEncontradoException::class)
    fun noEncontrado(e: NoEncontradoException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorRespuesta(e.message ?: "No encontrado"))

    @ExceptionHandler(ConflictoException::class)
    fun conflicto(e: ConflictoException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorRespuesta(e.message ?: "Conflicto"))

    @ExceptionHandler(DatosInvalidosException::class)
    fun datosInvalidos(e: DatosInvalidosException) =
        ResponseEntity.badRequest().body(ErrorRespuesta("Datos inválidos", e.errores))

    @ExceptionHandler(MotorInvalidoException::class)
    fun motorInvalido(e: MotorInvalidoException) =
        ResponseEntity.badRequest().body(ErrorRespuesta(e.message ?: "Motor inválido"))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun cuerpoIlegible(e: HttpMessageNotReadableException) =
        ResponseEntity.badRequest().body(ErrorRespuesta("El cuerpo del request no es un JSON válido o le faltan campos"))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun tipoInvalido(e: MethodArgumentTypeMismatchException) =
        ResponseEntity.badRequest().body(ErrorRespuesta("Valor inválido para '${e.name}': ${e.value}"))
}
