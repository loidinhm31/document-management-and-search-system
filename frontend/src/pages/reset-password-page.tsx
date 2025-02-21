import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import * as z from "zod";

import LanguageSwitcher from "@/components/common/language-switcher";
import { ThemeToggle } from "@/components/common/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { authService } from "@/services/auth.service";

// Create schema for password reset form with validation
const resetPasswordSchema = z
  .object({
    newPassword: z
      .string()
      .min(6, "Password must be at least 6 characters")
      .regex(
        /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/,
        "Password must contain at least one digit, lowercase, uppercase, and special character",
      ),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

export default function ResetPasswordPage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { token: authToken } = useAuth();
  const [isLoading, setIsLoading] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [tokenValid, setTokenValid] = useState<boolean>(true);
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState<boolean>(false);

  // Redirect to home if already logged in
  useEffect(() => {
    if (authToken) {
      navigate("/");
    }
  }, [authToken, navigate]);

  // Initialize form with react-hook-form and zod validation
  const form = useForm<ResetPasswordFormValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      newPassword: "",
      confirmPassword: "",
    },
  });

  // Extract token from URL when component mounts
  useEffect(() => {
    const tokenFromUrl = searchParams.get("token");
    if (!tokenFromUrl) {
      setTokenValid(false);
      toast({
        title: t("common.error"),
        description: t("auth.resetPassword.invalidToken"),
        variant: "destructive",
      });
    } else {
      setToken(tokenFromUrl);
    }
  }, [searchParams, toast, t]);

  // Handle form submission
  const onSubmit = async (data: ResetPasswordFormValues) => {
    if (!token) {
      toast({
        title: t("common.error"),
        description: t("auth.resetPassword.invalidToken"),
        variant: "destructive",
      });
      return;
    }

    setIsLoading(true);
    try {
      await authService.resetPassword(token, data.newPassword);

      toast({
        title: t("common.success"),
        description: t("auth.resetPassword.success"),
        variant: "success",
      });

      // Redirect to login page after successful password reset
      setTimeout(() => {
        navigate("/login");
      }, 1500);
    } catch (error) {
      console.log("error", error?.response);

      if (error?.response?.status === 500) {
        if (error?.response?.data === "INVALID_PASSWORD_RESET_TOKEN") {
          toast({
            title: t("common.error"),
            description: t("auth.resetPassword.validation.invalidPasswordResetToken"),
            variant: "destructive",
          });
        } else if (error?.response?.data === "PASSWORD_RESET_TOKEN_USED") {
          toast({
            title: t("common.error"),
            description: t("auth.resetPassword.validation.passwordResetTokenUsed"),
            variant: "destructive",
          });
        } else if (error?.response?.data === "PASSWORD_RESET_TOKEN_EXPIRED") {
          toast({
            title: t("common.error"),
            description: t("auth.resetPassword.validation.passwordResetTokenExpired"),
            variant: "destructive",
          });
        }
        setTimeout(() => {
          navigate("/login");
        }, 200);
      } else {
        toast({
          title: t("common.error"),
          description: t("auth.resetPassword.error"),
          variant: "destructive",
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  if (!tokenValid) {
    return (
      <div className="flex min-h-screen items-center justify-center p-4">
        <div className="absolute top-4 right-4 flex items-center gap-2">
          <LanguageSwitcher />
          <ThemeToggle />
        </div>
        <Card className="w-full max-w-md">
          <CardHeader className="text-center">
            <CardTitle>{t("auth.resetPassword.invalidTitle")}</CardTitle>
            <CardDescription>{t("auth.resetPassword.invalidDescription")}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button asChild className="w-full">
              <Link to="/login">{t("auth.resetPassword.backToLogin")}</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center p-4">
      <div className="absolute top-4 right-4 flex items-center gap-2">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>

      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle>{t("auth.resetPassword.title")}</CardTitle>
          <CardDescription>{t("auth.resetPassword.description")}</CardDescription>
        </CardHeader>

        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
              <FormField
                control={form.control}
                name="newPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("auth.resetPassword.form.password.label")}</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Input
                          {...field}
                          type={showPassword ? "text" : "password"}
                          placeholder={t("auth.resetPassword.form.password.placeholder")}
                          disabled={isLoading}
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="absolute right-1 top-1 h-8 w-8 p-0"
                          onClick={() => setShowPassword(!showPassword)}
                          tabIndex={-1}
                        >
                          {showPassword ? (
                            <EyeOff className="h-4 w-4" aria-hidden="true" />
                          ) : (
                            <Eye className="h-4 w-4" aria-hidden="true" />
                          )}
                          <span className="sr-only">
                            {showPassword
                              ? t("auth.resetPassword.form.password.hide")
                              : t("auth.resetPassword.form.password.show")}
                          </span>
                        </Button>
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="confirmPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("auth.resetPassword.form.confirm.label")}</FormLabel>
                    <FormControl>
                      <div className="relative">
                        <Input
                          {...field}
                          type={showConfirmPassword ? "text" : "password"}
                          placeholder={t("auth.resetPassword.form.confirm.placeholder")}
                          disabled={isLoading}
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          className="absolute right-1 top-1 h-8 w-8 p-0"
                          onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                          tabIndex={-1}
                        >
                          {showConfirmPassword ? (
                            <EyeOff className="h-4 w-4" aria-hidden="true" />
                          ) : (
                            <Eye className="h-4 w-4" aria-hidden="true" />
                          )}
                          <span className="sr-only">
                            {showConfirmPassword
                              ? t("auth.resetPassword.form.confirm.hide")
                              : t("auth.resetPassword.form.confirm.show")}
                          </span>
                        </Button>
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <Button type="submit" className="w-full" disabled={isLoading}>
                {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                {t("auth.resetPassword.buttons.submit")}
              </Button>

              <div className="text-center text-sm">
                <Button variant="link" asChild>
                  <Link to="/login">{t("auth.resetPassword.backToLogin")}</Link>
                </Button>
              </div>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}
