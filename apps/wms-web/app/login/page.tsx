'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Warehouse, LogIn } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'
import { getKeycloak } from '@/lib/auth/keycloak'

export default function LoginPage() {
  const { isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!isLoading && isAuthenticated) {
      router.replace('/dashboard')
    }
  }, [isAuthenticated, isLoading, router])

  function handleLogin() {
    const kc = getKeycloak()
    void kc.login({ redirectUri: `${window.location.origin}/dashboard` })
  }

  if (isLoading || isAuthenticated) return null

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="flex flex-col items-center gap-8 p-8 rounded-xl border bg-card shadow-sm w-full max-w-sm">
        <div className="flex flex-col items-center gap-3">
          <div className="flex h-14 w-14 items-center justify-center rounded-full bg-primary">
            <Warehouse className="h-7 w-7 text-primary-foreground" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight">Odin WMS</h1>
          <p className="text-sm text-muted-foreground text-center">
            Sistema de Gerenciamento de Armazém
          </p>
        </div>

        <Button onClick={handleLogin} className="w-full gap-2" size="lg">
          <LogIn className="h-4 w-4" />
          Entrar com Keycloak
        </Button>

        <p className="text-xs text-muted-foreground text-center">
          Acesso via SSO corporativo. Autenticação segura com PKCE.
        </p>
      </div>
    </div>
  )
}
