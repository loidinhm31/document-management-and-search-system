import { jwtDecode } from "jwt-decode";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { LoginForm } from "@/components/auth/login-form";
import { TwoFactorForm } from "@/components/auth/two-factor-form";
import LanguageSwitcher from "@/components/common/language-switcher";
import { ThemeToggle } from "@/components/common/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { JwtPayload, TokenResponse } from "@/types/auth";

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { token, setAuthData } = useAuth();
  const { toast } = useToast();

  const [step, setStep] = useState(1);
  const [username, setUsername] = useState("");
  const [tokenResponse, setTokenResponse] = useState<TokenResponse>();

  // Redirect to home if already logged in
  useEffect(() => {
    if (token) navigate("/");
  }, [navigate, token]);

  const handleLoginSuccess = (responseData: TokenResponse) => {
    setUsername(responseData.username);
    setTokenResponse(responseData);
    const decodedToken = jwtDecode<JwtPayload>(responseData.accessToken);
    if (decodedToken.is2faEnabled) {
      setStep(2);
    } else {
      handleSuccessfulLogin(responseData);
    }
  };

  const handleSuccessfulLogin = (token: TokenResponse) => {
    // Update auth context
    setAuthData(token);

    toast({
      title: t("common.success"),
      description: t("auth.login.success"),
      variant: "success",
    });
  };

  const handle2FASuccess = () => {
    handleSuccessfulLogin(tokenResponse);
    navigate("/");
  };

  return (
    <div className="flex min-h-screen items-center justify-center p-4 sm:p-8">
      <div className="absolute right-4 top-4 flex items-center gap-2">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>

      {step === 1 ? (
        <div className="w-full max-w-md space-y-6">
          <Card>
            <CardHeader className="text-center">
              <CardTitle>{t("auth.login.title")}</CardTitle>
              <CardDescription>{t("auth.login.description")}</CardDescription>
            </CardHeader>

            <CardContent>
              <div className="grid gap-6">
                <LoginForm onSuccess={handleLoginSuccess} />
                <p className="text-center text-xs text-muted-foreground">
                  {t("legal.consent")}{" "}
                  <Button variant="link" className="h-auto p-0 text-xs font-normal">
                    {t("legal.tos")}
                  </Button>{" "}
                  {t("common.and")}{" "}
                  <Button variant="link" className="h-auto p-0 text-xs font-normal">
                    {t("legal.privacy")}
                  </Button>
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : (
        <TwoFactorForm username={username} onSuccess={handle2FASuccess} />
      )}
    </div>
  );
}
