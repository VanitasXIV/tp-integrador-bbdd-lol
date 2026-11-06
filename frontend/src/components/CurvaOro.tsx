import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { COLOR_AZUL, COLOR_ROJO, ORO } from '../theme'
import type { PuntoOro } from '../types'

export function CurvaOro({ datos }: { datos: PuntoOro[] }) {
  return (
    <div style={{ width: '100%', height: 240 }}>
      <ResponsiveContainer>
        <LineChart data={datos} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="#1E2328" strokeDasharray="3 3" />
          <XAxis dataKey="minuto" stroke="#A09B8C" unit=" min" />
          <YAxis stroke="#A09B8C" />
          <Tooltip
            contentStyle={{ background: '#0A1428', border: `1px solid ${ORO}` }}
            labelFormatter={(m) => `Minuto ${m}`}
            formatter={(v) => {
              const n = Number(v)
              return [n >= 0 ? `+${n} azul` : `${n} rojo`, 'Diferencia'] as [string, string]
            }}
          />
          <ReferenceLine y={0} stroke="#785A28" />
          <Line type="monotone" dataKey="diferencia" stroke={ORO} strokeWidth={3} dot={false} />
        </LineChart>
      </ResponsiveContainer>
      <div style={{ display: 'flex', gap: 16, fontSize: 13, justifyContent: 'center' }}>
        <span style={{ color: COLOR_AZUL }}>Ventaja equipo azul (arriba)</span>
        <span style={{ color: COLOR_ROJO }}>Ventaja equipo rojo (abajo)</span>
      </div>
    </div>
  )
}
