import { TFunction } from "i18next";
import * as z from "zod";

export const loginSchema = z.object({
  username: z.string().min(1),
  password: z
    .string()
    .min(6)
    .regex(/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

// Factory function to create schema with translations
export const createLoginSchema = (t: TFunction) => {
  return z.object({
    username: z.string().min(1, t("auth.login.validation.usernameRequired")),
    password: z
      .string()
      .min(6, t("auth.login.validation.passwordMinLength", "Password must be at least 6 characters"))
      .regex(
        /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/,
        t(
          "auth.login.validation.passwordPattern",
          "Password must contain at least one digit, lowercase, uppercase, and special character",
        ),
      ),
  });
};
