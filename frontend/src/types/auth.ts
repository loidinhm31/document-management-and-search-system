export interface User {
  userId: string;
  username: string;
  email: string;
  accountNonLocked: boolean;
  enabled: boolean;
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

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  username: string;
  roles: string[];
  enabled: boolean;
  otpCount: number;
  locked: boolean;
  verified: boolean;
}

export interface RefreshTokenRequest {
  refreshToken: string;
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
