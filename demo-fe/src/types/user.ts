import { ApiResponse } from "@/types/auth";

export interface GetUsersParams {
  search?: string;
  enabled?: boolean;
  role?: string;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export interface Role {
  roleId: number;
  roleName: string;
}

export interface UserData {
  userId: number;
  username: string;
  email: string;
  role: Role;
  accountNonLocked: boolean;
  accountNonExpired: boolean;
  credentialsNonExpired: boolean;
  enabled: boolean;
  credentialsExpiryDate: string;
  accountExpiryDate: string;
  twoFactorEnabled: boolean;
  signUpMethod: string;
  createdDate: string;
  updatedDate: string;
}

export interface TwoFactorResponse extends ApiResponse<string> {
  data: string;
}

export interface TwoFactorStatusResponse extends ApiResponse<boolean> {
  data: boolean;
}


export interface UpdateStatusRequest {
  accountLocked?: boolean;
  accountExpired?: boolean;
  credentialsExpired?: boolean;
  enabled?: boolean;
  credentialsExpiryDate?: string;
  accountExpiryDate?: string;
}
