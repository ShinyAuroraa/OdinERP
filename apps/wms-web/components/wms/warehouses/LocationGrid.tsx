'use client'

import React, { useState } from 'react'
import { cn } from '@/lib/utils'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { LoadingState } from '@/components/shared/LoadingState'
import { useLocations } from '@/lib/api/warehouses'
import type { Location, LocationType, LocationStatus } from '@/types/warehouse'

const STATUS_COLORS: Record<LocationStatus, string> = {
  AVAILABLE: 'bg-green-100 border-green-400 hover:bg-green-200',
  OCCUPIED: 'bg-yellow-100 border-yellow-400 hover:bg-yellow-200',
  BLOCKED: 'bg-red-100 border-red-400 hover:bg-red-200',
  QUARANTINE: 'bg-orange-100 border-orange-400 hover:bg-orange-200',
}

const STATUS_LABELS: Record<LocationStatus, string> = {
  AVAILABLE: 'Disponível',
  OCCUPIED: 'Ocupado',
  BLOCKED: 'Bloqueado',
  QUARANTINE: 'Quarentena',
}

const TYPE_LABELS: Record<LocationType, string> = {
  PICKING: 'Picking',
  RECEIVING_DOCK: 'Doca Receb.',
  QUARANTINE: 'Quarentena',
  GENERAL_STORAGE: 'Estoque Geral',
  CROSS_DOCK: 'Cross-Dock',
  STAGING: 'Staging',
  BULK_STORAGE: 'Granel',
}

interface LocationGridProps {
  shelfId: string
  filterType?: LocationType | 'ALL'
}

export function LocationGrid({ shelfId, filterType = 'ALL' }: LocationGridProps) {
  const { data: locations, isLoading } = useLocations(shelfId)
  const [selectedType, setSelectedType] = useState<LocationType | 'ALL'>(filterType)

  if (isLoading) {
    return <LoadingState variant="skeleton" rows={3} />
  }

  const filtered = (locations ?? []).filter(
    (loc) => selectedType === 'ALL' || loc.locationType === selectedType,
  )

  const uniqueTypes = [...new Set((locations ?? []).map((l) => l.locationType))]

  return (
    <div className="space-y-4">
      <div className="flex gap-2 flex-wrap">
        <button
          className={cn(
            'px-3 py-1 rounded-full text-xs border transition-colors',
            selectedType === 'ALL' ? 'bg-primary text-primary-foreground' : 'bg-muted',
          )}
          onClick={() => setSelectedType('ALL')}
        >
          Todos
        </button>
        {uniqueTypes.map((type) => (
          <button
            key={type}
            className={cn(
              'px-3 py-1 rounded-full text-xs border transition-colors',
              selectedType === type ? 'bg-primary text-primary-foreground' : 'bg-muted',
            )}
            onClick={() => setSelectedType(type)}
          >
            {TYPE_LABELS[type]}
          </button>
        ))}
      </div>

      <div className="flex gap-4 text-xs text-muted-foreground">
        {(Object.entries(STATUS_LABELS) as [LocationStatus, string][]).map(([status, label]) => (
          <span key={status} className="flex items-center gap-1">
            <span className={cn('inline-block w-3 h-3 rounded border', STATUS_COLORS[status])} />
            {label}
          </span>
        ))}
      </div>

      {filtered.length === 0 ? (
        <p className="text-sm text-muted-foreground py-8 text-center">
          Nenhuma localização cadastrada.
        </p>
      ) : (
        <TooltipProvider>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(80px,1fr))] gap-2">
            {filtered.map((loc) => (
              <LocationCard key={loc.id} location={loc} />
            ))}
          </div>
        </TooltipProvider>
      )}
    </div>
  )
}

function LocationCard({ location }: { location: Location }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div
          className={cn(
            'border rounded p-2 text-center cursor-default transition-colors',
            STATUS_COLORS[location.status],
          )}
          role="listitem"
          aria-label={`Localização ${location.code}: ${STATUS_LABELS[location.status]}`}
        >
          <p className="text-xs font-medium truncate">{location.code}</p>
          <p className="text-[10px] text-muted-foreground truncate">
            {TYPE_LABELS[location.locationType]}
          </p>
        </div>
      </TooltipTrigger>
      <TooltipContent>
        <div className="text-xs space-y-1">
          <p className="font-medium">{location.code}</p>
          <p>Endereço: {location.fullAddress}</p>
          <p>Tipo: {TYPE_LABELS[location.locationType]}</p>
          <p>Status: {STATUS_LABELS[location.status]}</p>
          {location.capacityUnits && <p>Capacidade: {location.capacityUnits} un.</p>}
        </div>
      </TooltipContent>
    </Tooltip>
  )
}
