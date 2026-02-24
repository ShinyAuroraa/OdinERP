'use client'

import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import type Keycloak from 'keycloak-js'
import { getKeycloak, parseJwtPayload } from './keycloak'
import type { WmsRole, WmsUser } from '@/types/auth'

interface AuthContextValue {
  user: WmsUser | null
  token: string | null
  roles: WmsRole[]
  tenantId: string | null
  isAuthenticated: boolean
  isLoading: boolean
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

const TOKEN_COOKIE = 'wms-auth-token'
const TENANT_COOKIE = 'x-tenant-id'
const REFRESH_INTERVAL_MS = 25_000

function setAuthCookies(token: string, tenantId: string) {
  document.cookie = `${TOKEN_COOKIE}=${token}; path=/; SameSite=Strict`
  document.cookie = `${TENANT_COOKIE}=${tenantId}; path=/; SameSite=Strict`
}

function clearAuthCookies() {
  document.cookie = `${TOKEN_COOKIE}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT`
  document.cookie = `${TENANT_COOKIE}=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT`
}

function extractUser(token: string): { user: WmsUser; tenantId: string } | null {
  const payload = parseJwtPayload(token)
  const tenantId = payload['tenant_id'] as string | undefined
  const sub = payload['sub'] as string | undefined
  const username = payload['preferred_username'] as string | undefined
  const email = payload['email'] as string | undefined
  const realmRoles = (payload['realm_access'] as { roles?: string[] } | undefined)?.roles ?? []

  if (!tenantId || !sub) return null

  const roles = realmRoles.filter((r): r is WmsRole =>
    ['WMS_ADMIN', 'WMS_SUPERVISOR', 'WMS_OPERATOR', 'WMS_VIEWER'].includes(r)
  )

  return {
    user: {
      id: sub,
      username: username ?? '',
      email: email ?? '',
      tenantId,
      roles,
    },
    tenantId,
  }
}

interface KeycloakProviderProps {
  children: React.ReactNode
}

export function KeycloakProvider({ children }: KeycloakProviderProps) {
  const [user, setUser] = useState<WmsUser | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [tenantId, setTenantId] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  const handleToken = useCallback((kc: Keycloak) => {
    const t = kc.token
    if (!t) return

    const extracted = extractUser(t)
    if (!extracted) return

    setToken(t)
    setUser(extracted.user)
    setTenantId(extracted.tenantId)
    setAuthCookies(t, extracted.tenantId)
  }, [])

  const logout = useCallback(() => {
    const kc = getKeycloak()
    clearAuthCookies()
    setToken(null)
    setUser(null)
    setTenantId(null)
    void kc.logout({ redirectUri: `${window.location.origin}/login` })
  }, [])

  useEffect(() => {
    const kc = getKeycloak()

    void kc
      .init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
        pkceMethod: 'S256',
        checkLoginIframe: false,
      })
      .then((authenticated) => {
        if (authenticated) {
          handleToken(kc)
        }
        setIsLoading(false)
      })
      .catch(() => {
        setIsLoading(false)
      })

    kc.onTokenExpired = () => {
      void kc.updateToken(30).then((refreshed) => {
        if (refreshed) handleToken(kc)
      })
    }

    const interval = setInterval(() => {
      void kc.updateToken(30).then((refreshed) => {
        if (refreshed) handleToken(kc)
      })
    }, REFRESH_INTERVAL_MS)

    return () => {
      clearInterval(interval)
    }
  }, [handleToken])

  const roles = user?.roles ?? []
  const isAuthenticated = !!token && !!user

  return (
    <AuthContext.Provider value={{ user, token, roles, tenantId, isAuthenticated, isLoading, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuthContext(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuthContext must be used inside <KeycloakProvider>')
  }
  return ctx
}
