import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { LoginRequest, SignupRequest } from "@/types/auth";

class AuthService extends BaseService {
  login(data: LoginRequest) {
    return this.handleApiResponse(axiosInstance.post("/v1/auth/login", data));
  }

  register(data: SignupRequest) {
    return this.handleApiResponse(axiosInstance.post("/v1/auth/register", data));
  }

  forgotPassword(email: string) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/password/forgot", null, {
        params: { email },
      }),
    );
  }

  resetPassword(token: string, newPassword: string) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/password/reset", {
        token,
        newPassword,
      }),
    );
  }

  verify2FA(username: string, code: string) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/2fa/verify", {
        username,
        code,
      }),
    );
  }
}

export const authService = new AuthService();
