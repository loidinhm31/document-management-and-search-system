import { zodResolver } from "@hookform/resolvers/zod";
import { jwtDecode } from "jwt-decode";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { FcGoogle } from "react-icons/fc";
import { Link, useNavigate } from "react-router-dom";
import * as z from "zod";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/context/auth-context";
import { APP_API_URL } from "@/env";
import { useToast } from "@/hooks/use-toast";
import { LoginRequest, LoginResponse } from "@/types/auth";
import LanguageSwitcher from "@/components/language-switcher";
import { authService } from "@/services/auth.service";
import { userService } from "@/services/user.service";
import { ThemeToggle } from "@/components/theme-toggle";

const loginSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z
    .string()
    .min(6, "Password must be at least 6 characters")
    .regex(
      /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/,
      "Password must contain at least one digit, lowercase, uppercase, and special character",
    ),
});

// Define schema for 2FA verification form
const twoFactorSchema = z.object({
  code: z.string().min(6, "Code must be 6 digits").max(6),
});

type LoginFormValues = z.infer<typeof loginSchema>;
type TwoFactorFormValues = z.infer<typeof twoFactorSchema>;

const LoginPage = () => {
  const { t } = useTranslation();

  const navigate = useNavigate();
  const { setToken } = useAuth();
  const { toast } = useToast();

  const [step, setStep] = useState(1);
  const [jwtToken, setJwtToken] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  // Initialize login form
  const loginForm = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: "",
      password: "",
    },
  });

  // Initialize 2FA form
  const twoFactorForm = useForm<TwoFactorFormValues>({
    resolver: zodResolver(twoFactorSchema),
    defaultValues: {
      code: "",
    },
  });

  // Handle successful login
  const handleSuccessfulLogin = (token: string, decodedToken: any) => {
    const user = {
      username: decodedToken.sub,
      roles: decodedToken.roles ? decodedToken.roles.split(",") : [],
    };

    // Store tokens and user info
    localStorage.setItem("JWT_TOKEN", token);
    localStorage.setItem("USER", JSON.stringify(user));

    // Update auth context
    setToken(token);

    toast({
      title: t("common.success"),
      description: t("auth.login.success"),
      variant: "success",
    });

    navigate("/");
  };

  const onLoginSubmit = async (data: LoginFormValues) => {
    setIsLoading(true);
    try {
      const response = await authService.login(data as LoginRequest);
      const responseData = response.data.data as LoginResponse;

      if (responseData.jwtToken) {
        const decodedToken = jwtDecode<any>(responseData.jwtToken);
        if (decodedToken.is2faEnabled) {
          setJwtToken(responseData.jwtToken);
          setStep(2);
        } else {
          handleSuccessfulLogin(responseData.jwtToken, decodedToken);
        }
      }
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("auth.login.error"),
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const onTwoFactorSubmit = async (data: TwoFactorFormValues) => {
    setIsLoading(true);
    try {
      await userService.verify2FA(data.code);
      const decodedToken = jwtDecode<any>(jwtToken);
      handleSuccessfulLogin(jwtToken, decodedToken);
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("auth.login.2fa.error"),
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center p-4 sm:p-8">
      <div className="absolute top-4 right-4 flex items-center gap-2">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>
      <div className="w-full max-w-md space-y-6">
        <Card>
          <CardHeader className="text-center">
            <CardTitle>{t(step === 1 ? "auth.login.title" : "auth.login.2fa.title")}</CardTitle>
            <CardDescription>{t(step === 1 ? "auth.login.description" : "auth.login.2fa.description")}</CardDescription>
          </CardHeader>
          <CardContent>
            {step === 1 ? (
              <Form {...loginForm}>
                <form onSubmit={loginForm.handleSubmit(onLoginSubmit)} className="space-y-6">
                  <div className="grid gap-6">
                    <Link
                      to={`${APP_API_URL}/oauth2/authorization/google`}
                      className="flex gap-1 items-center justify-center flex-1 border p-2 shadow-sm shadow-slate-200 rounded-md hover:bg-slate-300 transition-all duration-300"
                    >
                      <span>
                        <FcGoogle className="text-2xl" />
                      </span>
                      <span className="font-semibold sm:text-customText text-xs">{t("auth.login.buttons.google")}</span>
                    </Link>

                    <div className="relative">
                      <div className="absolute inset-0 flex items-center">
                        <span className="w-full border-t" />
                      </div>
                      <div className="relative flex justify-center text-xs uppercase">
                        <span className="bg-background px-2 text-muted-foreground">{t("auth.login.form.or")}</span>
                      </div>
                    </div>

                    <div className="grid gap-4">
                      <FormField
                        control={loginForm.control}
                        name="username"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>{t("auth.login.form.username.label")}</FormLabel>
                            <FormControl>
                              <Input
                                {...field}
                                type="text"
                                disabled={isLoading}
                                placeholder={t("auth.login.form.username.placeholder")}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={loginForm.control}
                        name="password"
                        render={({ field }) => (
                          <FormItem>
                            <div className="flex items-center justify-between">
                              <FormLabel>{t("auth.login.form.password.label")}</FormLabel>
                              <Button
                                variant="link"
                                className="px-0 font-normal"
                                onClick={() => navigate("/forgot-password")}
                                type="button"
                              >
                                {t("auth.login.form.password.forgot")}
                              </Button>
                            </div>
                            <FormControl>
                              <Input
                                {...field}
                                type="password"
                                disabled={isLoading}
                                placeholder={t("auth.login.form.password.placeholder")}
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>

                    <Button type="submit" className="w-full" disabled={isLoading}>
                      {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                      {t("auth.login.buttons.signIn")}
                    </Button>

                    <p className="text-center text-sm text-muted-foreground">
                      {t("auth.login.signup.prompt")}{" "}
                      <Button
                        variant="link"
                        className="px-0 font-normal"
                        onClick={() => navigate("/register")}
                        type="button"
                      >
                        {t("auth.login.signup.link")}
                      </Button>
                    </p>
                  </div>
                </form>
              </Form>
            ) : (
              <Form {...twoFactorForm}>
                <form onSubmit={twoFactorForm.handleSubmit(onTwoFactorSubmit)} className="space-y-6">
                  <FormField
                    control={twoFactorForm.control}
                    name="code"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("auth.login.form.verificationCode.label")}</FormLabel>
                        <FormControl>
                          <Input
                            {...field}
                            type="text"
                            disabled={isLoading}
                            placeholder={t("auth.login.form.verificationCode.placeholder")}
                            maxLength={6}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <Button type="submit" className="w-full" disabled={isLoading}>
                    {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    {t("auth.login.buttons.verify")}
                  </Button>
                </form>
              </Form>
            )}
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
    </div>
  );
};

export default LoginPage;
