import { UpdateCredentialsRequest } from "@/types/auth";
import axiosInstance from "@/services/axios.config";


export const UserService = {
  updateCredentials: (data: UpdateCredentialsRequest) =>
    axiosInstance.post("/v1/auth/update-credentials", data),

  resetPassword: (token: string, newPassword: string) =>
    axiosInstance.post("/v1/auth/password/reset", {
      token,
      newPassword
    }),

  enable2FA: () =>
    axiosInstance.post("/v1/auth/2fa/enable"),

  verify2FA: (code: string) =>
    axiosInstance.post("/v1/auth/2fa/verify", {
      code
    }),

  disable2FA: () =>
    axiosInstance.post("/v1/auth/2fa/disable"),

  get2FAStatus: () =>
    axiosInstance.get("/v1/auth/2fa/status")
};