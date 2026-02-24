import { NextResponse } from 'next/server'

export async function GET() {
  return NextResponse.json(
    {
      status: 'ok',
      service: 'wms-web',
      timestamp: new Date().toISOString(),
      uptime: Math.floor(process.uptime()),
    },
    { status: 200 }
  )
}
