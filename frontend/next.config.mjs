// Proxy do usług backendowych — przeglądarka rozmawia tylko z frontendem (brak CORS).
// Uwaga: rewrites są wyliczane w czasie `next build`, dlatego w Dockerze adresy podawane są jako build args.
const backendCore = process.env.BACKEND_CORE_URL ?? "http://localhost:8080";
const aiGateway = process.env.AI_GATEWAY_URL ?? "http://localhost:8000";

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  reactStrictMode: true,
  async rewrites() {
    return [
      { source: "/api/ai/:path*", destination: `${aiGateway}/api/ai/:path*` },
      { source: "/api/:path*", destination: `${backendCore}/api/:path*` },
    ];
  },
};

export default nextConfig;
