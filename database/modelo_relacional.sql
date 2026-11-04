-- =====================================================================
--  TP Integrador - Base de Datos (UNSAM)
--  Análisis de partidas de League of Legends
--  Modelo relacional - PostgreSQL 16
--
--  Dataset: LoL Match Intervals: 2 Million In-Game Snapshots (Kaggle)
--  Análisis previo: documentacion/analisis_dataset.md
--
--  Orden de creación: catálogos -> partidas -> participantes y bans ->
--  snapshots -> vistas. El script se puede ejecutar varias veces: borra y
--  vuelve a crear todo.
-- =====================================================================

DROP VIEW  IF EXISTS v_resumen_partidas      CASCADE;
DROP VIEW  IF EXISTS v_estadisticas_finales  CASCADE;
DROP TABLE IF EXISTS snapshots_equipo        CASCADE;
DROP TABLE IF EXISTS snapshots_participante  CASCADE;
DROP TABLE IF EXISTS bans                    CASCADE;
DROP TABLE IF EXISTS participantes           CASCADE;
DROP TABLE IF EXISTS partidas                CASCADE;
DROP TABLE IF EXISTS roles                   CASCADE;
DROP TABLE IF EXISTS campeones               CASCADE;

-- ---------------------------------------------------------------------
-- Catálogos
-- ---------------------------------------------------------------------

-- Se usa el ID oficial de Riot como clave: es el mismo que aparece en los
-- bans del dataset. El ID 0 ("No Champion") del catálogo original no es un
-- campeón y no se carga.
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

-- Bans: en el dataset vienen como listas ("35,112,54,117,36") en un solo
-- campo (viola 1FN). Acá, una fila por ban. El turno sin ban (-1) no genera fila.
CREATE TABLE bans (
    partida_id  VARCHAR(20) NOT NULL REFERENCES partidas (id) ON DELETE CASCADE,
    lado        SMALLINT    NOT NULL CHECK (lado IN (100, 200)),
    turno       SMALLINT    NOT NULL CHECK (turno BETWEEN 1 AND 5),
    campeon_id  INTEGER     NOT NULL REFERENCES campeones (id),

    CONSTRAINT pk_bans PRIMARY KEY (partida_id, lado, turno),
    -- un equipo no banea dos veces al mismo campeón
    -- (los dos equipos sí pueden banear el mismo: es válido en Ranked)
    CONSTRAINT uq_bans_lado_campeon UNIQUE (partida_id, lado, campeon_id)
);

-- ---------------------------------------------------------------------
-- Participantes (un jugador dentro de una partida)
-- ---------------------------------------------------------------------

-- El dataset no identifica a las personas entre partidas: cada participación
-- tiene su propio ID. Se conserva ese ID porque es el que referencian los
-- snapshots. No se guarda participant_id (1-10): determina el lado y la
-- identidad natural ya es (partida, lado, rol).
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
-- Snapshots (estado de la partida cada 5 minutos)
-- ---------------------------------------------------------------------

-- Snapshot de un participante. Es la entidad del CRUD polimórfico: la misma
-- identidad (participante + minuto) existe en InfluxDB como
-- tag participant_id + _time (fecha_inicio de la partida + minuto).
-- No se guardan columnas derivadas: kills del equipo y diferencias de oro y
-- XP se calculan en las consultas.
CREATE TABLE snapshots_participante (
    participante_id  INTEGER  NOT NULL REFERENCES participantes (id) ON DELETE CASCADE,
    minuto           SMALLINT NOT NULL CHECK (minuto BETWEEN 1 AND 90),
    oro_actual       INTEGER  NOT NULL CHECK (oro_actual >= 0),   -- oro sin gastar
    oro_total        INTEGER  NOT NULL CHECK (oro_total  >= 0),   -- oro ganado en toda la partida
    cs               INTEGER  NOT NULL CHECK (cs         >= 0),   -- súbditos
    cs_jungla        INTEGER  NOT NULL CHECK (cs_jungla  >= 0),   -- monstruos de la jungla
    xp               INTEGER  NOT NULL CHECK (xp         >= 0),
    nivel            SMALLINT NOT NULL CHECK (nivel BETWEEN 1 AND 20),
    kills            SMALLINT NOT NULL CHECK (kills      >= 0),
    deaths           SMALLINT NOT NULL CHECK (deaths     >= 0),
    assists          SMALLINT NOT NULL CHECK (assists    >= 0),

    CONSTRAINT pk_snapshots_participante PRIMARY KEY (participante_id, minuto),
    CONSTRAINT ck_oro_actual_le_total CHECK (oro_actual <= oro_total)
);

-- Snapshot de un equipo. En el dataset estas columnas se repiten en las 5
-- filas de los jugadores del equipo: dependen de (partida, lado, minuto),
-- no del jugador. dragones_elder no existe en el dataset: se obtiene como
-- team_dragons menos la suma de los seis tipos.
CREATE TABLE snapshots_equipo (
    partida_id         VARCHAR(20) NOT NULL REFERENCES partidas (id) ON DELETE CASCADE,
    lado               SMALLINT    NOT NULL CHECK (lado IN (100, 200)),
    minuto             SMALLINT    NOT NULL CHECK (minuto BETWEEN 1 AND 90),
    torres             SMALLINT    NOT NULL CHECK (torres            >= 0),
    inhibidores        SMALLINT    NOT NULL CHECK (inhibidores       >= 0),
    dragones_fuego     SMALLINT    NOT NULL CHECK (dragones_fuego    >= 0),
    dragones_agua      SMALLINT    NOT NULL CHECK (dragones_agua     >= 0),
    dragones_tierra    SMALLINT    NOT NULL CHECK (dragones_tierra   >= 0),
    dragones_aire      SMALLINT    NOT NULL CHECK (dragones_aire     >= 0),
    dragones_quimtec   SMALLINT    NOT NULL CHECK (dragones_quimtec  >= 0),
    dragones_hextech   SMALLINT    NOT NULL CHECK (dragones_hextech  >= 0),
    dragones_elder     SMALLINT    NOT NULL CHECK (dragones_elder    >= 0),
    barones            SMALLINT    NOT NULL CHECK (barones           >= 0),
    larvas_vacio       SMALLINT    NOT NULL CHECK (larvas_vacio      >= 0),
    heraldos           SMALLINT    NOT NULL CHECK (heraldos          >= 0),

    CONSTRAINT pk_snapshots_equipo PRIMARY KEY (partida_id, lado, minuto)
);

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

-- Estadísticas finales de cada participante: su último snapshot.
-- (En el dataset no hay columnas de totales: salen de acá.)
CREATE VIEW v_estadisticas_finales AS
SELECT pa.id                          AS participante_id,
       pa.partida_id,
       pa.lado,
       pa.rol,
       c.nombre                       AS campeon,
       s.minuto                       AS ultimo_minuto,
       s.kills, s.deaths, s.assists,
       ROUND((s.kills + s.assists)::NUMERIC / GREATEST(s.deaths, 1), 2) AS kda,
       s.oro_total,
       s.cs + s.cs_jungla             AS cs_total,
       s.nivel,
       (pa.lado = p.lado_ganador)     AS gano
FROM participantes pa
JOIN partidas  p ON p.id = pa.partida_id
JOIN campeones c ON c.id = pa.campeon_id
JOIN LATERAL (
    SELECT *
    FROM snapshots_participante sp
    WHERE sp.participante_id = pa.id
    ORDER BY sp.minuto DESC
    LIMIT 1
) s ON TRUE;
