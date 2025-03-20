import { TFunction } from "i18next";
import * as z from "zod";

export const forgotPasswordSchema = z.object({
  email: z.string().email(),
});

export type ForgotPasswordFormValues = z.infer<typeof forgotPasswordSchema>;

// Factory function to create schema with translations
export const createForgotPasswordSchema = (t: TFunction) => {
  return z.object({
    email: z.string().email(t("auth.forgotPassword.validation.invalidEmail", "Invalid email address")),
  });
};
