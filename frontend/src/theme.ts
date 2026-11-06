import { createTheme } from '@mui/material/styles'

export const ORO = '#C8AA6E'
export const ORO_OSCURO = '#785A28'
export const COLOR_AZUL = '#5383E8'
export const COLOR_ROJO = '#E84057'
export const HEXTECH = '#0AC8B9'

const FUENTE_TITULOS = '"Cinzel", Georgia, serif'

export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: ORO, contrastText: '#010A13' },
    secondary: { main: HEXTECH },
    error: { main: COLOR_ROJO },
    info: { main: COLOR_AZUL },
    background: { default: '#010A13', paper: '#0A1428' },
    text: { primary: '#F0E6D2', secondary: '#A09B8C' },
    divider: '#1E2328',
  },
  shape: { borderRadius: 4 },
  typography: {
    fontFamily: '"Barlow", system-ui, sans-serif',
    h1: { fontFamily: FUENTE_TITULOS, fontWeight: 700, fontSize: '2rem' },
    h2: { fontFamily: FUENTE_TITULOS, fontWeight: 700, fontSize: '1.4rem' },
    h3: { fontFamily: FUENTE_TITULOS, fontWeight: 600, fontSize: '1.15rem' },
    h4: { fontFamily: FUENTE_TITULOS, fontWeight: 600, fontSize: '1.05rem' },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundImage: 'radial-gradient(ellipse at top, #0A1E3A 0%, #010A13 55%)',
          backgroundAttachment: 'fixed',
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none', border: `1px solid ${ORO_OSCURO}` },
      },
    },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { minHeight: 40 },
        outlined: { borderColor: ORO_OSCURO, '&:hover': { borderColor: ORO } },
      },
    },
    MuiTableCell: {
      styleOverrides: {
        root: { borderColor: '#1E2328' },
        head: { color: ORO, fontWeight: 600 },
      },
    },
    MuiDialog: {
      styleOverrides: {
        paper: { boxShadow: '0 0 40px rgba(200,170,110,0.18)' },
      },
    },
  },
})
