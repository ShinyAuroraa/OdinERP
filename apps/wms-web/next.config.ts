import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/proxy/:path*',
        destination: `${process.env.WMS_API_URL ?? 'http://localhost:8080'}/api/v1/:path*`,
      },
    ]
  },
}

export default nextConfig
