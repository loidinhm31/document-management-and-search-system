import i18next from "i18next";

import axiosInstance from "@/services/axios.config";
import { UpdateCredentialsRequest } from "@/types/auth";

import { BaseService } from "./base.service";

class UserService extends BaseService {
  updateCredentials(data: UpdateCredentialsRequest) {
    return this.handleApiResponse(axiosInstance.post("/v1/auth/update-credentials", data), {
      successMessage: i18next.t("profile.updateProfile.messages.success"),
      errorMessage: i18next.t("profile.updateProfile.messages.error"),
    });
  }

  resetPassword(token: string, newPassword: string) {
    return this.handleApiResponse(
      axiosInstance.post("/v1/auth/password/reset", {
        token,
        newPassword,
      }),
      {
        successMessage: i18next.t("profile.password.resetSuccess"),
        errorMessage: i18next.t("profile.password.resetError"),
      },
    );
  }

  getCurrentUser() {
    return this.handleApiResponse(axiosInstance.get("/v1/users/me"));
  }

  enable2FA() {
    return this.handleApiResponse(axiosInstance.post("/v1/users/2fa/enable"), {
      errorMessage: i18next.t("profile.twoFactor.messages.enableError"),
    });
  }

  verify2FA(code: string) {
    return this.handleApiResponse(axiosInstance.post("/v1/users/2fa/verify", { code }), {
      successMessage: i18next.t("profile.twoFactor.messages.enableSuccess"),
      errorMessage: i18next.t("profile.twoFactor.messages.verifyError"),
    });
  }

  disable2FA() {
    return this.handleApiResponse(axiosInstance.post("/v1/users/2fa/disable"), {
      successMessage: i18next.t("profile.twoFactor.messages.disableSuccess"),
      errorMessage: i18next.t("profile.twoFactor.messages.disableError"),
    });
  }

  get2FAStatus(userId: number) {
    return this.handleApiResponse(axiosInstance.get(`/v1/users/${userId}/2fa/status`), {
      errorMessage: i18next.t("profile.twoFactor.messages.statusError"),
    });
  }
}

export const userService = new UserService();
