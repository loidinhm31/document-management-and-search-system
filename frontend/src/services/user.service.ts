import i18next from "i18next";

import axiosInstance from "@/services/axios.config";
import { UpdateCredentialsRequest, UpdatePasswordRequest, User } from "@/types/auth";

import { BaseService } from "./base.service";

class UserService extends BaseService {
  updateCredentials(data: UpdateCredentialsRequest) {
    return this.handleApiResponse(axiosInstance.post("/auth/api/v1/auth/update-credentials", data), {
      successMessage: i18next.t("profile.updateProfile.messages.success"),
      errorMessage: i18next.t("profile.updateProfile.messages.error"),
    });
  }

  resetPassword(token: string, newPassword: string) {
    return this.handleApiResponse(
      axiosInstance.post("/auth/api/v1/auth/password/reset", {
        token,
        newPassword,
      }),
      {
        successMessage: i18next.t("profile.password.resetSuccess"),
        errorMessage: i18next.t("profile.password.resetError"),
      },
    );
  }

  updatePassword(userId: string, data: UpdatePasswordRequest) {
    return axiosInstance.put(`/auth/api/v1/users/${userId}/password`, data);
  }

  getCurrentUser() {
    return this.handleApiResponse(axiosInstance.get<User>("/auth/api/v1/users/me"));
  }

  enable2FA(userId: string) {
    return this.handleApiResponse(axiosInstance.post(`/auth/api/v1/users/${userId}/2fa/enable`), {
      errorMessage: i18next.t("profile.twoFactor.messages.enableError"),
    });
  }

  verify2FA(userId: string, code: string) {
    return this.handleApiResponse(axiosInstance.post(`/auth/api/v1/users/${userId}/2fa/verify`, { code }), {
      successMessage: i18next.t("profile.twoFactor.messages.enableSuccess"),
      errorMessage: i18next.t("profile.twoFactor.messages.verifyError"),
    });
  }

  disable2FA(userId: string) {
    return this.handleApiResponse(axiosInstance.post(`/auth/api/v1/users/${userId}/2fa/disable`), {
      successMessage: i18next.t("profile.twoFactor.messages.disableSuccess"),
      errorMessage: i18next.t("profile.twoFactor.messages.disableError"),
    });
  }

  get2FAStatus(userId: string) {
    return this.handleApiResponse(axiosInstance.get(`/auth/api/v1/users/${userId}/2fa/status`), {
      errorMessage: i18next.t("profile.twoFactor.messages.statusError"),
    });
  }
}

export const userService = new UserService();
