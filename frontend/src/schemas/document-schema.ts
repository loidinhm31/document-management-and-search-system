import { TFunction } from "i18next";
import * as z from "zod";

// Type definition schema (without translations)
export const documentSchema = z.object({
  summary: z
    .string()
    .optional()
    .refine(
      (val) => !val || (val.length >= 50 && val.length <= 500),
      (val) => ({
        message:
          val && val.length < 50 ? "Summary must be at least 50 characters" : "Summary must not exceed 500 characters",
      }),
    ),
  majors: z.array(z.string()).min(1),
  courseCodes: z.array(z.string()).optional(),
  level: z.string().min(1),
  categories: z.array(z.string()).min(1),
  tags: z.array(z.string()).optional(),
});

export type DocumentFormValues = z.infer<typeof documentSchema>;

// Factory function to create schema with translations
export const createDocumentSchema = (t: TFunction) => {
  return z.object({
    summary: z
      .string()
      .optional()
      .refine(
        (val) => !val || (val.length >= 50 && val.length <= 500),
        (val) => ({
          message:
            val && val.length < 50
              ? t("document.upload.validation.summaryMinLength", "Summary must be at least 50 characters")
              : t("document.upload.validation.summaryMaxLength", "Summary must not exceed 500 characters"),
        }),
      ),
    majors: z.array(z.string()).min(1, t("document.upload.validation.majorRequired", "At least one major is required")),
    courseCodes: z.array(z.string()).optional(),
    level: z.string().min(1, t("document.upload.validation.levelRequired", "Course level is required")),
    categories: z
      .array(z.string())
      .min(1, t("document.upload.validation.categoryRequired", "At least one document category is required")),
    tags: z.array(z.string()).optional(),
  });
};
