import { TFunction } from "i18next";
import * as z from "zod";

// Type definition schema (without translations)
export const passwordSchema = z
  .object({
    currentPassword: z.string().min(1),
    newPassword: z
      .string()
      .min(6)
      .regex(/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords don't match",
    path: ["confirmPassword"],
  });

export type PasswordFormValues = z.infer<typeof passwordSchema>;

// Factory function to create schema with translations
export const createPasswordSchema = (t: TFunction) => {
  return z
    .object({
      currentPassword: z
        .string()
        .min(1, t("profile.password.validation.currentRequired", "Current password is required")),
      newPassword: z
        .string()
        .min(6, t("profile.password.validation.passwordMinLength", "Password must be at least 6 characters"))
        .regex(
          /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/,
          t(
            "profile.password.validation.passwordPattern",
            "Password must contain at least one digit, lowercase, uppercase, and special character",
          ),
        ),
      confirmPassword: z.string(),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
      message: t("profile.password.validation.passwordMismatch", "Passwords don't match"),
      path: ["confirmPassword"],
    });
};
