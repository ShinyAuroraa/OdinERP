import { describe, it, expect } from 'vitest'
import { GET } from './route'

describe('GET /api/metrics', () => {
  it('retorna métricas em formato Prometheus text', async () => {
    const response = await GET()
    const text = await response.text()
    expect(text).toContain('process_uptime_seconds')
    expect(text).toContain('process_heap_bytes')
    expect(text).toContain('process_rss_bytes')
  })

  it('retorna Content-Type text/plain (Prometheus format)', async () => {
    const response = await GET()
    expect(response.headers.get('Content-Type')).toContain('text/plain')
  })
})
