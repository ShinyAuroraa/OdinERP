import { LayoutDashboard } from 'lucide-react'

export default function DashboardPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] gap-4 text-center">
      <LayoutDashboard className="h-16 w-16 text-muted-foreground" />
      <h1 className="text-2xl font-bold">Dashboard</h1>
      <p className="text-muted-foreground max-w-md">
        Dashboard em construção. Será implementado na Wave 7 (Story 8.1) com KPIs em tempo real,
        gráficos Recharts e widgets configuráveis por role.
      </p>
    </div>
  )
}
