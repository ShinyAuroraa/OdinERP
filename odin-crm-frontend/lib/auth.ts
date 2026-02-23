import NextAuth from 'next-auth';
import Keycloak from 'next-auth/providers/keycloak';
// NOTE: Do NOT import lib/env here — this file is imported by middleware.ts
// which runs in Edge Runtime. server-only package throws in Edge Runtime.
// Use process.env directly.

interface RefreshToken {
  accessToken?: string;
  refreshToken?: string;
  expiresAt?: number;
  roles?: string[];
  error?: string;
  [key: string]: unknown;
}

async function refreshAccessToken(token: RefreshToken): Promise<RefreshToken> {
  try {
    const issuer = process.env.KEYCLOAK_ISSUER!;
    const response = await fetch(
      `${issuer}/protocol/openid-connect/token`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'refresh_token',
          client_id: process.env.KEYCLOAK_CLIENT_ID!,
          client_secret: process.env.KEYCLOAK_CLIENT_SECRET!,
          refresh_token: token.refreshToken ?? '',
        }),
      }
    );

    const data = await response.json() as {
      access_token?: string;
      refresh_token?: string;
      expires_in?: number;
      error?: string;
    };

    if (!response.ok || data.error) {
      throw new Error(data.error ?? 'RefreshAccessTokenError');
    }

    return {
      ...token,
      accessToken: data.access_token,
      refreshToken: data.refresh_token ?? token.refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + (data.expires_in ?? 300),
      error: undefined,
    };
  } catch {
    return { ...token, error: 'RefreshAccessTokenError' };
  }
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
      issuer: process.env.KEYCLOAK_ISSUER!,
    }),
  ],
  callbacks: {
    async jwt({ token, account }) {
      // Initial sign-in — store tokens from Keycloak
      if (account) {
        const roles =
          (
            (token as { realm_access?: { roles?: string[] } })
              .realm_access?.roles ?? []
          ).filter((r: string) => r.startsWith('crm-'));

        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          expiresAt: account.expires_at,
          roles,
        };
      }

      // Token still valid
      if (Date.now() < ((token as RefreshToken).expiresAt ?? 0) * 1000) {
        return token;
      }

      // Token expired — perform silent refresh
      return refreshAccessToken(token as RefreshToken);
    },

    session({ session, token }) {
      const t = token as RefreshToken;
      session.accessToken = t.accessToken ?? '';
      if (session.user) {
        (session.user as { roles?: string[] }).roles = t.roles ?? [];
      }
      if (t.error) {
        (session as { error?: string }).error = t.error;
      }
      return session;
    },
  },
});
