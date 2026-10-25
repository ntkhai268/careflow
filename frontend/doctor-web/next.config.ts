import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  allowedDevOrigins: ["127.0.0.1", "localhost"],
  async rewrites() {
    const gateway=process.env.CAREFLOW_GATEWAY_URL;
    return {fallback: gateway ? [{source:"/api/:path*",destination:`${gateway}/api/:path*`}] : []};
  },
};

export default nextConfig;
