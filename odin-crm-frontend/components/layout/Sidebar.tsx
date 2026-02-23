'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';

const navItems = [
  { href: '/dashboard', label: 'Dashboard' },
  { href: '/contas', label: 'Contas' },
  { href: '/oportunidades', label: 'Oportunidades' },
  { href: '/pedidos', label: 'Pedidos' },
  { href: '/atividades', label: 'Atividades' },
];

interface SidebarProps {
  collapsed: boolean;
}

export function Sidebar({ collapsed }: SidebarProps) {
  const pathname = usePathname();

  return (
    <aside
      className={cn(
        'flex flex-col h-full bg-sidebar border-r border-sidebar-border transition-all duration-200',
        collapsed ? 'w-16' : 'w-64'
      )}
    >
      <div className="flex items-center h-16 px-4 border-b border-sidebar-border">
        {!collapsed && (
          <span className="text-lg font-semibold text-sidebar-foreground">ODIN CRM</span>
        )}
      </div>

      <nav className="flex-1 py-4 space-y-1 px-2">
        {navItems.map((item) => (
          <Link
            key={item.href}
            href={item.href}
            className={cn(
              'flex items-center px-3 py-2 rounded-md text-sm font-medium transition-colors',
              'text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground',
              pathname === item.href && 'bg-sidebar-accent text-sidebar-accent-foreground',
              collapsed && 'justify-center'
            )}
          >
            {!collapsed && item.label}
          </Link>
        ))}
      </nav>
    </aside>
  );
}
