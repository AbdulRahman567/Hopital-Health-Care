import { describe, it, expect, beforeEach } from "vitest";
import { makeStore } from "@/store";
import {
  setSession,
  clearSession,
  setAccessToken,
  selectIsAuthenticated,
  selectUser,
  selectRoles,
  selectAccessToken,
} from "@/store/authSlice";
import type { User } from "@/lib/api/types";

const mockUser: User = {
  id: "user-1",
  email: "admin@hospital.com",
  firstName: "John",
  lastName: "Doe",
  roles: ["ADMIN"],
};

describe("authSlice", () => {
  let store: ReturnType<typeof makeStore>;

  beforeEach(() => {
    store = makeStore();
  });

  it("has correct initial state", () => {
    const state = store.getState();
    expect(selectIsAuthenticated(state)).toBe(false);
    expect(selectUser(state)).toBeNull();
    expect(selectRoles(state)).toEqual([]);
    expect(selectAccessToken(state)).toBeNull();
  });

  it("setSession sets user, token, and roles", () => {
    store.dispatch(
      setSession({
        user: mockUser,
        accessToken: "token-123",
        roles: ["ADMIN"],
      })
    );

    const state = store.getState();
    expect(selectIsAuthenticated(state)).toBe(true);
    expect(selectUser(state)).toEqual(mockUser);
    expect(selectRoles(state)).toEqual(["ADMIN"]);
    expect(selectAccessToken(state)).toBe("token-123");
  });

  it("clearSession resets to initial state", () => {
    store.dispatch(
      setSession({
        user: mockUser,
        accessToken: "token-123",
        roles: ["ADMIN"],
      })
    );
    store.dispatch(clearSession());

    const state = store.getState();
    expect(selectIsAuthenticated(state)).toBe(false);
    expect(selectUser(state)).toBeNull();
    expect(selectRoles(state)).toEqual([]);
    expect(selectAccessToken(state)).toBeNull();
  });

  it("setAccessToken updates only the token", () => {
    store.dispatch(
      setSession({
        user: mockUser,
        accessToken: "token-123",
        roles: ["ADMIN"],
      })
    );
    store.dispatch(setAccessToken("new-token"));

    const state = store.getState();
    expect(selectAccessToken(state)).toBe("new-token");
    expect(selectUser(state)).toEqual(mockUser);
  });
});
