# Frontend de partidas (React + TypeScript + MUI)

    npm install
    cp .env.example .env   # VITE_API_URL apunta al backend
    npm run dev

- Una sola pantalla: el listado, con el detalle desplegable debajo de cada partida.
- Las métricas del jugador se abren en un popup. La URL refleja el estado:
  `/partidas/1042` (detalle abierto) y `/partidas/1042/jugadores/2` (popup abierto).
- Las rutas del backend están en `src/api/partidas.ts`; ajustalas si tus endpoints son otros.
- El backend debe habilitar CORS para http://localhost:5173.
- Colores y tipografías del tema (estética tipo LoL) en `src/theme.ts`.
