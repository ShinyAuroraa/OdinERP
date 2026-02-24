'use client'

import { usePathname } from 'next/navigation'
import Link from 'next/link'
import { ChevronRight, Home } from 'lucide-react'

const SEGMENT_LABELS: Record<string, string> = {
  dashboard: 'Dashboard',
  warehouses: 'Armazéns',
  products: 'Produtos',
  receiving: 'Recebimento',
  putaway: 'Putaway',
  stock: 'Estoque',
  traceability: 'Rastreabilidade',
  inventory: 'Inventário',
  audit: 'Auditoria',
  transfers: 'Transferências',
  picking: 'Picking',
  packing: 'Packing',
  shipping: 'Shipping',
  mrp: 'MRP',
  reports: 'Relatórios',
  new: 'Novo',
  edit: 'Editar',
}

function formatSegment(segment: string): string {
  return SEGMENT_LABELS[segment] ?? segment.charAt(0).toUpperCase() + segment.slice(1)
}

function isUuid(segment: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(segment)
}

export function Breadcrumbs() {
  const pathname = usePathname()

  const segments = pathname.split('/').filter(Boolean)

  const crumbs = segments.map((segment, index) => {
    const href = '/' + segments.slice(0, index + 1).join('/')
    const label = isUuid(segment) ? 'Detalhes' : formatSegment(segment)
    const isLast = index === segments.length - 1
    return { href, label, isLast }
  })

  if (crumbs.length === 0) return null

  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm text-muted-foreground">
      <Link href="/dashboard" className="flex items-center hover:text-foreground transition-colors">
        <Home className="h-4 w-4" />
        <span className="sr-only">Início</span>
      </Link>
      {crumbs.map((crumb) => (
        <span key={crumb.href} className="flex items-center gap-1">
          <ChevronRight className="h-4 w-4" />
          {crumb.isLast ? (
            <span className="font-medium text-foreground" aria-current="page">
              {crumb.label}
            </span>
          ) : (
            <Link href={crumb.href} className="hover:text-foreground transition-colors">
              {crumb.label}
            </Link>
          )}
        </span>
      ))}
    </nav>
  )
}
