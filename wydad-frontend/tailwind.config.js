/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      colors: {
        // Core Wydad Palette
        'wydad-red': '#DC143C',
        'wydad-red-light': '#FF1744',
        'wydad-red-dark': '#9B0000',
        'wydad-gold': '#D4AF37',
        'wydad-gold-light': '#F5D060',
        'wydad-gold-dark': '#A68B2A',

        // Dark Mode Surfaces
        'surface-0': '#050505',    // Deepest black (body)
        'surface-1': '#0A0A0A',    // Primary background
        'surface-2': '#111111',    // Elevated panels
        'surface-3': '#161616',    // Cards
        'surface-4': '#1C1C1C',    // Interactive surfaces
        'surface-5': '#222222',    // Hover states

        // Text Hierarchy
        'text-primary': '#FAFAFA',
        'text-secondary': '#A0A0A0',
        'text-tertiary': '#666666',
        'text-muted': '#444444',

        // ─── Thème clair « rouge & blanc » (pages publiques) ───
        // paper = surfaces blanches, ink = textes noirs.
        // L'ADMIN reste sur surface-*/text-* sombres : les deux
        // familles coexistent sans régression back-office.
        'paper-0': '#FFFFFF',      // fond principal blanc pur
        'paper-1': '#F7F7F8',      // section alternée gris très pâle
        'paper-2': '#EFEFF1',      // panneaux élevés
        'paper-3': '#E5E5E8',      // bordures/hover clairs
        'ink-primary': '#141414',  // texte principal noir
        'ink-secondary': '#4A4A4F',// texte secondaire
        'ink-tertiary': '#88888E', // texte discret

        // Semantic Colors
        'success': '#00C853',
        'warning': '#FFB300',
        'danger': '#FF1744',
        'info': '#00B0FF',

        // Tier Colors (Membership)
        'tier-rouge': '#DC143C',
        'tier-or': '#D4AF37',
        'tier-diamant': '#B9F2FF',
        'tier-legende': '#9C27B0',
        'tier-junior': '#4CAF50',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        display: ['Oswald', 'Outfit', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
      fontSize: {
        'hero': ['clamp(3rem, 8vw, 8rem)', { lineHeight: '0.9', letterSpacing: '-0.04em', fontWeight: '900' }],
        'headline': ['clamp(2rem, 5vw, 4rem)', { lineHeight: '1', letterSpacing: '-0.03em', fontWeight: '800' }],
        'subhead': ['clamp(1.25rem, 3vw, 2rem)', { lineHeight: '1.2', letterSpacing: '-0.02em', fontWeight: '700' }],
      },
      backgroundImage: {
        'gradient-radial': 'radial-gradient(var(--tw-gradient-stops))',
        'gradient-conic': 'conic-gradient(from 180deg at 50% 50%, var(--tw-gradient-stops))',
        'shimmer': 'linear-gradient(110deg, transparent 33%, rgba(255,255,255,0.05) 50%, transparent 67%)',
        'red-glow': 'radial-gradient(ellipse at center, rgba(220,20,60,0.15) 0%, transparent 70%)',
        'gold-glow': 'radial-gradient(ellipse at center, rgba(212,175,55,0.1) 0%, transparent 70%)',
        // Thème clair : dégradés signature « blanc → rouge »
        'hero-light': 'linear-gradient(180deg, #FFFFFF 0%, #FFF5F6 45%, rgba(220,20,60,0.10) 100%)',
        'red-band': 'linear-gradient(135deg, #DC143C 0%, #9B0000 100%)',
      },
      boxShadow: {
        'glow-red': '0 0 30px rgba(220, 20, 60, 0.3)',
        'glow-red-lg': '0 0 60px rgba(220, 20, 60, 0.4)',
        'glow-gold': '0 0 30px rgba(212, 175, 55, 0.3)',
        'card': '0 4px 30px rgba(0, 0, 0, 0.5)',
        'card-hover': '0 8px 40px rgba(0, 0, 0, 0.7)',
        'glass': '0 8px 32px rgba(0, 0, 0, 0.4)',
        'inner-glow': 'inset 0 1px 0 rgba(255,255,255,0.05)',
        // Thème clair : ombres douces à teinte rouge
        'paper-card': '0 2px 12px rgba(20, 20, 20, 0.06), 0 1px 3px rgba(20, 20, 20, 0.04)',
        'paper-card-hover': '0 12px 40px rgba(220, 20, 60, 0.14), 0 2px 8px rgba(20, 20, 20, 0.08)',
      },
      borderRadius: {
        '2xl': '1rem',
        '3xl': '1.5rem',
        '4xl': '2rem',
      },
      backdropBlur: {
        'xs': '2px',
      },
      animation: {
        'fade-in': 'fadeIn 0.6s ease-out forwards',
        'fade-in-up': 'fadeInUp 0.8s ease-out forwards',
        'fade-in-down': 'fadeInDown 0.6s ease-out forwards',
        'slide-in-left': 'slideInLeft 0.6s ease-out forwards',
        'slide-in-right': 'slideInRight 0.6s ease-out forwards',
        'scale-in': 'scaleIn 0.5s ease-out forwards',
        'shimmer': 'shimmer 2.5s ease-in-out infinite',
        'pulse-glow': 'pulseGlow 3s ease-in-out infinite',
        'float': 'float 6s ease-in-out infinite',
        'spin-slow': 'spin 20s linear infinite',
        'card-hover': 'cardHover 0.3s ease-out forwards',
        'counter': 'counter 2s ease-out forwards',
        'slow-zoom': 'slowZoom 20s ease-in-out infinite alternate',
      },
      keyframes: {
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        fadeInUp: {
          '0%': { opacity: '0', transform: 'translateY(30px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        fadeInDown: {
          '0%': { opacity: '0', transform: 'translateY(-20px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        slideInLeft: {
          '0%': { opacity: '0', transform: 'translateX(-40px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        slideInRight: {
          '0%': { opacity: '0', transform: 'translateX(40px)' },
          '100%': { opacity: '1', transform: 'translateX(0)' },
        },
        scaleIn: {
          '0%': { opacity: '0', transform: 'scale(0.9)' },
          '100%': { opacity: '1', transform: 'scale(1)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        pulseGlow: {
          '0%, 100%': { boxShadow: '0 0 20px rgba(220, 20, 60, 0.2)' },
          '50%': { boxShadow: '0 0 40px rgba(220, 20, 60, 0.5)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-10px)' },
        },
        cardHover: {
          '0%': { transform: 'translateY(0) scale(1)' },
          '100%': { transform: 'translateY(-4px) scale(1.01)' },
        },
        slowZoom: {
          '0%': { transform: 'scale(1)' },
          '100%': { transform: 'scale(1.1)' },
        },
      },
      transitionTimingFunction: {
        'bounce-in': 'cubic-bezier(0.68, -0.55, 0.265, 1.55)',
        'smooth': 'cubic-bezier(0.4, 0, 0.2, 1)',
      },
    },
  },
  plugins: [],
}
