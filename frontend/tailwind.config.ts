import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: ["class"],
  content: [
    "./src/app/**/*.{ts,tsx}",
    "./src/components/**/*.{ts,tsx}",
    "./src/features/**/*.{ts,tsx}",
    "./src/lib/**/*.{ts,tsx}",
    "./src/hooks/**/*.{ts,tsx}",
    "./src/services/**/*.{ts,tsx}",
    "./src/types/**/*.{ts,tsx}"
  ],
  theme: {
    extend: {
      colors: {
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        card: "hsl(var(--card))",
        primary: "hsl(var(--primary))",
        secondary: "hsl(var(--secondary))",
        muted: "hsl(var(--muted))",
        accent: "hsl(var(--accent))",
        border: "hsl(var(--border))"
      },
      boxShadow: {
        glow: "0 0 30px rgba(56, 189, 248, 0.22)"
      }
    }
  },
  plugins: []
};

export default config;
