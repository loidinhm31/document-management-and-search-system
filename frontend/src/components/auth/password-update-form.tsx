import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import * as z from "zod";

import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { userService } from "@/services/user.service";

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Current password is required"),
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
    message: "Passwords don't match",
    path: ["confirmPassword"],
  });

type PasswordFormValues = z.infer<typeof passwordSchema>;

export default function PasswordUpdateForm() {
  const { t } = useTranslation();
  const { currentUser } = useAuth();
  const [loading, setLoading] = useState(false);
  const { toast } = useToast();

  const form = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: {
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    },
  });

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
