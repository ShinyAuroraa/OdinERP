import React from 'react'
import { Package, Thermometer, Snowflake, AlertTriangle, ArrowRight, Shield } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { StorageType } from '@/types/product'

interface StorageTypeConfig {
  label: string
  className: string
  Icon: React.ComponentType<{ className?: string }>
}

const STORAGE_TYPE_CONFIG: Record<StorageType, StorageTypeConfig> = {
  DRY: { label: 'Seco', className: 'bg-gray-100 text-gray-800 border-gray-300', Icon: Package },
  REFRIGERATED: { label: 'Refrigerado', className: 'bg-blue-100 text-blue-800 border-blue-300', Icon: Thermometer },
  FROZEN: { label: 'Congelado', className: 'bg-cyan-100 text-cyan-800 border-cyan-300', Icon: Snowflake },
  HAZARDOUS: { label: 'Perigoso', className: 'bg-red-100 text-red-800 border-red-300', Icon: AlertTriangle },
  OVERSIZED: { label: 'Carga Pesada', className: 'bg-orange-100 text-orange-800 border-orange-300', Icon: ArrowRight },
  CONTROLLED: { label: 'Controlado', className: 'bg-yellow-100 text-yellow-800 border-yellow-300', Icon: Shield },
}

interface StorageTypeBadgeProps {
  storageType: StorageType
  showIcon?: boolean
}

export function StorageTypeBadge({ storageType, showIcon = true }: StorageTypeBadgeProps) {
  const config = STORAGE_TYPE_CONFIG[storageType]
  if (!config) return null
  const { label, className, Icon } = config

  return (
    <Badge variant="outline" className={cn('gap-1', className)}>
      {showIcon && <Icon className="h-3 w-3" />}
      {label}
    </Badge>
  )
}
