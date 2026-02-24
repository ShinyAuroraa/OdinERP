'use client'

import React from 'react'
import { DataTable } from '@/components/shared/DataTable'
import { PageHeader } from '@/components/shared/PageHeader'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useStockBalance } from '@/lib/api/stock'
import type { StockBalance, StockBalanceFilters } from '@/types/stock'
import type { ColumnDef } from '@tanstack/react-table'

const columns: ColumnDef<StockBalance>[] = [
  { accessorKey: 'productSku', header: 'SKU' },
  { accessorKey: 'productName', header: 'Produto' },
  { accessorKey: 'locationCode', header: 'Localização' },
  { accessorKey: 'lotNumber', header: 'Lote', cell: ({ row }) => row.original.lotNumber ?? '—' },
  { accessorKey: 'availableQuantity', header: 'Disponível' },
  { accessorKey: 'reservedQuantity', header: 'Reservado' },
  { accessorKey: 'totalQuantity', header: 'Total' },
]

interface StockListProps {
  warehouseId?: string
}

export function StockList({ warehouseId }: StockListProps) {
  const [productSearch, setProductSearch] = React.useState('')
  const [filters, setFilters] = React.useState<StockBalanceFilters>({})

  React.useEffect(() => {
    const timeout = setTimeout(() => {
      setFilters((f) => ({ ...f, warehouseId }))
    }, 300)
    return () => clearTimeout(timeout)
  }, [warehouseId])

  const { data: stock, isLoading } = useStockBalance(filters)

  const filtered = React.useMemo(() => {
    if (!productSearch) return stock ?? []
    const q = productSearch.toLowerCase()
    return (stock ?? []).filter(
      (s) => s.productSku.toLowerCase().includes(q) || s.productName.toLowerCase().includes(q),
    )
  }, [stock, productSearch])

  return (
    <div className="p-6 space-y-4">
      <PageHeader title="Estoque" description="Saldo de estoque por produto e localização" />
      <div className="flex gap-2">
        <Input
          placeholder="Buscar por SKU ou produto..."
          value={productSearch}
          onChange={(e) => setProductSearch(e.target.value)}
          className="max-w-xs"
        />
        <Select
          value={filters.warehouseId ?? 'ALL'}
          onValueChange={(v) => setFilters((f) => ({ ...f, warehouseId: v === 'ALL' ? undefined : v }))}
        >
          <SelectTrigger className="w-48">
            <SelectValue placeholder="Todos os armazéns" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Todos os armazéns</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <DataTable
        columns={columns}
        data={filtered}
        loading={isLoading}
        emptyMessage="Nenhum item em estoque."
        searchPlaceholder="Buscar..."
      />
    </div>
  )
}
