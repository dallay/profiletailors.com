import { ref, computed } from 'vue'

export interface ValidationRule<T = unknown> {
  validate: (value: T) => boolean
  message: string
}

interface FieldValidation<T = unknown> {
  rules: ValidationRule<T>[]
  error?: string
}

export function useFormValidation<T extends object>(
  fieldsDefinition: Record<keyof T, ValidationRule[]> = {} as Record<keyof T, ValidationRule[]>,
) {
  const fields = ref<Record<string, FieldValidation>>({})
  const touched = ref<Set<string>>(new Set())

  // Initialize fields
  Object.entries(fieldsDefinition).forEach(([name, rules]) => {
    fields.value[name] = { rules: rules as ValidationRule[] }
  })

  const validateField = (fieldName: string, value: unknown): boolean => {
    const field = fields.value[fieldName]
    if (!field) return true

    for (const rule of field.rules) {
      if (!rule.validate(value)) {
        field.error = rule.message
        return false
      }
    }

    field.error = undefined
    return true
  }

  const validateAll = (values: T): boolean => {
    let isValid = true
    Object.keys(fields.value).forEach((name) => {
      if (!validateField(name, (values as Record<string, unknown>)[name])) {
        isValid = false
      }
    })
    return isValid
  }

  const markFieldTouched = (fieldName: string) => {
    touched.value.add(fieldName)
  }

  const getFieldError = (fieldName: string): string | undefined => {
    const field = fields.value[fieldName]
    return field?.error
  }

  const hasErrors = computed(() => {
    return Object.values(fields.value).some((field) => field.error)
  })

  const reset = () => {
    Object.values(fields.value).forEach((field) => {
      field.error = undefined
    })
    touched.value.clear()
  }

  return {
    validateField,
    validateAll,
    markFieldTouched,
    getFieldError,
    hasErrors,
    touched,
    reset,
  }
}
