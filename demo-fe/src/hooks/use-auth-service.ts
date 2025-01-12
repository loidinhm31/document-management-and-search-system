import { useCallback } from "react";
import { authService } from "@/services/auth.service";
import { LoginRequest, SignupRequest } from "@/types/auth";

export const useAuthService = () => {
  const login = useCallback(async (data: LoginRequest) => {
    return authService.login(data);
  }, []);

  const register = useCallback(async (data: SignupRequest) => {
    return authService.register(data);
  }, []);

  const forgotPassword = useCallback(async (email: string) => {
    return authService.forgotPassword(email);
  }, []);

  const resetPassword = useCallback(async (token: string, newPassword: string) => {
    return authService.resetPassword(token, newPassword);
  }, []);

  const verify2FA = useCallback(async (code: string, jwtToken?: string) => {
    return authService.verify2FA(code, jwtToken);
  }, []);

  const enable2FA = useCallback(async () => {
    return authService.enable2FA();
  }, []);

  const disable2FA = useCallback(async () => {
    return authService.disable2FA();
  }, []);

  const get2FAStatus = useCallback(async () => {
    return authService.get2FAStatus();
  }, []);

  const getCurrentUser = useCallback(async () => {
    return authService.getCurrentUser();
  }, []);

  return {
    login,
    register,
    forgotPassword,
    resetPassword,
    verify2FA,
    enable2FA,
    disable2FA,
    get2FAStatus,
    getCurrentUser,
  };
};
