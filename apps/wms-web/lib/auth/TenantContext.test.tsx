import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import React from 'react'
import { TenantProvider, useTenantContext } from './TenantContext'

function TenantConsumer() {
  const { tenantId } = useTenantContext()
  return <span data-testid="tenantId">{tenantId}</span>
}

describe('TenantContext', () => {
  it('fornece tenantId via context', () => {
    render(
      <TenantProvider tenantId="tenant-test-123">
        <TenantConsumer />
      </TenantProvider>,
    )
    expect(screen.getByTestId('tenantId').textContent).toBe('tenant-test-123')
  })

  it('lança erro se usado fora do provider', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<TenantConsumer />)).toThrow('useTenantContext must be used inside <TenantProvider>')
    consoleSpy.mockRestore()
  })
})
