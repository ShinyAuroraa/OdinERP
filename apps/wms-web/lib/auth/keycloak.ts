import Keycloak from 'keycloak-js'

const keycloakConfig = {
  url: process.env['NEXT_PUBLIC_KEYCLOAK_URL'] ?? 'http://localhost:8090',
  realm: process.env['NEXT_PUBLIC_KEYCLOAK_REALM'] ?? 'odin',
  clientId: process.env['NEXT_PUBLIC_KEYCLOAK_CLIENT_ID'] ?? 'wms-web',
}

let keycloakInstance: Keycloak | null = null

export function getKeycloak(): Keycloak {
  if (!keycloakInstance) {
    keycloakInstance = new Keycloak(keycloakConfig)
  }
  return keycloakInstance
}

export function parseJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) {
    return {}
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = globalThis.atob(base64)
    return JSON.parse(json) as Record<string, unknown>
  } catch {
    return {}
  }
}
