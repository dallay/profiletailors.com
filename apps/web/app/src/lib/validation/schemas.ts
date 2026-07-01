import { z } from 'zod'

// ---------------------------------------------------------------------------
// Auth credentials (login + register)
// ---------------------------------------------------------------------------

export const authCredentialsSchema = z.object({
  email: z
    .string()
    .trim()
    .transform((val) => val.toLowerCase())
    .pipe(z.email('invalidEmail')),
  password: z.string().trim().min(1, 'passwordRequired'),
})

export type AuthCredentials = z.infer<typeof authCredentialsSchema>

export const registerSchema = authCredentialsSchema
  .extend({
    confirmPassword: z.string().trim().min(1, 'confirmPasswordRequired'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'passwordsMustMatch',
    path: ['confirmPassword'],
  })

export type RegisterCredentials = z.infer<typeof registerSchema>

// ---------------------------------------------------------------------------
// Workspace rename
// ---------------------------------------------------------------------------

export const workspaceNameSchema = z
  .string()
  .trim()
  .min(1, 'workspaceNameRequired')
  .max(255, 'workspaceNameTooLong')

export type WorkspaceName = z.infer<typeof workspaceNameSchema>
