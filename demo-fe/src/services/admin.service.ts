import axiosInstance from "@/services/axios.config";

export const AdminService = {
  getAllUsers: () =>
    axiosInstance.get("/v1/admin/users"),

  getUser: (id: number) =>
    axiosInstance.get(`/v1/admin/users/${id}`),

  getAllRoles: () =>
    axiosInstance.get("/v1/admin/roles"),

  updateUserRole: (userId: number, request: { userId: number, roleName: string }) =>
    axiosInstance.put(`/v1/admin/users/${userId}/role`, request),

  updateAccountLockStatus: (userId: number, locked: boolean) =>
    axiosInstance.put(`/v1/admin/users/${userId}/status/lock`, null, {
      params: { locked }
    }),

  updateAccountExpiryStatus: (userId: number, expired: boolean) =>
    axiosInstance.put(`/v1/admin/users/${userId}/status/expiry`, null, {
      params: { expired }
    }),

  updateAccountEnabledStatus: (userId: number, enabled: boolean) =>
    axiosInstance.put(`/v1/admin/users/${userId}/status/enable`, null, {
      params: { enabled }
    }),

  updateCredentialsExpiryStatus: (userId: number, expired: boolean) =>
    axiosInstance.put(`/v1/admin/users/${userId}/credentials/expiry`, null, {
      params: { expired }
    }),

  updatePassword: (userId: number, newPassword: string) =>
    axiosInstance.put(`/v1/admin/users/${userId}/password`, {
      newPassword
    }),
};