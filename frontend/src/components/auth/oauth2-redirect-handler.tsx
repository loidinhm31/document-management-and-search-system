import { jwtDecode } from "jwt-decode";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";

import { TwoFactorForm } from "@/components/auth/two-factor-form";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { JwtPayload, TokenResponse } from "@/types/auth";
import LanguageSwitcher from "@/components/common/language-switcher";
import { ThemeToggle } from "@/components/common/theme-toggle";

export default function OAuth2RedirectHandler() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { setAuthData } = useAuth();
  const [tokenResponse, setTokenResponse] = useState<TokenResponse | null>();
  const [requires2FA, setRequires2FA] = useState(false);
  const [username, setUsername] = useState<string | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const token = params.get("token");
    const refreshToken = params.get("refreshToken");

    if (token) {
      try {
        const decodedToken = jwtDecode<JwtPayload>(token);

        const tokenAuth = {
          accessToken: token,
          refreshToken: refreshToken,
          tokenType: "Bearer",
          username: decodedToken.sub,
          roles: decodedToken.roles,
        };
        setTokenResponse(tokenAuth);

        if (decodedToken.is2faEnabled) {
          setRequires2FA(true);
          setUsername(decodedToken.sub);
        } else {
          handleSuccessfulLogin(tokenAuth);
        }
      } catch (error) {
        console.info("Token decoding failed:", error);
        navigate("/login");
      }
    } else {
      navigate("/login");
    }
  }, [location, navigate]);

  const handleSuccessfulLogin = (tokenAuth: TokenResponse) => {
    setAuthData(tokenAuth);

    toast({
      title: t("common.success"),
      description: t("auth.login.success"),
      variant: "success",
    });

    navigate("/");
  };

  const handle2FASuccess = () => {
    handleSuccessfulLogin(tokenResponse);
  };

  if (!requires2FA || !username) {
    return <div>Redirecting...</div>;
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4 sm:p-8">
      <div className="absolute right-4 top-4 flex items-center gap-2">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>
      <div className="w-full max-w-md space-y-6">
        <TwoFactorForm username={username} onSuccess={handle2FASuccess} />
      </div>
    </div>
  );
}
