import { TFunction } from "i18next";
import * as z from "zod";

export const resetPasswordSchema = z
  .object({
    newPassword: z
      .string()
      .min(6)
      .regex(/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@!#$%^&+=*]).*$/),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;

// Factory function to create schema with translations
export const createResetPasswordSchema = (t: TFunction) => {
  return z
    .object({
      newPassword: z
        .string()
        .min(6, t("auth.resetPassword.validation.passwordMinLength", "Password must be at least 6 characters"))
        .regex(
          /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@!#$%^&+=*]).*$/,
          t(
            "auth.resetPassword.validation.passwordPattern",
            "Password must contain at least one digit, lowercase, uppercase, and special character",
          ),
        ),
      confirmPassword: z.string(),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
      message: t("auth.resetPassword.validation.passwordMismatch", "Passwords do not match"),
      path: ["confirmPassword"],
    });
};
