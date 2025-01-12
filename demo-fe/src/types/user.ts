import { ApiResponse } from "@/types/auth";

export interface Role {
  roleId: number;
  roleName: string;
}

export interface UserData {
  userId: number;
  userName: string;
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