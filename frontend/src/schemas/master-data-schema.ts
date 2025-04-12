import { TFunction } from "i18next";
import * as z from "zod";

export const masterDataFormSchema = z.object({
  code: z.string().min(1),
  translations: z.object({
    en: z.string().min(1),
    vi: z.string().min(1),
  }),
  description: z.string().optional(),
  isActive: z.boolean(),
  parentId: z.string().optional(),
});

export type MasterDataFormValues = z.infer<typeof masterDataFormSchema>;

// Factory function to create schema with translations
export const createMasterDataFormSchema = (t: TFunction) => {
  return z.object({
    code: z.string().min(1, t("admin.masterData.validation.codeRequired", "Code is required")),
    translations: z.object({
      en: z.string().min(1, t("admin.masterData.validation.englishNameRequired", "English name is required")),
      vi: z.string().min(1, t("admin.masterData.validation.vietnameseNameRequired", "Vietnamese name is required")),
    }),
    description: z.string().optional(),
    isActive: z.boolean(),
    parentId: z.string().optional(),
  });
};
