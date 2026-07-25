import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useFormValidation, type ValidationRule } from '@shared/composables'

interface AuthFormValues {
  email: string
  password: string
  confirmPassword?: string
  confirmedAgeEligibility?: boolean
  acceptedTerms?: boolean
}

const emailRules: ValidationRule[] = [
  {
    validate: (val: unknown) => Boolean(val),
    message: 'emailRequired',
  },
  {
    validate: (val: unknown) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(val)),
    message: 'invalidEmail',
  },
]

const passwordRules: ValidationRule[] = [
  {
    validate: (val: unknown) => Boolean(val),
    message: 'passwordRequired',
  },
  {
    validate: (val: unknown) => String(val).length >= 8,
    message: 'passwordTooShort',
  },
]

export function useAuthForm() {
  const route = useRoute()
  const isRegisterMode = computed(() => route.name === 'register')

  const email = ref('')
  const password = ref('')
  const confirmPassword = ref('')

  const formValidation = useFormValidation<AuthFormValues>({
    email: emailRules,
    password: passwordRules,
    confirmPassword: [
      {
        validate: (val: unknown) => !isRegisterMode.value || Boolean(val),
        message: 'confirmPasswordRequired',
      },
      {
        validate: (val: unknown) =>
          !isRegisterMode.value || !confirmPassword.value || val === password.value.trim(),
        message: 'passwordsMustMatch',
      },
    ],
    confirmedAgeEligibility: [
      {
        validate: (val: unknown) => !isRegisterMode.value || val === true,
        message: 'ageEligibilityRequired',
      },
    ],
    acceptedTerms: [
      {
        validate: (val: unknown) => !isRegisterMode.value || val === true,
        message: 'termsRequired',
      },
    ],
  })
  const confirmedAgeEligibility = ref(false)
  const acceptedTerms = ref(false)
  const formError = ref<string | null>(null)
  const isSubmitting = ref(false)

  // Clear form when mode changes
  watch(
    () => route.name,
    () => {
      email.value = ''
      password.value = ''
      confirmPassword.value = ''
      confirmedAgeEligibility.value = false
      acceptedTerms.value = false
      formError.value = null
      formValidation.reset()
    },
  )

  const validateForm = (): boolean => {
    const values: AuthFormValues = {
      email: email.value.trim(),
      password: password.value.trim(),
      ...(isRegisterMode.value && {
        confirmPassword: confirmPassword.value.trim(),
        confirmedAgeEligibility: confirmedAgeEligibility.value,
        acceptedTerms: acceptedTerms.value,
      }),
    }

    return formValidation.validateAll(values)
  }

  const getFormPayload = () => {
    const trimmed = {
      email: email.value.trim(),
      password: password.value.trim(),
    }

    if (isRegisterMode.value) {
      return {
        type: 'register' as const,
        data: {
          ...trimmed,
          confirmPassword: confirmPassword.value,
          confirmedAgeEligibility: confirmedAgeEligibility.value,
          acceptedTerms: acceptedTerms.value,
        },
      }
    }

    return {
      type: 'login' as const,
      data: trimmed,
    }
  }

  const resetForm = () => {
    email.value = ''
    password.value = ''
    confirmPassword.value = ''
    confirmedAgeEligibility.value = false
    acceptedTerms.value = false
    formError.value = null
    formValidation.reset()
  }

  const setFormError = (err: string | null) => {
    formError.value = err
  }

  const setSubmitting = (state: boolean) => {
    isSubmitting.value = state
  }

  // Computed object for template compatibility
  const fieldErrors = computed(() => ({
    email: formValidation.getFieldError('email'),
    password: formValidation.getFieldError('password'),
    confirmPassword: formValidation.getFieldError('confirmPassword'),
    confirmedAgeEligibility: formValidation.getFieldError('confirmedAgeEligibility'),
    acceptedTerms: formValidation.getFieldError('acceptedTerms'),
  }))

  return {
    // State
    email,
    password,
    confirmPassword,
    confirmedAgeEligibility,
    acceptedTerms,
    formError,
    isSubmitting,
    isRegisterMode,

    // Validation state
    fieldErrors,
    hasErrors: formValidation.hasErrors,

    // Methods
    validateForm,
    getFormPayload,
    resetForm,
    setFormError,
    setSubmitting,
    markFieldTouched: formValidation.markFieldTouched,
  }
}
