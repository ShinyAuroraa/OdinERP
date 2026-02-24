import type { WmsRole } from '@/types/auth'
import { useAuth } from './useAuth'

export function useHasRole(role: WmsRole | WmsRole[]): boolean {
  const { roles } = useAuth()
  if (Array.isArray(role)) {
    return role.some((r) => roles.includes(r))
  }
  return roles.includes(role)
}
