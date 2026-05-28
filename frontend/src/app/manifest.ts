import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "InvoiceMind AI",
    short_name: "InvoiceMind",
    description: "AI-powered OCR automation platform for enterprise finance teams",
    start_url: "/",
    display: "standalone",
    background_color: "#0b1020",
    theme_color: "#38bdf8",
    icons: [
      {
        src: "/icon.svg",
        sizes: "192x192",
        type: "image/svg+xml"
      }
    ]
  };
}
