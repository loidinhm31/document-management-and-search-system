import { jwtDecode } from "jwt-decode";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLocation, useNavigate } from "react-router-dom";

import { TwoFactorForm } from "@/components/auth/two-factor-form";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { JwtPayload, TokenResponse } from "@/types/auth";

export default function OAuth2RedirectHandler() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { setAuthData, setRole } = useAuth();
  const [tokenResponse, setTokenResponse] = useState<TokenResponse | null>();
  const [requires2FA, setRequires2FA] = useState(false);
  const [username, setUsername] = useState<string | null>(null);
  const [tempToken, setTempToken] = useState<string | null>(null);
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
          roles: decodedToken.roles.split(",")
        };
        setTokenResponse(tokenAuth);

        if (decodedToken.is2faEnabled) {
          setRequires2FA(true);
          setUsername(decodedToken.sub);
          setTempToken(token);
        } else {
          handleSuccessfulLogin(tokenAuth, decodedToken);
        }
      } catch (error) {
        console.error("Token decoding failed:", error);
        navigate("/login");
      }
    } else {
      navigate("/login");
    }
  }, [location, navigate]);

  const handleSuccessfulLogin = (tokenAuth: TokenResponse, decodedToken: JwtPayload) => {
    setAuthData(tokenAuth);
    setRole(decodedToken.roles[0]);

    toast({
      title: t("common.success"),
      description: t("auth.login.success"),
      variant: "success"
    });

    navigate("/");
  };

  const handle2FASuccess = () => {
    const decodedToken = jwtDecode<JwtPayload>(tempToken);
    handleSuccessfulLogin(tokenResponse, decodedToken);
  };

  if (!requires2FA || !username) {
    return <div>Redirecting...</div>;
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <TwoFactorForm username={username} onSuccess={handle2FASuccess} />
    </div>
  );
}
