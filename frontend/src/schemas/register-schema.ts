import { TFunction } from "i18next";
import * as z from "zod";

export const signupSchema = z.object({
  username: z.string().min(3),
  email: z.string().email(),
  password: z
    .string()
    .min(6)
    .regex(/^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/),
});

export type SignupFormValues = z.infer<typeof signupSchema>;

// Factory function to create schema with translations
export const createSignupSchema = (t: TFunction) => {
  return z.object({
    username: z
      .string()
      .min(3, t("auth.register.validation.usernameMinLength", "Username must be at least 3 characters")),
    email: z.string().email(t("auth.register.validation.invalidEmail", "Invalid email address")),
    password: z
      .string()
      .min(6, t("auth.register.validation.passwordMinLength", "Password must be at least 6 characters"))
      .regex(
        /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/,
        t(
          "auth.register.validation.passwordPattern",
          "Password must contain at least one digit, lowercase, uppercase, and special character",
        ),
      ),
  });
};
