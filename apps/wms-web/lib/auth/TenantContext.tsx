'use client'

import { createContext, useContext } from 'react'

interface TenantContextValue {
  tenantId: string
}

const TenantContext = createContext<TenantContextValue | null>(null)

interface TenantProviderProps {
  tenantId: string
  children: React.ReactNode
}

export function TenantProvider({ tenantId, children }: TenantProviderProps) {
  return <TenantContext.Provider value={{ tenantId }}>{children}</TenantContext.Provider>
}

export function useTenantContext(): TenantContextValue {
  const ctx = useContext(TenantContext)
  if (!ctx) {
    throw new Error('useTenantContext must be used inside <TenantProvider>')
  }
  return ctx
}
