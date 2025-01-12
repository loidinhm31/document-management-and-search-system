import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { LoginRequest, SignupRequest } from "@/types/auth";


class AuthService extends BaseService {
  login(data: LoginRequest) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/login", data)
    );
  }

  register(data: SignupRequest) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/register", data)
    );
  }

  forgotPassword(email: string) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/password/forgot", null, {
        params: { email }
      })
    );
  }

  resetPassword(token: string, newPassword: string) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/password/reset", {
        token,
        newPassword
      })
    );
  }

  verify2FA(code: string, jwtToken?: string) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/2fa/verify", {
        code,
        jwtToken
      })
    );
  }

  enable2FA() {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/2fa/enable")
    );
  }

  disable2FA() {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/2fa/disable")
    );
  }

  get2FAStatus() {
    return this.handleApiResponse(
      axiosInstance.get("/v1/auth/2fa/status")
    );
  }

  getCurrentUser() {
    return this.handleApiResponse(
      axiosInstance.get("/v1/auth/user")
    );
  }
}

export const authService = new AuthService();