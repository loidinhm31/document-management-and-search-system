import axiosInstance from "@/services/axios.config";
import { LoginRequest, SignupRequest } from "@/types/auth";

export const AuthService = {
  login: (data: LoginRequest) =>
    axiosInstance.post("/v1/auth/login", data),

  register: (data: SignupRequest) =>
    axiosInstance.post("/v1/auth/register", data),

  forgotPassword: (email: string) =>
    axiosInstance.post("/v1/auth/password/forgot", null, {
      params: { email }
    }),

  resetPassword: (token: string, newPassword: string) =>
    axiosInstance.post("/v1/auth/password/reset", {
      token,
      newPassword
    }),

  verify2FA: (code: string, jwtToken?: string) =>
    axiosInstance.post("/v1/auth/2fa/verify", {
      code,
      jwtToken
    }),

  enable2FA: () =>
    axiosInstance.post("/v1/auth/2fa/enable"),

  disable2FA: () =>
    axiosInstance.post("/v1/auth/2fa/disable"),

  get2FAStatus: () =>
    axiosInstance.get("/v1/auth/2fa/status"),

  getCurrentUser: () =>
    axiosInstance.get("/v1/auth/user"),
};
