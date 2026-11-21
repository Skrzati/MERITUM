// Proxy do backend-core — przeglądarka rozmawia tylko z frontendem (brak CORS). AI wyłącznie przez /api/core-ai/*
// (uwierzytelnienie + audyt AI Act art. 26); ai-gateway nie jest osiągalny z przeglądarki.
// Uwaga: rewrites są wyliczane w czasie `next build`, dlatego w Dockerze adres podawany jest jako build arg.
const backendCore = process.env.BACKEND_CORE_URL ?? "http://localhost:8080";

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  reactStrictMode: true,
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendCore}/api/:path*` },
    ];
  },
};

export default nextConfig;
