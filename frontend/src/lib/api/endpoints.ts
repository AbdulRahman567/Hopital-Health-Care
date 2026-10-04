export const API_ENDPOINTS = {
  AUTH: {
    LOGIN: "/auth/login",
    LOGOUT: "/auth/logout",
    REFRESH: "/auth/refresh",
    FORGOT_PASSWORD: "/auth/forgot-password",
    RESET_PASSWORD: "/auth/reset-password",
    REGISTER_HOSPITAL: "/auth/register-hospital",
    VERIFY_EMAIL: "/auth/verify-email",
    RESEND_VERIFICATION: "/auth/resend-verification",
  },
  ROLES: {
    LIST: "/roles",
    DETAIL: (roleId: string) => `/roles/${roleId}`,
    CREATE: "/roles",
    UPDATE: (roleId: string) => `/roles/${roleId}`,
    DELETE: (roleId: string) => `/roles/${roleId}`,
  },
  PERMISSIONS: {
    LIST: "/permissions",
  },
  STAFF: {
    LIST: "/staff",
    DETAIL: (userId: string) => `/staff/${userId}`,
  },
} as const;
