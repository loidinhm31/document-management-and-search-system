import i18next from "i18next";

import axiosInstance from "@/services/axios.config";

import { BaseService } from "./base.service";

class AdminService extends BaseService {
  getAllUsers() {
    return this.handleApiResponse(
      axiosInstance.get("/v1/admin/users")
    );
  }

  getUser(id: number) {
    return this.handleApiResponse(
      axiosInstance.get(`/v1/admin/users/${id}`)
    );
  }

  getAllRoles() {
    return this.handleApiResponse(
      axiosInstance.get("/v1/admin/roles")
    );
  }

  updateUserRole(userId: number, request: { userId: number; roleName: string }) {
    return this.handleApiResponse(
      axiosInstance.put(`/v1/admin/users/${userId}/role`, request),
      {
        successMessage: i18next.t('admin.users.actions.updateRole.success'),
        errorMessage: i18next.t('admin.users.actions.updateRole.error')
      }
    );
  }

  updateAccountLockStatus(userId: number, locked: boolean) {
    return this.handleApiResponse(
      axiosInstance.put(`/v1/admin/users/${userId}/status/lock`, null, {
        params: { locked }
      }),
      {
        successMessage: i18next.t('admin.users.actions.updateAccount.lockSuccess'),
        errorMessage: i18next.t('admin.users.actions.updateAccount.error')
      }
    );
  }

  updateAccountEnabledStatus(userId: number, enabled: boolean) {
    return this.handleApiResponse(
      axiosInstance.put(`/v1/admin/users/${userId}/status/enable`, null, {
        params: { enabled }
      }),
      {
        successMessage: enabled
          ? i18next.t('admin.users.actions.updateStatus.enable')
          : i18next.t('admin.users.actions.updateStatus.disable'),
        errorMessage: i18next.t('admin.users.actions.updateStatus.error')
      }
    );
  }

  updateAccountExpiryStatus(userId: number, expired: boolean) {
    return this.handleApiResponse(
      axiosInstance.put(`/v1/admin/users/${userId}/status/expiry`, null, {
        params: { expired }
      }),
      {
        successMessage: i18next.t('admin.users.actions.updateAccount.expirySuccess'),
        errorMessage: i18next.t('admin.users.actions.updateAccount.error')
      }
    );
  }

  updateCredentialsExpiryStatus(userId: number, expired: boolean) {
    return this.handleApiResponse(
      axiosInstance.put(`/v1/admin/users/${userId}/credentials/expiry`, null, {
        params: { expired }
      }),
      {
        successMessage: i18next.t('admin.users.actions.updateAccount.credentialsSuccess'),
        errorMessage: i18next.t('admin.users.actions.updateAccount.error')
      }
    );
  }

  updatePassword(userId: number, newPassword: string) {
    return this.handleApiResponse(
      axiosInstance.put(`/v1/admin/users/${userId}/password`, {
        newPassword
      }),
      {
        successMessage: i18next.t('admin.users.actions.updatePassword.success'),
        errorMessage: i18next.t('admin.users.actions.updatePassword.error')
      }
    );
  }
}

export const adminService = new AdminService();