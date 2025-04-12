import { TFunction } from "i18next";
import * as z from "zod";

export const twoFactorSchema = z.object({
  code: z.string().min(6).max(6),
});

export type TwoFactorFormValues = z.infer<typeof twoFactorSchema>;

// Factory function to create schema with translations
export const createTwoFactorSchema = (t: TFunction) => {
  return z.object({
    code: z
      .string()
      .min(6, t("auth.login.2fa.validation.codeLength", "Code must be 6 digits"))
      .max(6, t("auth.login.2fa.validation.codeLength", "Code must be 6 digits")),
  });
};
