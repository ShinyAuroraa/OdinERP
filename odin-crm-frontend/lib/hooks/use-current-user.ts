'use client';

import { useSession } from 'next-auth/react';

export function useCurrentUser() {
  const { data: session, status } = useSession();

  return {
    name: session?.user?.name ?? null,
    email: session?.user?.email ?? null,
    roles: (session?.user as { roles?: string[] } | undefined)?.roles ?? [],
    isLoading: status === 'loading',
    hasError: !!(session as { error?: string } | null)?.error,
  };
}
