import type { NextConfig } from "next";

// Where the Spring backend lives from the Next.js server's point of view. In Docker this is http://backend:8080
// (set at build time, see the Dockerfile); for a local `next dev` it is the backend published on the host.
const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080";

const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
  // The frontend server is the only public entry point, so it also does what a reverse proxy would: the API and the
  // WebSocket go to the backend through it, which keeps the browser on one origin (httpOnly refresh cookie, no CORS).
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backend}/api/:path*` },
      { source: "/ws/:path*", destination: `${backend}/ws/:path*` },
      { source: "/swagger-ui/:path*", destination: `${backend}/swagger-ui/:path*` },
      { source: "/swagger-ui.html", destination: `${backend}/swagger-ui.html` },
      { source: "/v3/api-docs/:path*", destination: `${backend}/v3/api-docs/:path*` },
    ];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
        ],
      },
    ];
  },
  // An answer that waits for the language model ("Why was this flagged?") can take longer than the 30 s default.
  // Uploads go through the same proxy: Next buffers at most this much of a request body (default 10 MB), the backend accepts
  // files up to 15 MB plus multipart overhead and answers anything bigger with a clean 413.
  experimental: { proxyTimeout: 120_000, middlewareClientMaxBodySize: "16mb" },
};

export default config;
