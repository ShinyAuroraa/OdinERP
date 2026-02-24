'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Sidebar } from '@/components/layout/Sidebar'
import { Header } from '@/components/layout/Header'
import { useAuth } from '@/hooks/useAuth'
import { Skeleton } from '@/components/ui/skeleton'

function LoadingShell() {
  return (
    <div className="flex h-screen">
      <aside className="hidden md:flex md:w-60 border-r bg-sidebar" />
      <div className="flex flex-1 flex-col">
        <div className="h-16 border-b flex items-center px-6 gap-4">
          <Skeleton className="h-4 w-32" />
          <div className="flex-1" />
          <Skeleton className="h-8 w-8 rounded-full" />
        </div>
        <main className="flex-1 p-6">
          <Skeleton className="h-8 w-64 mb-4" />
          <Skeleton className="h-4 w-full mb-2" />
          <Skeleton className="h-4 w-3/4" />
        </main>
      </div>
    </div>
  )
}

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/login')
    }
  }, [isAuthenticated, isLoading, router])

  useEffect(() => {
    const handleUnauthorized = () => {
      router.replace('/login')
    }
    window.addEventListener('wms:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('wms:unauthorized', handleUnauthorized)
  }, [router])

  if (isLoading) return <LoadingShell />
  if (!isAuthenticated) return null

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <Header />
        <main className="flex-1 overflow-y-auto p-4 md:p-6">{children}</main>
      </div>
    </div>
  )
}
