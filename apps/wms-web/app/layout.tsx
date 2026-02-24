import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'
import { KeycloakProvider } from '@/lib/auth/AuthContext'
import { QueryProvider } from '@/components/providers/QueryProvider'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: 'Odin WMS',
  description: 'Warehouse Management System — Odin ERP',
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body className={inter.className}>
        <QueryProvider>
          <KeycloakProvider>{children}</KeycloakProvider>
        </QueryProvider>
      </body>
    </html>
  )
}
