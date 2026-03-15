/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        base:    '#0a0a0a',
        card:    '#171717',
        surface: '#262626',
        muted:   '#a3a3a3',
        'rank-gold':   '#FBBF24',
        'rank-silver': '#D2D5D9',
        'rank-bronze': '#FAA95F',
        accent: {
          DEFAULT: '#3b82f6',
          hover:   '#2563eb',
        },
        up: {
          DEFAULT: '#10b981',
          glow:    'rgba(16,185,129,0.25)',
        },
        down: {
          DEFAULT: '#ef4444',
          hover:   '#c03d3d',
          glow:    'rgba(239,68,68,0.25)',
        },
        line: '#333333',
      },
      fontFamily: {
        sans: [
          '-apple-system', 'BlinkMacSystemFont', '"Segoe UI"',
          'Roboto', 'Helvetica', 'Arial', 'sans-serif',
        ],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
    },
  },
  plugins: [],
}
