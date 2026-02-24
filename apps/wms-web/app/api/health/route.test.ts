import { describe, it, expect } from 'vitest'
import { GET } from './route'

describe('GET /api/health', () => {
  it('retorna HTTP 200', async () => {
    const response = await GET()
    expect(response.status).toBe(200)
  })

  it('retorna body com campos obrigatórios', async () => {
    const response = await GET()
    const body = await response.json()
    expect(body.status).toBe('ok')
    expect(body.service).toBe('wms-web')
    expect(typeof body.timestamp).toBe('string')
    expect(typeof body.uptime).toBe('number')
  })
})
