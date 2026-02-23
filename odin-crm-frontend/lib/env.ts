// Import this module only from server-side entry points (layout.tsx, instrumentation.ts)
// Do not import in individual Server Components to avoid re-parsing on every request
import { z } from 'zod';

const envSchema = z.object({
  NEXTAUTH_URL: z.string().url(),
  NEXTAUTH_SECRET: z.string().min(32),
  KEYCLOAK_CLIENT_ID: z.string(),
  KEYCLOAK_CLIENT_SECRET: z.string(),
  KEYCLOAK_ISSUER: z.string().url(),
  NEXT_PUBLIC_API_URL: z.string().url(),
});

export const env = envSchema.parse({
  NEXTAUTH_URL: process.env.NEXTAUTH_URL,
  NEXTAUTH_SECRET: process.env.NEXTAUTH_SECRET,
  KEYCLOAK_CLIENT_ID: process.env.KEYCLOAK_CLIENT_ID,
  KEYCLOAK_CLIENT_SECRET: process.env.KEYCLOAK_CLIENT_SECRET,
  KEYCLOAK_ISSUER: process.env.KEYCLOAK_ISSUER,
  NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL,
});
