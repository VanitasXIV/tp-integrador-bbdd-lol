-- =====================================================================
--  TP Integrador - Base de Datos (UNSAM)
--  Análisis de partidas de League of Legends
--  Modelo relacional - PostgreSQL 16
--
--  Dataset: LoL Match Intervals: 2 Million In-Game Snapshots (Kaggle)
--  Análisis previo: documentacion/analisis_dataset.md
--
--  PostgreSQL guarda los datos estructurales de las partidas. La evolución
--  en el tiempo (snapshots de participantes y de equipos) se guarda en
--  InfluxDB.
--
--  Orden de creación: catálogos -> partidas -> participantes -> vistas.
--  El script se puede ejecutar varias veces: borra y vuelve a crear todo.
-- =====================================================================

DROP VIEW  IF EXISTS v_resumen_partidas CASCADE;
DROP TABLE IF EXISTS participantes      CASCADE;
DROP TABLE IF EXISTS partidas           CASCADE;
DROP TABLE IF EXISTS roles              CASCADE;
DROP TABLE IF EXISTS campeones          CASCADE;

-- ---------------------------------------------------------------------
-- Catálogos
-- ---------------------------------------------------------------------

-- Se usa el ID oficial de Riot como clave. El ID 0 ("No Champion") del
-- catálogo original no es un campeón y no se carga.
CREATE TABLE campeones (
    id      INTEGER     PRIMARY KEY CHECK (id > 0),
    nombre  VARCHAR(30) NOT NULL UNIQUE
);

-- Posiciones del mapa, con los códigos que usa Riot.
CREATE TABLE roles (
    codigo  VARCHAR(7)  PRIMARY KEY
        CHECK (codigo IN ('TOP', 'JUNGLE', 'MIDDLE', 'BOTTOM', 'UTILITY')),
    nombre  VARCHAR(20) NOT NULL UNIQUE,
    orden   SMALLINT    NOT NULL UNIQUE CHECK (orden BETWEEN 1 AND 5)
);

-- ---------------------------------------------------------------------
-- Partidas
-- ---------------------------------------------------------------------

-- Lados: 100 = azul, 200 = rojo (convención de Riot).
-- No se guardan columnas constantes en todo el dataset (región, cola, modo)
-- ni derivadas (parche = primer número de version_juego).
CREATE TABLE partidas (
    id                 VARCHAR(20) PRIMARY KEY
        CHECK (id ~ '^[A-Z0-9]+_[0-9]+$'),            -- ej. EUW1_7677509245
    fecha_inicio       TIMESTAMP   NOT NULL,         -- tal como viene en el dataset (sin zona horaria)
    duracion_segundos  INTEGER     NOT NULL CHECK (duracion_segundos > 0),
    version_juego      VARCHAR(20) NOT NULL
        CHECK (version_juego ~ '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'),
    lado_ganador       SMALLINT    NOT NULL CHECK (lado_ganador IN (100, 200)),
    rango_promedio     VARCHAR(11) NOT NULL
        CHECK (rango_promedio IN ('IRON', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM',
                                  'EMERALD', 'DIAMOND', 'MASTER', 'GRANDMASTER',
                                  'CHALLENGER', 'UNRANKED'))
);

-- ---------------------------------------------------------------------
-- Participantes (un jugador dentro de una partida)
-- ---------------------------------------------------------------------

-- El dataset no identifica a las personas entre partidas: cada participación
-- tiene su propio ID. Se conserva ese ID porque es el que usan los snapshots
-- en InfluxDB (tag participant_id). No se guarda participant_id (1-10):
-- determina el lado y la identidad natural ya es (partida, lado, rol).
CREATE TABLE participantes (
    id          INTEGER     PRIMARY KEY CHECK (id > 0),
    partida_id  VARCHAR(20) NOT NULL REFERENCES partidas (id) ON DELETE CASCADE,
    lado        SMALLINT    NOT NULL CHECK (lado IN (100, 200)),
    rol         VARCHAR(7)  NOT NULL REFERENCES roles (codigo),
    campeon_id  INTEGER     NOT NULL REFERENCES campeones (id),

    -- cada equipo tiene un único jugador por rol
    CONSTRAINT uq_participantes_lado_rol UNIQUE (partida_id, lado, rol),
    -- un campeón no puede repetirse dentro de la misma partida
    CONSTRAINT uq_participantes_campeon  UNIQUE (partida_id, campeon_id)
);

CREATE INDEX ix_participantes_campeon ON participantes (campeon_id);

-- ---------------------------------------------------------------------
-- Vistas: atributos derivados que no se almacenan
-- ---------------------------------------------------------------------

-- Partida con su parche, su duración legible y el nombre del lado ganador.
CREATE VIEW v_resumen_partidas AS
SELECT p.id,
       p.fecha_inicio,
       p.duracion_segundos,
       TO_CHAR(MAKE_INTERVAL(secs => p.duracion_segundos), 'MI:SS') AS duracion,
       p.version_juego,
       SPLIT_PART(p.version_juego, '.', 1)::INTEGER                 AS parche,
       p.lado_ganador,
       CASE p.lado_ganador WHEN 100 THEN 'AZUL' ELSE 'ROJO' END     AS equipo_ganador,
       p.rango_promedio
FROM partidas p;
