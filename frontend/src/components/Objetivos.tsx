import Box from '@mui/material/Box'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { COLOR_AZUL, COLOR_ROJO } from '../theme'
import type { ObjetivosEquipo } from '../types'

const AZUL = 100
const ROJO = 200

/** Nombres de los dragones elementales como en el cliente de LoL en español. */
const DRAGONES: { clave: keyof ObjetivosEquipo; nombre: string }[] = [
  { clave: 'dragonesFuego', nombre: 'infernal' },
  { clave: 'dragonesAgua', nombre: 'del océano' },
  { clave: 'dragonesTierra', nombre: 'de montaña' },
  { clave: 'dragonesAire', nombre: 'de nube' },
  { clave: 'dragonesQuimtec', nombre: 'quimtec' },
  { clave: 'dragonesHextech', nombre: 'hextech' },
]

function dragonesElementales(o?: ObjetivosEquipo) {
  if (!o) return { total: 0, detalle: '' }
  const partes = DRAGONES.filter((d) => o[d.clave] > 0).map((d) => `${o[d.clave]} ${d.nombre}`)
  return { total: DRAGONES.reduce((s, d) => s + o[d.clave], 0), detalle: partes.join(', ') }
}

const FILAS: { etiqueta: string; valor: (o?: ObjetivosEquipo) => string | number }[] = [
  { etiqueta: 'Torres', valor: (o) => o?.torres ?? 0 },
  { etiqueta: 'Inhibidores', valor: (o) => o?.inhibidores ?? 0 },
  {
    etiqueta: 'Dragones elementales',
    valor: (o) => {
      const { total, detalle } = dragonesElementales(o)
      return detalle ? `${total} (${detalle})` : total
    },
  },
  { etiqueta: 'Dragón ancestral', valor: (o) => o?.dragonesElder ?? 0 },
  { etiqueta: 'Barones', valor: (o) => o?.barones ?? 0 },
  { etiqueta: 'Heraldos', valor: (o) => o?.heraldos ?? 0 },
  { etiqueta: 'Larvas del vacío', valor: (o) => o?.larvasVacio ?? 0 },
]

/** Último estado de los objetivos de cada lado (el snapshot de equipo con mayor minuto). */
function finalDe(objetivos: ObjetivosEquipo[], lado: number) {
  return objetivos.filter((o) => o.lado === lado).reduce<ObjetivosEquipo | undefined>(
    (ultimo, o) => (!ultimo || o.minuto > ultimo.minuto ? o : ultimo),
    undefined,
  )
}

export function Objetivos({ objetivos }: { objetivos: ObjetivosEquipo[] }) {
  const azul = finalDe(objetivos, AZUL)
  const rojo = finalDe(objetivos, ROJO)
  const minuto = Math.max(azul?.minuto ?? 0, rojo?.minuto ?? 0)

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Typography variant="h3" sx={{ mb: 0.5 }}>
        Objetivos
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Acumulados al minuto {minuto}
      </Typography>
      <Table size="small" aria-label="Objetivos de cada equipo">
        <TableHead>
          <TableRow>
            <TableCell>Objetivo</TableCell>
            <TableCell sx={{ color: COLOR_AZUL }}>Equipo azul</TableCell>
            <TableCell sx={{ color: COLOR_ROJO }}>Equipo rojo</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {FILAS.map((f) => (
            <TableRow key={f.etiqueta}>
              <TableCell>{f.etiqueta}</TableCell>
              <TableCell>{f.valor(azul)}</TableCell>
              <TableCell>{f.valor(rojo)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  )
}
