import { apiClient } from "./client";
import { API_ENDPOINTS } from "./endpoints";
import { ApiError } from "./errors";

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: string) => void;
  reject: (reason?: unknown) => void;
}> = [];

function processQueue(error: ApiError | null, token: string | null = null) {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token as string);
    }
  });
  failedQueue = [];
}

export async function silentRefresh(): Promise<string> {
  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      failedQueue.push({ resolve, reject });
    });
  }

  isRefreshing = true;

  try {
    const response = await apiClient.post<{ data: { accessToken: string } }>(
      API_ENDPOINTS.AUTH.REFRESH
    );
    const token = response.data.data.accessToken;
    processQueue(null, token);
    return token;
  } catch (error) {
    const apiError =
      error instanceof ApiError
        ? error
        : new ApiError("Session expired. Please log in again.", "TOKEN_EXPIRED");
    processQueue(apiError, null);
    throw apiError;
  } finally {
    isRefreshing = false;
  }
}
