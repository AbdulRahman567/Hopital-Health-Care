import { describe, it, expect } from "vitest";
import { ApiError, normalizeApiError } from "@/lib/api/errors";

describe("ApiError", () => {
  it("creates an ApiError with all properties", () => {
    const error = new ApiError("Test error", "TEST_CODE", [
      { field: "email", message: "Invalid email" },
    ], "trace-123");

    expect(error.message).toBe("Test error");
    expect(error.code).toBe("TEST_CODE");
    expect(error.fields).toEqual([{ field: "email", message: "Invalid email" }]);
    expect(error.traceId).toBe("trace-123");
  });

  it("defaults to INTERNAL_ERROR code", () => {
    const error = new ApiError("Test error");
    expect(error.code).toBe("INTERNAL_ERROR");
  });
});

describe("normalizeApiError", () => {
  it("returns ApiError as-is", () => {
    const original = new ApiError("Test", "TEST");
    const result = normalizeApiError(original);
    expect(result).toBe(original);
  });

  it("normalizes Axios error with response data", () => {
    const axiosError = {
      response: {
        data: {
          success: false,
          error: {
            code: "VALIDATION_FAILED",
            message: "Validation failed",
            fields: [{ field: "email", message: "Required" }],
          },
          traceId: "trace-456",
        },
      },
    };

    const result = normalizeApiError(axiosError);
    expect(result).toBeInstanceOf(ApiError);
    expect(result.code).toBe("VALIDATION_FAILED");
    expect(result.message).toBe("Validation failed");
    expect(result.traceId).toBe("trace-456");
  });

  it("normalizes generic Error", () => {
    const result = normalizeApiError(new Error("Something went wrong"));
    expect(result).toBeInstanceOf(ApiError);
    expect(result.message).toBe("Something went wrong");
    expect(result.code).toBe("INTERNAL_ERROR");
  });

  it("normalizes unknown error", () => {
    const result = normalizeApiError("unknown error");
    expect(result).toBeInstanceOf(ApiError);
    expect(result.message).toBe("An unexpected error occurred");
  });
});
