import { zodResolver } from "@hookform/resolvers/zod";
import { Eye, EyeOff, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { FcGoogle } from "react-icons/fc";
import { Link, useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { OAUTH_GOOGLE_REDIRECT_URL } from "@/env";
import { useToast } from "@/hooks/use-toast";
import { createLoginSchema, LoginFormValues } from "@/schemas/login-schema";
import { authService } from "@/services/auth.service";
import { LoginRequest, TokenResponse } from "@/types/auth";

interface LoginFormProps {
  onSuccess: (responseData: TokenResponse) => void;
}

export const LoginForm = ({ onSuccess }: LoginFormProps) => {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState<boolean>(false);

  useEffect(() => {
    // Get fields that have been touched by the user
    const touchedFields = Object.keys(form.formState.touchedFields);

    // Only trigger validation for fields the user has interacted with
    if (touchedFields.length > 0) {
      form.trigger(touchedFields as any);
    }
  }, [i18n.language]);

  // Create the form with translated schema
  const form = useForm({
    resolver: zodResolver(createLoginSchema(t)),
    mode: "onBlur",
    defaultValues: {
      identifier: "",
      password: "",
    },
  });

  const onSubmit = async (data: LoginFormValues) => {
    setIsLoading(true);
    try {
      const response = await authService.login(data as LoginRequest);
      if (!response.data.enabled) {
        // Redirect to OTP verification if account is not enabled
        navigate("/verify-otp", {
          state: { username: data.username },
        });
        return;
      }

      onSuccess(response.data);
    } catch (error) {
      console.log("toast", error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <div className="grid gap-6">
          <Link
            to={`${OAUTH_GOOGLE_REDIRECT_URL}/oauth2/authorization/google`}
            className="flex items-center justify-center gap-1 rounded-md border p-2 shadow-sm shadow-slate-200 transition-all duration-300 hover:bg-slate-300"
          >
            <span>
              <FcGoogle className="text-2xl" />
            </span>
            <span className="text-xs font-semibold sm:text-customText">{t("auth.login.buttons.google")}</span>
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
              control={form.control}
              name="identifier"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("auth.login.form.identifier.label")}</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      type="text"
                      disabled={isLoading}
                      placeholder={t("auth.login.form.identifier.placeholder")}
                    />
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
                  <div className="flex items-center justify-between">
                    <FormLabel>{t("auth.login.form.password.label")}</FormLabel>
                    <Button variant="link" className="px-0 font-normal" asChild>
                      <Link to="/forgot-password">{t("auth.login.form.password.forgot")}</Link>
                    </Button>
                  </div>
                  <FormControl>
                    <div className="relative">
                      <Input
                        {...field}
                        type={showPassword ? "text" : "password"}
                        disabled={isLoading}
                        placeholder={t("auth.login.form.password.placeholder")}
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
          </div>

          <Button type="submit" className="w-full" disabled={isLoading}>
            {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("auth.login.buttons.signIn")}
          </Button>

          <p className="text-center text-sm text-muted-foreground">
            {t("auth.login.signup.prompt")}{" "}
            <Button variant="link" className="px-0 font-normal" asChild>
              <Link to="/register">{t("auth.login.signup.link")}</Link>
            </Button>
          </p>
        </div>
      </form>
    </Form>
  );
};
