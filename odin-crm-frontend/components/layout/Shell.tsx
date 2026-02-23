'use client';

import { useState } from 'react';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

interface ShellProps {
  children: React.ReactNode;
  breadcrumb?: string;
}

export function Shell({ children, breadcrumb }: ShellProps) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      <Sidebar collapsed={collapsed} />

      <div className="flex flex-col flex-1 overflow-hidden">
        <Topbar breadcrumb={breadcrumb} />

        <button
          onClick={() => setCollapsed((prev) => !prev)}
          className="sr-only"
          aria-label={collapsed ? 'Expandir menu' : 'Recolher menu'}
        />

        <main className="flex-1 overflow-y-auto p-6">{children}</main>
      </div>
    </div>
  );
}
