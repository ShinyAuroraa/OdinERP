'use client'

import { useState } from 'react'
import { Menu, LogOut } from 'lucide-react'
import { Breadcrumbs } from './Breadcrumbs'
import { MobileSidebar } from './Sidebar'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { useAuth } from '@/hooks/useAuth'

function getInitials(username: string): string {
  return username
    .split(/[\s._-]/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() ?? '')
    .join('')
}

export function Header() {
  const { user, tenantId, logout } = useAuth()
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <header className="flex h-16 items-center gap-4 border-b bg-background px-4 md:px-6 shrink-0">
      {/* Mobile hamburger */}
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden"
        onClick={() => setMobileOpen(true)}
        aria-label="Abrir menu"
      >
        <Menu className="h-5 w-5" />
      </Button>

      {/* Breadcrumbs */}
      <div className="flex-1 min-w-0">
        <Breadcrumbs />
      </div>

      {/* Right section */}
      <div className="flex items-center gap-3">
        {tenantId && (
          <span className="hidden sm:block text-xs text-muted-foreground border rounded px-2 py-0.5">
            {tenantId}
          </span>
        )}

        <div className="flex items-center gap-2">
          <Avatar className="h-8 w-8">
            <AvatarFallback className="text-xs bg-primary text-primary-foreground">
              {user ? getInitials(user.username) : '?'}
            </AvatarFallback>
          </Avatar>
          <span className="hidden sm:block text-sm font-medium">{user?.username ?? 'Usuário'}</span>
        </div>

        <Button variant="ghost" size="icon" onClick={logout} aria-label="Sair">
          <LogOut className="h-4 w-4" />
        </Button>
      </div>

      {/* Mobile drawer */}
      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="left" className="w-60 p-0">
          <SheetHeader className="sr-only">
            <SheetTitle>Menu de navegação</SheetTitle>
          </SheetHeader>
          <MobileSidebar onClose={() => setMobileOpen(false)} />
        </SheetContent>
      </Sheet>
    </header>
  )
}
