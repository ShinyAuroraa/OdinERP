'use client';

import { signIn } from 'next-auth/react';
import { Button } from '@/components/ui/button';

export function SignInButton() {
  return (
    <Button
      className="w-full"
      onClick={() => { void signIn('keycloak', { callbackUrl: '/dashboard' }); }}
    >
      Entrar com Keycloak
    </Button>
  );
}
