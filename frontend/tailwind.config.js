/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        cbs: {
          primary: '#0f3a68',
          accent: '#1e60a3',
          dark: '#0a2544',
          gold: '#c99a3e',
          surface: '#f5f7fa',
          border: '#e2e8f0',
        }
      }
    },
  },
  plugins: [],
}
