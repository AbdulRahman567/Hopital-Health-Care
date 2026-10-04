export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  success: boolean;
  data: {
    accessToken: string;
    user: {
      id: string;
      email: string;
      firstName: string;
      lastName: string;
      roles: string[];
    };
  };
  traceId?: string;
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

export interface Role {
  id: string;
  name: string;
  systemFlag: boolean;
  permissions: string[];
}

export interface Permission {
  code: string;
  module: string;
  action: string;
  description: string;
}

export interface StaffMember {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
  status: string;
}

export interface PaginatedResponse<T> {
  success: boolean;
  data: T[];
  meta: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
  traceId?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  traceId?: string;
}
