'use client'

import React from 'react'
import { useParams } from 'next/navigation'
import { ArrowLeft } from 'lucide-react'
import Link from 'next/link'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { LoadingState } from '@/components/shared/LoadingState'
import { ErrorBoundary } from '@/components/shared/ErrorBoundary'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useWarehouse } from '@/lib/api/warehouses'
import { ZoneList } from '@/components/wms/warehouses/ZoneList'

export default function WarehouseDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { data: warehouse, isLoading } = useWarehouse(id)

  if (isLoading) return <LoadingState variant="page" />
  if (!warehouse) return <p className="text-muted-foreground">Armazém não encontrado.</p>

  return (
    <ErrorBoundary>
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="sm" asChild>
            <Link href="/warehouses">
              <ArrowLeft className="h-4 w-4 mr-1" />
              Armazéns
            </Link>
          </Button>
        </div>

        <PageHeader
          title={warehouse.name}
          description={`Código: ${warehouse.code}${warehouse.location ? ` · ${warehouse.location}` : ''}`}
          actions={<StatusBadge status={warehouse.status} />}
        />

        <Tabs defaultValue="zones">
          <TabsList>
            <TabsTrigger value="zones">Zonas</TabsTrigger>
            <TabsTrigger value="overview">Visão Geral</TabsTrigger>
          </TabsList>

          <TabsContent value="zones" className="mt-4">
            <ZoneList warehouseId={id} />
          </TabsContent>

          <TabsContent value="overview" className="mt-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Card>
                <CardHeader><CardTitle className="text-sm text-muted-foreground">Capacidade</CardTitle></CardHeader>
                <CardContent>
                  <p className="text-2xl font-bold">
                    {warehouse.capacitySqMeters ? `${warehouse.capacitySqMeters} m²` : '—'}
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader><CardTitle className="text-sm text-muted-foreground">Zonas</CardTitle></CardHeader>
                <CardContent>
                  <p className="text-2xl font-bold">{warehouse.zonesCount ?? '—'}</p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader><CardTitle className="text-sm text-muted-foreground">Status</CardTitle></CardHeader>
                <CardContent>
                  <StatusBadge status={warehouse.status} />
                </CardContent>
              </Card>
              {warehouse.description && (
                <Card className="md:col-span-3">
                  <CardHeader><CardTitle className="text-sm text-muted-foreground">Descrição</CardTitle></CardHeader>
                  <CardContent>
                    <p className="text-sm">{warehouse.description}</p>
                  </CardContent>
                </Card>
              )}
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </ErrorBoundary>
  )
}
