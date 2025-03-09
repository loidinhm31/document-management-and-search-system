import { TFunction } from "i18next";
import * as z from "zod";

export const reportFormSchema = z.object({
  reportTypeCode: z.string(),
  description: z.string().optional(),
});

export type ReportFormValues = z.infer<typeof reportFormSchema>;

// Factory functions to create schemas with translations
export const createDocumentReportSchema = (t: TFunction) => {
  return z.object({
    reportTypeCode: z.string({
      required_error: t("document.report.form.type.required", "Please select a report type"),
    }),
    description: z.string().optional(),
  });
};

export const createCommentReportSchema = (t: TFunction) => {
  return z.object({
    reportTypeCode: z.string({
      required_error: t("document.comments.report.form.type.required", "Please select a report type"),
    }),
    description: z.string().optional(),
  });
};
