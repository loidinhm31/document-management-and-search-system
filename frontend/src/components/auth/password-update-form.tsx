import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { createPasswordSchema, PasswordFormValues } from "@/schemas/password-update-schema";
import { userService } from "@/services/user.service";

export default function PasswordUpdateForm() {
  const { t, i18n } = useTranslation();
  const { currentUser, clearAuthData } = useAuth();
  const [loading, setLoading] = useState(false);
  const { toast } = useToast();
  const navigate = useNavigate();

  const form = useForm<PasswordFormValues>({
    resolver: zodResolver(createPasswordSchema(t)),
    mode: "onBlur",
    defaultValues: {
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
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

  const onSubmit = async (data: PasswordFormValues) => {
    if (!currentUser?.userId) return;

    setLoading(true);
    try {
      await userService.updatePassword(currentUser.userId, {
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      });

      toast({
        title: t("common.success"),
        description: t("profile.password.updateSuccess"),
        variant: "success",
      });

      form.reset();
      setTimeout(() => {
        clearAuthData();
        navigate("/login");
      }, 2000);
    } catch (error: any) {
      console.log("error", error?.response);

      // Error handling is done by the service
      if (error?.response?.status === 500) {
        if (error?.response?.data === "INCORRECT_PASSWORD") {
          form.setError("currentPassword", {
            message: t("profile.password.errors.incorrectCurrent"),
          });
        } else if (error?.response?.data === "DIFFERENT_PASSWORD") {
          form.setError("newPassword", {
            message: t("profile.password.errors.passwordNotTheSame"),
          });
        }
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          control={form.control}
          name="currentPassword"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("profile.password.fields.current")}</FormLabel>
              <FormControl>
                <Input
                  type="password"
                  placeholder={t("profile.password.placeholders.current")}
                  disabled={loading}
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="newPassword"
          render={({ field }) => (
            <FormItem>
              <FormLabel>{t("profile.password.fields.new")}</FormLabel>
              <FormControl>
                <Input
                  type="password"
                  placeholder={t("profile.password.placeholders.new")}
                  disabled={loading}
                  {...field}
                />
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
              <FormLabel>{t("profile.password.fields.confirm")}</FormLabel>
              <FormControl>
                <Input
                  type="password"
                  placeholder={t("profile.password.placeholders.confirm")}
                  disabled={loading}
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" disabled={loading}>
          {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
          {t("profile.password.actions.update")}
        </Button>
      </form>
    </Form>
  );
}
