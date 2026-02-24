'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import {
  LayoutDashboard,
  Warehouse,
  Package,
  Truck,
  MapPin,
  AlertTriangle,
  BarChart3,
  GitBranch,
  ClipboardList,
  FileSearch,
  ArrowLeftRight,
  ShoppingCart,
  Box,
  Ship,
  Factory,
  FileText,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'

interface NavItem {
  label: string
  href: string
  icon: React.ComponentType<{ className?: string }>
  implemented: boolean
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard', href: '/dashboard', icon: LayoutDashboard, implemented: true },
  { label: 'Armazéns', href: '/warehouses', icon: Warehouse, implemented: true },
  { label: 'Produtos', href: '/products', icon: Package, implemented: true },
  { label: 'Recebimento', href: '/receiving', icon: Truck, implemented: true },
  { label: 'Putaway', href: '/putaway', icon: MapPin, implemented: true },
  { label: 'Quarentena', href: '/quarantine', icon: AlertTriangle, implemented: true },
  { label: 'Estoque', href: '/stock', icon: BarChart3, implemented: true },
  { label: 'Rastreabilidade', href: '/traceability', icon: GitBranch, implemented: true },
  { label: 'Inventário', href: '/inventory', icon: ClipboardList, implemented: true },
  { label: 'Auditoria', href: '/audit', icon: FileSearch, implemented: true },
  { label: 'Transferências', href: '/transfers', icon: ArrowLeftRight, implemented: true },
  { label: 'Picking', href: '/picking', icon: ShoppingCart, implemented: true },
  { label: 'Packing', href: '/packing', icon: Box, implemented: true },
  { label: 'Shipping', href: '/shipping', icon: Ship, implemented: true },
  { label: 'MRP', href: '/mrp', icon: Factory, implemented: true },
  { label: 'Relatórios', href: '/reports', icon: FileText, implemented: true },
]

interface SidebarNavProps {
  onNavClick?: () => void
}

function SidebarNav({ onNavClick }: SidebarNavProps) {
  const pathname = usePathname()

  return (
    <nav className="flex flex-col gap-1 px-2 py-4" aria-label="Navegação principal">
      {NAV_ITEMS.map((item) => {
        const Icon = item.icon
        const isActive = pathname.startsWith(item.href)

        return (
          <Link
            key={item.href}
            href={item.href}
            onClick={onNavClick}
            className={cn(
              'group flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
              isActive
                ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                : 'text-sidebar-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-accent-foreground',
              !item.implemented && 'opacity-60 cursor-default pointer-events-none',
            )}
            aria-current={isActive ? 'page' : undefined}
            tabIndex={!item.implemented ? -1 : undefined}
          >
            <Icon className="h-4 w-4 shrink-0" />
            <span className="flex-1">{item.label}</span>
            {!item.implemented && (
              <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
                Em breve
              </Badge>
            )}
          </Link>
        )
      })}
    </nav>
  )
}

export function Sidebar() {
  return (
    <aside
      className="hidden md:flex md:flex-col md:w-60 md:shrink-0 border-r bg-sidebar"
      aria-label="Barra lateral"
    >
      <div className="flex h-16 items-center gap-2 border-b px-4">
        <Warehouse className="h-6 w-6 text-primary" />
        <span className="font-semibold text-sidebar-foreground">Odin WMS</span>
      </div>
      <div className="flex-1 overflow-y-auto">
        <SidebarNav />
      </div>
    </aside>
  )
}

export function MobileSidebar({ onClose }: { onClose: () => void }) {
  return (
    <div className="flex flex-col h-full bg-sidebar">
      <div className="flex h-16 items-center gap-2 border-b px-4">
        <Warehouse className="h-6 w-6 text-primary" />
        <span className="font-semibold text-sidebar-foreground">Odin WMS</span>
      </div>
      <div className="flex-1 overflow-y-auto">
        <SidebarNav onNavClick={onClose} />
      </div>
    </div>
  )
}
