import i18next from "i18next";

import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { User } from "@/types/auth";
import { GetUsersRequest, PageResponse, Role, UpdateStatusRequest, UserData } from "@/types/user";

class AdminService extends BaseService {
  getAllUsers(usersRequest: GetUsersRequest = {}) {
    return this.handleApiResponse<PageResponse<User>>(
      axiosInstance.post("/auth/api/v1/admin/users", usersRequest),
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
