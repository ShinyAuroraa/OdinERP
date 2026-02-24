import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { apiClient, ApiError } from './client'

describe('apiClient', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('inclui header Authorization quando token é fornecido', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ data: 'ok' }),
    })
    vi.stubGlobal('fetch', mockFetch)

    await apiClient('/warehouses', { token: 'my-jwt-token', tenantId: 'tenant-1' })

    const [, opts] = mockFetch.mock.calls[0] as [string, RequestInit]
    const headers = opts.headers as Record<string, string>
    expect(headers['Authorization']).toBe('Bearer my-jwt-token')
  })

  it('inclui header X-Tenant-Id quando tenantId é fornecido', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    })
    vi.stubGlobal('fetch', mockFetch)

    await apiClient('/products', { token: 'tok', tenantId: 'tenant-abc' })

    const [, opts] = mockFetch.mock.calls[0] as [string, RequestInit]
    const headers = opts.headers as Record<string, string>
    expect(headers['X-Tenant-Id']).toBe('tenant-abc')
  })

  it('lança ApiError em resposta 404', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404, statusText: 'Not Found' }))

    await expect(apiClient('/not-found')).rejects.toBeInstanceOf(ApiError)
    await expect(apiClient('/not-found')).rejects.toMatchObject({ status: 404 })
  })

  it('retorna undefined para status 204', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 204 }))

    const result = await apiClient('/delete-something', { method: 'DELETE' })
    expect(result).toBeUndefined()
  })

  it('não inclui Authorization quando token é null', async () => {
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({}),
    })
    vi.stubGlobal('fetch', mockFetch)

    await apiClient('/public')

    const [, opts] = mockFetch.mock.calls[0] as [string, RequestInit]
    const headers = opts.headers as Record<string, string>
    expect(headers['Authorization']).toBeUndefined()
  })
})
