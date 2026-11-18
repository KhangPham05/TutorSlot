import type { NextConfig } from "next";

// The browser only ever talks to this Next.js app. Every /api/... call is passed through to the
// Spring Boot backend, so the pages and the API share one address and need no CORS setup.
// BACKEND_URL is read when the app is built: localhost for local dev, http://app:8080 in Docker.
const backend = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // A small self-contained server for the Docker image.
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backend}/api/:path*` }];
  },
};

export default nextConfig;
