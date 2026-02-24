export type WmsRole =
  | 'WMS_ADMIN'
  | 'WMS_SUPERVISOR'
  | 'WMS_OPERATOR'
  | 'WMS_VIEWER'

export interface WmsUser {
  id: string
  username: string
  email: string
  tenantId: string
  roles: WmsRole[]
}

export interface KeycloakTokenPayload {
  sub: string
  preferred_username: string
  email: string
  tenant_id: string
  realm_access: {
    roles: string[]
  }
  exp: number
  iat: number
}
