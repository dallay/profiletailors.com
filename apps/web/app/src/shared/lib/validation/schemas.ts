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
    password: z.string().trim().min(1, 'passwordRequired').min(12, 'passwordTooShort'),
    confirmPassword: z.string().trim().min(1, 'confirmPasswordRequired'),
    confirmedAgeEligibility: z.literal(true, { message: 'ageEligibilityRequired' }),
    acceptedTerms: z.literal(true, { message: 'termsRequired' }),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'passwordsMustMatch',
    path: ['confirmPassword'],
  })

export type RegisterCredentials = z.infer<typeof registerSchema>

export const forgotPasswordSchema = z.object({
  email: z
    .string()
    .trim()
    .transform((value) => value.toLowerCase())
    .pipe(z.email('invalidEmail')),
})

export const resetPasswordSchema = z
  .object({
    password: z
      .string()
      .min(1, 'passwordRequired')
      .min(12, 'passwordTooShort')
      .max(128, 'passwordTooLong'),
    confirmPassword: z.string().min(1, 'confirmPasswordRequired'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'passwordsMustMatch',
    path: ['confirmPassword'],
  })

export type ForgotPasswordCredentials = z.infer<typeof forgotPasswordSchema>
export type ResetPasswordCredentials = z.infer<typeof resetPasswordSchema>

// ---------------------------------------------------------------------------
// Workspace rename
// ---------------------------------------------------------------------------

export const workspaceNameSchema = z
  .string()
  .trim()
  .min(1, 'workspaceNameRequired')
  .max(255, 'workspaceNameTooLong')

export type WorkspaceName = z.infer<typeof workspaceNameSchema>
