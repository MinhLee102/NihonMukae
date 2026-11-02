// Optional: when BACKEND_PROXY_URL is set (e.g. http://203.0.113.10), requests to /backend/* are
// forwarded to that server by Vercel. The browser then only talks to the frontend's own origin, which
// keeps the refresh-token cookie first-party. Use it with NEXT_PUBLIC_API_URL=/backend.
// Not needed when the backend has its own HTTPS domain (set NEXT_PUBLIC_API_URL to it instead).
const backendProxy = process.env.BACKEND_PROXY_URL?.replace(/\/$/, "");

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    if (!backendProxy) return [];
    return [{ source: "/backend/:path*", destination: `${backendProxy}/:path*` }];
  },
};

export default nextConfig;
