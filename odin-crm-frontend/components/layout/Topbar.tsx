interface TopbarProps {
  breadcrumb?: string;
}

export function Topbar({ breadcrumb }: TopbarProps) {
  return (
    <header className="flex items-center justify-between h-16 px-6 border-b bg-background">
      <div className="text-sm text-muted-foreground">
        {breadcrumb ?? 'Dashboard'}
      </div>

      <div className="flex items-center gap-3">
        {/* User avatar placeholder — implementado na Story 1.5 */}
        <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center text-xs font-medium">
          U
        </div>
      </div>
    </header>
  );
}
