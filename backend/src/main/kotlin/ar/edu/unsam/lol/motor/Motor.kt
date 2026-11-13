package ar.edu.unsam.lol.motor

/** Motores de base de datos que pueden resolver las operaciones polimórficas. */
enum class Motor(val codigo: String) {
    POSTGRES("postgres"),
    INFLUX("influx");

    companion object {
        /** Acepta "postgres" / "influx" sin distinguir mayúsculas. */
        fun desdeCodigo(valor: String): Motor =
            entries.firstOrNull { it.codigo.equals(valor.trim(), ignoreCase = true) }
                ?: throw MotorInvalidoException(valor)
    }
}

class MotorInvalidoException(valor: String) :
    IllegalArgumentException(
        "Motor desconocido: '$valor'. Valores válidos: ${Motor.entries.joinToString { it.codigo }}"
    )
