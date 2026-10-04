import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import type {
  LoginRequest,
  LoginResponse,
  Role,
  Permission,
  StaffMember,
  PaginatedResponse,
} from "./types";
import { useAppDispatch } from "@/store";
import { setSession, clearSession } from "@/store/authSlice";

export function useLogin() {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: LoginRequest) => {
      const response = await apiClient.post<LoginResponse>(
        API_ENDPOINTS.AUTH.LOGIN,
        data
      );
      return response.data;
    },
    onSuccess: (data) => {
      dispatch(
        setSession({
          user: data.data.user,
          accessToken: data.data.accessToken,
          roles: data.data.user.roles,
        })
      );
      queryClient.invalidateQueries();
    },
  });
}

export function useLogout() {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      await apiClient.post(API_ENDPOINTS.AUTH.LOGOUT);
    },
    onSuccess: () => {
      dispatch(clearSession());
      queryClient.clear();
    },
  });
}

export function useRoles() {
  return useQuery({
    queryKey: ["roles"],
    queryFn: async () => {
      const response = await apiClient.get<PaginatedResponse<Role>>(
        API_ENDPOINTS.ROLES.LIST
      );
      return response.data;
    },
  });
}

export function usePermissions() {
  return useQuery({
    queryKey: ["permissions"],
    queryFn: async () => {
      const response = await apiClient.get<{ data: Permission[] }>(
        API_ENDPOINTS.PERMISSIONS.LIST
      );
      return response.data;
    },
  });
}

export function useStaff() {
  return useQuery({
    queryKey: ["staff"],
    queryFn: async () => {
      const response = await apiClient.get<PaginatedResponse<StaffMember>>(
        API_ENDPOINTS.STAFF.LIST
      );
      return response.data;
    },
  });
}
