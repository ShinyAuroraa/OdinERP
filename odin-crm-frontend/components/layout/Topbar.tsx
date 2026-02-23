'use client';

import { useCurrentUser } from '@/lib/hooks/use-current-user';
import { LogoutButton } from './LogoutButton';

interface TopbarProps {
  breadcrumb?: string;
}

export function Topbar({ breadcrumb }: TopbarProps) {
  const { name, isLoading } = useCurrentUser();

  return (
    <header className="flex items-center justify-between h-16 px-6 border-b bg-background">
      <div className="text-sm text-muted-foreground">
        {breadcrumb ?? 'Dashboard'}
      </div>

      <div className="flex items-center gap-3">
        <span className="text-sm text-foreground">
          {isLoading ? '...' : (name ?? 'Usuário')}
        </span>
        <LogoutButton />
      </div>
    </header>
  );
}
