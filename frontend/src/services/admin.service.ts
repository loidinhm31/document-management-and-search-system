import i18next from "i18next";

import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { User } from "@/types/auth";
import { GetUsersParams, PageResponse, Role, UpdateStatusRequest, UserData } from "@/types/user";

class AdminService extends BaseService {
  getAllUsers(params: GetUsersParams = {}) {
    return this.handleApiResponse<PageResponse<User>>(
      axiosInstance.get("/auth/api/v1/admin/users", {
        params: {
          search: params.search,
          enabled: params.enabled,
          role: params.role,
          page: params.page || 0,
          size: params.size || 10,
        },
      }),
    );
  }

  getUser(id: string) {
    return this.handleApiResponse<UserData>(axiosInstance.get(`/auth/api/v1/users/${id}`));
  }

  getAllRoles() {
    return this.handleApiResponse<Role[]>(axiosInstance.get("/auth/api/v1/admin/roles"));
  }

  updateUserRole(userId: string, request: { userId: string; roleName: string }) {
    return this.handleApiResponse(axiosInstance.put(`/auth/api/v1/users/${userId}/role`, request), {
      successMessage: i18next.t("admin.users.actions.updateRole.success"),
      errorMessage: i18next.t("admin.users.actions.updateRole.error"),
    });
  }

  updateStatus(userId: string, request: UpdateStatusRequest) {
    return this.handleApiResponse(axiosInstance.put(`/auth/api/v1/users/${userId}/status`, request), {
      successMessage: i18next.t("admin.users.actions.updateAccount.success"),
      errorMessage: i18next.t("admin.users.actions.updateAccount.error"),
    });
  }
}

export const adminService = new AdminService();
