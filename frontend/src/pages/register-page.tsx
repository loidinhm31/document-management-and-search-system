import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";

import LanguageSwitcher from "@/components/common/language-switcher";
import { ThemeToggle } from "@/components/common/theme-toggle";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { createSignupSchema, SignupFormValues } from "@/schemas/register-schema";
import { authService } from "@/services/auth.service";
import { SignupRequest } from "@/types/auth";

export default function RegisterPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { token } = useAuth();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState<boolean>(false);

  const form = useForm<SignupFormValues>({
    resolver: zodResolver(createSignupSchema(t)),
    mode: "onBlur",
    defaultValues: {
      username: "",
      email: "",
      password: "",
    },
  });

  useEffect(() => {
    // Get fields that have been touched by the user
    const touchedFields = Object.keys(form.formState.touchedFields);

    // Only trigger validation for fields the user has interacted with
    if (touchedFields.length > 0) {
      form.trigger(touchedFields as any);
    }
  }, [i18n.language]);

  useEffect(() => {
    if (token) {
      navigate("/");
    }
  }, [token, navigate]);

  const onSubmit = async (data: SignupFormValues) => {
    setIsLoading(true);
    try {
      const signupData: SignupRequest = {
        username: data.username,
        email: data.email,
        password: data.password,
      };
      await authService.register(signupData);

      toast({
        title: t("common.success"),
        description: t("auth.register.otpSent"),
        variant: "success",
      });

      // Redirect to OTP verification page after successful registration
      navigate("/verify-otp", {
        state: { username: data.username },
      });
    } catch (error: any) {
      console.log("error", error?.response);

      if (error?.response?.status === 500) {
        if (error?.response?.data === "USERNAME_EXISTS") {
          form.setError("username", {
            message: t("auth.register.validation.usernameExists"),
          });
        } else if (error?.response?.data === "EMAIL_EXISTS") {
          form.setError("email", {
            message: t("auth.register.validation.emailExists"),
          });
        }
      } else {
        toast({
          title: t("common.error"),
          description: t("auth.register.error"),
          variant: "destructive",
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="absolute right-4 top-4 flex items-center gap-2">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>

      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle>{t("auth.register.title")}</CardTitle>
          <CardDescription>{t("auth.register.description")}</CardDescription>
        </CardHeader>

        <CardContent>
          <div className="grid gap-6">
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                  control={form.control}
                  name="username"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("auth.register.form.username.label")}</FormLabel>
                      <FormControl>
                        <Input placeholder={t("auth.register.form.username.placeholder")} {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="email"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("auth.register.form.email.label")}</FormLabel>
                      <FormControl>
                        <Input placeholder={t("auth.register.form.email.placeholder")} type="email" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("auth.register.form.password.label")}</FormLabel>
                      <FormControl>
                        <div className="relative">
                          <Input
                            placeholder={t("auth.register.form.password.placeholder")}
                            type={showPassword ? "text" : "password"}
                            {...field}
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
                          </Button>
                        </div>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <Button type="submit" className="w-full" disabled={isLoading}>
                  {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  {t("auth.register.buttons.submit")}
                </Button>
              </form>
            </Form>

            <div className="text-center text-sm text-muted-foreground">
              {t("auth.register.login.prompt")}{" "}
              <Button variant="link" className="p-0" asChild>
                <Link to="/login">{t("auth.register.login.link")}</Link>
              </Button>
            </div>

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
  );
}
