const BASE_URL = process.env['NEXT_PUBLIC_API_URL'] ?? '/api/proxy'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface ApiClientOptions extends Omit<RequestInit, 'headers'> {
  token?: string | null
  tenantId?: string | null
  headers?: Record<string, string>
}

export async function apiClient<T>(path: string, options: ApiClientOptions = {}): Promise<T> {
  const { token, tenantId, headers = {}, ...rest } = options

  const requestHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...headers,
  }

  if (token) {
    requestHeaders['Authorization'] = `Bearer ${token}`
  }

  if (tenantId) {
    requestHeaders['X-Tenant-Id'] = tenantId
  }

  const url = path.startsWith('http') ? path : `${BASE_URL}${path}`
  const response = await fetch(url, { ...rest, headers: requestHeaders })

  if (!response.ok) {
    if (response.status === 401) {
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('wms:unauthorized'))
      }
    }
    if (response.status === 403) {
      if (typeof window !== 'undefined') {
        window.location.href = '/unauthorized'
      }
    }
    throw new ApiError(response.status, `HTTP ${response.status}: ${response.statusText}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const warehouseKeys = {
  all: ['warehouses'] as const,
  byTenant: (tenantId: string) => [...warehouseKeys.all, tenantId] as const,
}

export const productKeys = {
  all: ['products'] as const,
  byTenant: (tenantId: string) => [...productKeys.all, tenantId] as const,
}

export const stockKeys = {
  all: ['stock'] as const,
  byTenant: (tenantId: string) => [...stockKeys.all, tenantId] as const,
}
