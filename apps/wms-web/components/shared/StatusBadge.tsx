import * as React from 'react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

type StatusVariant = 'success' | 'warning' | 'destructive' | 'orange' | 'secondary'

const STATUS_MAP: Record<string, StatusVariant> = {
  // Green — success
  ACTIVE: 'success',
  CONFIRMED: 'success',
  COMPLETED: 'success',
  DONE: 'success',
  RECEIVED: 'success',
  AVAILABLE: 'success',
  SHIPPED: 'success',
  PASS: 'success',
  APPROVED: 'success',
  DELIVERED: 'success',
  // Yellow — warning
  PENDING: 'warning',
  IN_PROGRESS: 'warning',
  PICKING: 'warning',
  PICKING_PENDING: 'warning',
  PROCESSING: 'warning',
  RESERVING: 'warning',
  PARTIAL: 'warning',
  COUNTING: 'warning',
  // Red — destructive
  INACTIVE: 'destructive',
  CANCELLED: 'destructive',
  REJECTED: 'destructive',
  FAILED: 'destructive',
  ERROR: 'destructive',
  FAIL: 'destructive',
  // Orange — warning-destructive
  QUARANTINE: 'orange',
  BLOCKED: 'orange',
  STOCK_SHORTAGE: 'orange',
  DIVERGENT: 'orange',
  // Gray — secondary
  DRAFT: 'secondary',
  SCHEDULED: 'secondary',
  UNKNOWN: 'secondary',
  PENDING_ALLOCATION: 'secondary',
}

const VARIANT_CLASSES: Record<StatusVariant, string> = {
  success: 'bg-green-100 text-green-800 border-green-200 hover:bg-green-100',
  warning: 'bg-yellow-100 text-yellow-800 border-yellow-200 hover:bg-yellow-100',
  destructive: 'bg-red-100 text-red-800 border-red-200 hover:bg-red-100',
  orange: 'bg-orange-100 text-orange-800 border-orange-200 hover:bg-orange-100',
  secondary: 'bg-slate-100 text-slate-700 border-slate-200 hover:bg-slate-100',
}

interface StatusBadgeProps {
  status: string
  label?: string
  variant?: 'default' | 'success' | 'warning' | 'destructive' | 'outline'
  className?: string
}

/**
 * StatusBadge — badge de status WMS com cores padronizadas.
 * Fallback: badge secondary para status desconhecidos.
 */
export function StatusBadge({ status, label: labelProp, className }: StatusBadgeProps) {
  const upper = status.toUpperCase().replace(/\s+/g, '_')
  const variantKey = STATUS_MAP[upper] ?? 'secondary'
  const variantClass = VARIANT_CLASSES[variantKey]

  const label = labelProp ?? status
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())

  return (
    <Badge variant="outline" className={cn(variantClass, 'font-medium text-xs', className)}>
      {label}
    </Badge>
  )
}
