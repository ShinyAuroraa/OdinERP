import { type NextRequest, NextResponse } from 'next/server'

const TOKEN_COOKIE = 'wms-auth-token'
const TENANT_COOKIE = 'x-tenant-id'

const PUBLIC_PATHS = ['/login', '/unauthorized', '/api/auth', '/silent-check-sso.html', '/_next', '/favicon.ico']

const ROLE_PROTECTED_PATHS: Record<string, string[]> = {
  '/admin': ['WMS_ADMIN'],
}

function isPublicPath(pathname: string): boolean {
  return PUBLIC_PATHS.some((p) => pathname.startsWith(p))
}

function parseJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return {}
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
    const binary = atob(padded)
    return JSON.parse(binary) as Record<string, unknown>
  } catch {
    return {}
  }
}

function isTokenExpired(payload: Record<string, unknown>): boolean {
  const exp = payload['exp']
  if (typeof exp !== 'number') return true
  return Date.now() / 1000 > exp
}

export function middleware(request: NextRequest): NextResponse {
  const { pathname } = request.nextUrl

  if (isPublicPath(pathname)) {
    return NextResponse.next()
  }

  const token = request.cookies.get(TOKEN_COOKIE)?.value

  if (!token) {
    const loginUrl = new URL('/login', request.url)
    loginUrl.searchParams.set('callbackUrl', pathname)
    return NextResponse.redirect(loginUrl)
  }

  const payload = parseJwtPayload(token)

  if (isTokenExpired(payload)) {
    const loginUrl = new URL('/login', request.url)
    loginUrl.searchParams.set('callbackUrl', pathname)
    const response = NextResponse.redirect(loginUrl)
    response.cookies.delete(TOKEN_COOKIE)
    response.cookies.delete(TENANT_COOKIE)
    return response
  }

  const realmRoles = (payload['realm_access'] as { roles?: string[] } | undefined)?.roles ?? []

  for (const [protectedPath, requiredRoles] of Object.entries(ROLE_PROTECTED_PATHS)) {
    if (pathname.startsWith(protectedPath)) {
      const hasRole = requiredRoles.some((r) => realmRoles.includes(r))
      if (!hasRole) {
        return NextResponse.redirect(new URL('/unauthorized', request.url))
      }
    }
  }

  const tenantId = payload['tenant_id'] as string | undefined

  const response = NextResponse.next()

  if (tenantId) {
    response.cookies.set(TENANT_COOKIE, tenantId, {
      path: '/',
      sameSite: 'strict',
      httpOnly: false,
    })
    response.headers.set('X-Tenant-Id', tenantId)
  }

  return response
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|.*\\.png|.*\\.svg).*)'],
}
