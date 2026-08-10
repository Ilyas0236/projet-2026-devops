/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        'wydad-red': '#D32F2F', // Primary Red
        'wydad-dark': '#121212', // Background Dark
        'wydad-light': '#F5F5F5', // Background Light
        'wydad-gold': '#FFC107', // Accent Gold
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        display: ['Outfit', 'sans-serif'],
      }
    },
  },
  plugins: [],
}
