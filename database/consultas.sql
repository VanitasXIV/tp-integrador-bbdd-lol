-- =====================================================================
-- Consultas representativas - TP Integrador LoL (UNSAM)
-- Ejecutar sobre la base luego de modelo_relacional.sql y datos_iniciales.sql.
-- =====================================================================

-- 1. Listado de partidas: fecha, duración, versión, ganador y rango promedio.
SELECT
    id AS partida_id,
    fecha_inicio,
    duracion_segundos,
    version_juego,
    equipo_ganador,
    rango_promedio
FROM v_resumen_partidas
ORDER BY fecha_inicio DESC;

-- 2. Participantes de una partida, con el nombre de su campeón y rol.
-- Reemplazar el match_id por el identificador de la partida deseada.
SELECT
    p.partida_id,
    p.lado,
    CASE p.lado WHEN 100 THEN 'Azul' ELSE 'Rojo' END AS equipo,
    p.id AS participante_id,
    c.nombre AS campeon,
    r.nombre AS rol
FROM participantes p
JOIN campeones c ON c.id = p.campeon_id
JOIN roles r ON r.codigo = p.rol
WHERE p.partida_id = 'EUW1_7677509245'
ORDER BY p.lado, r.orden;

-- 3. Winrate por campeón (JOIN + GROUP BY).
-- Se considera victoria cuando el lado del participante coincide
-- con el lado ganador de la partida.
SELECT
    c.id AS campeon_id,
    c.nombre AS campeon,
    COUNT(*) AS partidas_jugadas,
    COUNT(*) FILTER (WHERE p.lado = pa.lado_ganador) AS victorias,
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE p.lado = pa.lado_ganador)
        / NULLIF(COUNT(*), 0),
        2
    ) AS winrate_porcentaje
FROM participantes p
JOIN partidas pa ON pa.id = p.partida_id
JOIN campeones c ON c.id = p.campeon_id
GROUP BY c.id, c.nombre
ORDER BY winrate_porcentaje DESC, partidas_jugadas DESC, c.nombre;

-- 4. Winrate por rol (JOIN + GROUP BY).
SELECT
    r.codigo AS rol,
    r.nombre AS nombre_rol,
    COUNT(*) AS partidas_jugadas,
    COUNT(*) FILTER (WHERE p.lado = pa.lado_ganador) AS victorias,
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE p.lado = pa.lado_ganador)
        / NULLIF(COUNT(*), 0),
        2
    ) AS winrate_porcentaje
FROM participantes p
JOIN partidas pa ON pa.id = p.partida_id
JOIN roles r ON r.codigo = p.rol
GROUP BY r.codigo, r.nombre, r.orden
ORDER BY r.orden;

-- 5. Cantidad de partidas y participantes por rango promedio.
SELECT
    pa.rango_promedio,
    COUNT(DISTINCT pa.id) AS partidas,
    COUNT(p.id) AS participantes
FROM partidas pa
LEFT JOIN participantes p ON p.partida_id = pa.id
GROUP BY pa.rango_promedio
ORDER BY partidas DESC, pa.rango_promedio;
