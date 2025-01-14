export interface User {
  userId: string;
  username: string;
  email: string;
  accountNonLocked: boolean;
  accountNonExpired: boolean;
  credentialsNonExpired: boolean;
  enabled: boolean;
  credentialsExpiryDate: string;
  accountExpiryDate: string;
  twoFactorEnabled: boolean;
  signUpMethod: "email" | "google";
  roles: string[];
  createdDate: string;
  updatedDate: string;
  imageUrl?: string;
}

export interface JwtPayload {
  sub: string;
  roles: string;
  is2faEnabled?: boolean;
  exp: number;
  iat: number;
}

export interface UpdateCredentialsRequest {
  username?: string;
  password?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface SignupRequest {
  username: string;
  email: string;
  password: string;
  role?: string[];
}

export interface LoginResponse {
  jwtToken: string;
  username: string;
  roles: string[];
}

export interface UpdatePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: {
    status: number;
    message: string;
    details?: string;
  };
  timestamp: string;
}
