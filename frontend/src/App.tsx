import Container from '@mui/material/Container'
import { Route, Routes } from 'react-router-dom'
import { PartidasPage } from './pages/PartidasPage'

/**
 * Una sola pantalla. La URL guarda qué partida está desplegada y qué jugador
 * está abierto en el popup, así se puede recargar o compartir el link.
 */
export default function App() {
  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Routes>
        <Route path="/" element={<PartidasPage />} />
        <Route path="/partidas/:partidaId" element={<PartidasPage />} />
        <Route path="/partidas/:partidaId/jugadores/:jugadorId" element={<PartidasPage />} />
        <Route path="*" element={<PartidasPage />} />
      </Routes>
    </Container>
  )
}
