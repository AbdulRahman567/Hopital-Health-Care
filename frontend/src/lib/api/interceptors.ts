import { apiClient } from "./client";
import { silentRefresh } from "./refresh";
import { normalizeApiError } from "./errors";
import { store } from "@/store";
import { clearSession } from "@/store/authSlice";

export function setupInterceptors() {
  apiClient.interceptors.request.use(
    (config) => {
      const token = store.getState().auth.accessToken;
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    },
    (error) => Promise.reject(error)
  );

  apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
      const originalRequest = error.config;

      if (error.response?.status === 401 && !originalRequest._retry) {
        originalRequest._retry = true;

        try {
          const token = await silentRefresh();
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return apiClient(originalRequest);
        } catch (refreshError) {
          store.dispatch(clearSession());
          if (typeof window !== "undefined") {
            window.location.href = "/login";
          }
          return Promise.reject(refreshError);
        }
      }

      return Promise.reject(normalizeApiError(error));
    }
  );
}
