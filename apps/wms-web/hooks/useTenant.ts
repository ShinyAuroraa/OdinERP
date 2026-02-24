import { useAuthContext } from '@/lib/auth/AuthContext'

export function useTenant(): string | null {
  const { tenantId } = useAuthContext()
  return tenantId
}
