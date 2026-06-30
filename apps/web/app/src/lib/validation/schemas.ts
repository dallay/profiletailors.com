import { z } from 'zod'

// ---------------------------------------------------------------------------
// Auth credentials (login + register)
// ---------------------------------------------------------------------------

export const authCredentialsSchema = z.object({
  email: z
    .string()
    .trim()
    .transform((val) => val.toLowerCase())
    .pipe(z.email('Please enter a valid email address.')),
  password: z.string().trim().min(1, 'Please enter your password.'),
})

export type AuthCredentials = z.infer<typeof authCredentialsSchema>

export const registerSchema = authCredentialsSchema
  .extend({
    confirmPassword: z.string().trim().min(1, 'Please confirm your password.'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords must match.',
    path: ['confirmPassword'],
  })

export type RegisterCredentials = z.infer<typeof registerSchema>

// ---------------------------------------------------------------------------
// Workspace rename
// ---------------------------------------------------------------------------

export const workspaceNameSchema = z
  .string()
  .trim()
  .min(1, 'Please enter a workspace name.')
  .max(255, 'Workspace name must be 255 characters or fewer.')

export type WorkspaceName = z.infer<typeof workspaceNameSchema>
