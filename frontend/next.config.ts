import type { NextConfig } from "next";

// Where the Spring backend lives from the Next.js server's point of view. In Docker this is
// http://backend:8080 (set at build time, see Dockerfile); for local `next dev` it is the dev overlay port.
const backend = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:18080";

const config: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  reactStrictMode: true,
  // Same-origin API: the browser talks to Next (or Nginx in production), so the httpOnly
  // refresh cookie and CORS-free requests work identically in dev and production.
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default config;
