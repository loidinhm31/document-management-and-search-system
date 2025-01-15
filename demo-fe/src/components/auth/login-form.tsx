import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { FcGoogle } from "react-icons/fc";
import { Link } from "react-router-dom";
import * as z from "zod";

import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { APP_API_URL } from "@/env";
import { useToast } from "@/hooks/use-toast";
import { authService } from "@/services/auth.service";
import { LoginRequest, TokenResponse } from "@/types/auth";

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

type LoginFormValues = z.infer<typeof loginSchema>;

interface LoginFormProps {
  onSuccess: (responseData: TokenResponse) => void;
}

export const LoginForm = ({ onSuccess }: LoginFormProps) => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: "",
      password: "",
    },
  });

  const onSubmit = async (data: LoginFormValues) => {
    setIsLoading(true);
    try {
      const response = await authService.login(data as LoginRequest);
      console.log("onSuccess", response.data.data);
      onSuccess(response.data.data);
    } catch (error) {
      console.log("toast", error);
      toast({
        title: t("common.error"),
        description: t("auth.login.error"),
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
        <div className="grid gap-6">
          <Link
            to={`${APP_API_URL}/oauth2/authorization/google`}
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
            <Button variant="link" className="px-0 font-normal" asChild>
              <Link to="/register">{t("auth.login.signup.link")}</Link>
            </Button>
          </p>
        </div>
      </form>
    </Form>
  );
};
