import { createSlice, type PayloadAction } from "@reduxjs/toolkit";
import type { RootState } from "./index";
import type { User } from "@/lib/api/types";

interface AuthState {
  user: User | null;
  accessToken: string | null;
  roles: string[];
  isAuthenticated: boolean;
  isLoading: boolean;
}

const initialState: AuthState = {
  user: null,
  accessToken: null,
  roles: [],
  isAuthenticated: false,
  isLoading: true,
};

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setSession: (
      state,
      action: PayloadAction<{
        user: User;
        accessToken: string;
        roles: string[];
      }>
    ) => {
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.roles = action.payload.roles;
      state.isAuthenticated = true;
      state.isLoading = false;
    },
    clearSession: (state) => {
      state.user = null;
      state.accessToken = null;
      state.roles = [];
      state.isAuthenticated = false;
      state.isLoading = false;
    },
    setAccessToken: (state, action: PayloadAction<string>) => {
      state.accessToken = action.payload;
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.isLoading = action.payload;
    },
  },
});

export const { setSession, clearSession, setAccessToken, setLoading } =
  authSlice.actions;

export const selectIsAuthenticated = (state: RootState) =>
  state.auth.isAuthenticated;
export const selectUser = (state: RootState) => state.auth.user;
export const selectRoles = (state: RootState) => state.auth.roles;
export const selectAccessToken = (state: RootState) => state.auth.accessToken;
export const selectIsLoading = (state: RootState) => state.auth.isLoading;

export default authSlice.reducer;
