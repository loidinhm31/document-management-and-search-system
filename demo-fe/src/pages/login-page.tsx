import { jwtDecode } from "jwt-decode";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { LoginForm } from "@/components/auth/login-form";
import { TwoFactorForm } from "@/components/auth/two-factor-form";
import LanguageSwitcher from "@/components/language-switcher";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { JwtPayload, LoginResponse } from "@/types/auth";

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { token, setToken, setIsAdmin } = useAuth();
  const { toast } = useToast();

  const [step, setStep] = useState(1);
  const [jwtToken, setJwtToken] = useState("");
  const [username, setUsername] = useState("");

  // Redirect to home if already logged in
  if (token) {
    navigate("/");
    return null;
  }

  const handleLoginSuccess = (responseData: LoginResponse) => {
    setUsername(responseData.username);
    const decodedToken = jwtDecode<JwtPayload>(responseData.jwtToken);
    if (decodedToken.is2faEnabled) {
      setJwtToken(responseData.jwtToken);
      setStep(2);
    } else {
      handleSuccessfulLogin(responseData.jwtToken, decodedToken);
    }
  };

  const handleSuccessfulLogin = (token: string, decodedToken: JwtPayload) => {
    const user = {
      username: decodedToken.sub,
      roles: decodedToken.roles.split(","),
    };

    // Store tokens and user info
    localStorage.setItem("JWT_TOKEN", token);
    localStorage.setItem("USER", JSON.stringify(user));

    // Update auth context
    setToken(token);
    setIsAdmin(user.roles.includes("ROLE_ADMIN"));

    toast({
      title: t("common.success"),
      description: t("auth.login.success"),
      variant: "success",
    });

    navigate("/");
  };

  const handle2FASuccess = () => {
    const decodedToken = jwtDecode<JwtPayload>(jwtToken);
    handleSuccessfulLogin(jwtToken, decodedToken);
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
              <CardDescription>
                {t("auth.login.description")}
              </CardDescription>
            </CardHeader>

            <CardContent>
              <LoginForm onSuccess={handleLoginSuccess} />
            </CardContent>
          </Card>

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
      ) : (
        <TwoFactorForm username={username} onSuccess={handle2FASuccess} />
      )}
    </div>
  );
}
