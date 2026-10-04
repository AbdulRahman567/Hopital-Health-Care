export interface ApiErrorFields {
  field: string;
  message: string;
}

export class ApiError extends Error {
  code: string;
  fields?: ApiErrorFields[];
  traceId?: string;

  constructor(
    message: string,
    code: string = "INTERNAL_ERROR",
    fields?: ApiErrorFields[],
    traceId?: string
  ) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.fields = fields;
    this.traceId = traceId;
  }
}

interface ErrorResponse {
  success: boolean;
  error?: {
    code: string;
    message: string;
    fields?: ApiErrorFields[];
  };
  traceId?: string;
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }

  if (error && typeof error === "object" && "response" in error) {
    const axiosError = error as {
      response?: { data?: ErrorResponse; status?: number };
      message?: string;
    };

    if (axiosError.response?.data) {
      const data = axiosError.response.data;
      return new ApiError(
        data.error?.message || "An unexpected error occurred",
        data.error?.code || "INTERNAL_ERROR",
        data.error?.fields,
        data.traceId
      );
    }

    if (axiosError.response?.status) {
      return new ApiError(
        `Request failed with status ${axiosError.response.status}`,
        "INTERNAL_ERROR"
      );
    }
  }

  if (error instanceof Error) {
    return new ApiError(error.message, "INTERNAL_ERROR");
  }

  return new ApiError("An unexpected error occurred", "INTERNAL_ERROR");
}
