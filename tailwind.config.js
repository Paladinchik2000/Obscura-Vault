/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        obscura: {
          bg: '#000000',
          card: '#121212',
          border: '#242424',
          crimson: '#E50914',
          crimsonHover: '#B80710',
          textMuted: '#737373',
          textSecondary: '#A3A3A3',
          securityGreen: '#10B981',
          securityYellow: '#F59E0B',
          securityRed: '#EF4444',
        }
      },
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'monospace'],
      }
    },
  },
  plugins: [],
}
