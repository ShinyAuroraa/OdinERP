'use client'

import React from 'react'
import { type ColumnDef } from '@tanstack/react-table'
import { useForm } from 'react-hook-form'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataTable } from '@/components/shared/DataTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { toast } from 'sonner'
import { useReports, useGenerateReport, useSchedules, useToggleSchedule } from '@/lib/api/reports'
import type { ReportType, ReportStatus, GenerateReportRequest, Report, ReportSchedule } from '@/types/reports'

const REPORT_TYPES: { value: ReportType; label: string }[] = [
  { value: 'SNGPC', label: 'SNGPC' },
  { value: 'ANVISA', label: 'Anvisa' },
  { value: 'PRESCRIPTION', label: 'Receituário' },
]

const STATUS_LABELS: Record<ReportStatus, string> = {
  PENDING: 'Pendente',
  GENERATED: 'Gerado',
  ERROR: 'Erro',
}

function GenerateForm({ type }: { type: ReportType }) {
  const { register, handleSubmit, formState: { errors } } = useForm<GenerateReportRequest>()
  const generate = useGenerateReport()

  function onSubmit(values: GenerateReportRequest) {
    generate.mutate(
      { ...values, type },
      {
        onSuccess: () => toast.success('Relatório sendo gerado...'),
        onError: () => toast.error('Erro ao gerar relatório.'),
      },
    )
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="flex gap-3 items-end">
      <div className="space-y-1">
        <Label htmlFor={`from-${type}`} className="text-xs">De</Label>
        <Input id={`from-${type}`} type="date" className="w-36" {...register('periodFrom', { required: true })} />
        {errors.periodFrom && <p className="text-xs text-destructive">Obrigatório</p>}
      </div>
      <div className="space-y-1">
        <Label htmlFor={`to-${type}`} className="text-xs">Até</Label>
        <Input id={`to-${type}`} type="date" className="w-36" {...register('periodTo', { required: true })} />
        {errors.periodTo && <p className="text-xs text-destructive">Obrigatório</p>}
      </div>
      <Button type="submit" disabled={generate.isPending} size="sm">
        {generate.isPending ? 'Gerando...' : 'Gerar Relatório'}
      </Button>
    </form>
  )
}

function ScheduleItem({ schedule }: { schedule: ReportSchedule }) {
  const toggle = useToggleSchedule(schedule.id)
  return (
    <div className="flex items-center justify-between border rounded-lg p-3">
      <div>
        <p className="font-medium text-sm">{schedule.type} — {schedule.frequency}</p>
        {schedule.nextRun && (
          <p className="text-xs text-muted-foreground">
            Próxima execução: {new Date(schedule.nextRun).toLocaleDateString('pt-BR')}
          </p>
        )}
      </div>
      <div className="flex items-center gap-2">
        <Badge variant={schedule.active ? 'default' : 'secondary'}>
          {schedule.active ? 'Ativo' : 'Inativo'}
        </Badge>
        <Button size="sm" variant="outline" disabled={toggle.isPending}
          onClick={() => toggle.mutate(undefined, {
            onSuccess: () => toast.success('Agendamento atualizado.'),
            onError: () => toast.error('Erro ao atualizar agendamento.'),
          })}>
          {schedule.active ? 'Desativar' : 'Ativar'}
        </Button>
      </div>
    </div>
  )
}

export function ReportList() {
  const { data: reportsData, isLoading } = useReports()
  const { data: schedules = [] } = useSchedules()
  const reports = reportsData?.content ?? []

  const columns: ColumnDef<Report>[] = [
    { accessorKey: 'type', header: 'Tipo' },
    {
      accessorKey: 'periodFrom',
      header: 'Período',
      cell: ({ row }) => `${new Date(row.original.periodFrom).toLocaleDateString('pt-BR')} → ${new Date(row.original.periodTo).toLocaleDateString('pt-BR')}`,
    },
    {
      accessorKey: 'status',
      header: 'Status',
      cell: ({ row }) => <StatusBadge status={row.original.status} label={STATUS_LABELS[row.original.status]} />,
    },
    {
      accessorKey: 'generatedAt',
      header: 'Gerado Em',
      cell: ({ row }) => row.original.generatedAt ? new Date(row.original.generatedAt).toLocaleDateString('pt-BR') : '—',
    },
    {
      id: 'download',
      header: '',
      cell: ({ row }) => row.original.status === 'GENERATED' && row.original.fileUrl ? (
        <a href={row.original.fileUrl} target="_blank" rel="noopener noreferrer">
          <Button size="sm" variant="outline">Download</Button>
        </a>
      ) : null,
    },
  ]

  if (isLoading) {
    return <div className="p-6"><div className="animate-pulse h-64 bg-muted rounded-lg" /></div>
  }

  return (
    <div className="p-6 space-y-4">
      <PageHeader title="Relatórios" description="Relatórios regulatórios e conformidade" />
      <Tabs defaultValue="SNGPC">
        <TabsList>
          {REPORT_TYPES.map((t) => (
            <TabsTrigger key={t.value} value={t.value}>{t.label}</TabsTrigger>
          ))}
          <TabsTrigger value="schedules">Agendamentos</TabsTrigger>
        </TabsList>

        {REPORT_TYPES.map((t) => (
          <TabsContent key={t.value} value={t.value} className="space-y-4">
            <GenerateForm type={t.value} />
            <DataTable
              columns={columns}
              data={reports.filter((r) => r.type === t.value)}
              loading={false}
              emptyMessage="Nenhum relatório gerado."
            />
          </TabsContent>
        ))}

        <TabsContent value="schedules" className="space-y-3">
          <Card>
            <CardHeader><CardTitle className="text-base">Agendamentos Ativos</CardTitle></CardHeader>
            <CardContent>
              {schedules.length === 0 ? (
                <p className="text-sm text-muted-foreground">Nenhum agendamento configurado.</p>
              ) : (
                <div className="space-y-2">
                  {schedules.map((s) => <ScheduleItem key={s.id} schedule={s} />)}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
