import { jwtDecode } from "jwt-decode";
import React from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";

import OtpVerificationForm from "@/components/auth/otp-verification-form";
import LanguageSwitcher from "@/components/common/language-switcher";
import { ThemeToggle } from "@/components/common/theme-toggle";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { authService } from "@/services/auth.service";
import { JwtPayload } from "@/types/auth";

export default function OtpVerificationPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { toast } = useToast();
  const { setAuthData, setRole } = useAuth();

  // Get email from location state or query params
  const username = location.state?.username || new URLSearchParams(location.search).get("username");

  if (!username) {
    // Redirect to login if no email is provided
    navigate("/login");
    return null;
  }

  const handleVerification = async (otp: string) => {
    try {
      const response = await authService.verifyOtp({
        username,
        otp,
      });

      if (response.data.verified) {
        toast({
          title: t("common.success"),
          description: t("auth.otp.verifySuccess"),
          variant: "success",
        });

        const decodedToken = jwtDecode<JwtPayload>(response.data.accessToken);

        const user = {
          username: decodedToken.sub,
          roles: decodedToken.roles.split(","),
        };

        // Update auth context
        setAuthData(response.data);
        setRole(user.roles[0]);

        navigate("/");
      }
      return response;
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("auth.otp.failure"),
        variant: "destructive",
      });
    }
  };

  const handleResend = async () => {
    try {
      await authService.resendOtp({ username });
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("auth.otp.resendError"),
        variant: "destructive",
      });
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="absolute right-4 top-4 flex items-center gap-2">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>

      <div className="w-full max-w-md space-y-6">
        <OtpVerificationForm onVerified={handleVerification} onResend={handleResend} />
      </div>
    </div>
  );
}