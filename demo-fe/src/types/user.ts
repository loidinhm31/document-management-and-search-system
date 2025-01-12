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