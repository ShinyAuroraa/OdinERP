'use client'

import { WarehouseList } from '@/components/wms/warehouses/WarehouseList'
import { ErrorBoundary } from '@/components/shared/ErrorBoundary'

export default function WarehousesPage() {
  return (
    <ErrorBoundary>
      <WarehouseList />
    </ErrorBoundary>
  )
}
