import { zodResolver } from "@hookform/resolvers/zod";
import { Flag, Loader2 } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";
import { createDocumentReportSchema, ReportFormValues } from "@/schemas/report-schemas";
import { documentReportService } from "@/services/document-report.service";
import { reportService } from "@/services/report.service";
import { CreateReportRequest, DocumentReport, ReportType } from "@/types/document-report";

interface ReportDialogProps {
  documentId: string;
  documentName: string;
  iconOnly?: boolean;
}

export function ReportDocumentDialog({ documentId, documentName, iconOnly = false }: ReportDialogProps) {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();

  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [reportTypes, setReportTypes] = useState<ReportType[]>([]);
  const [existingReport, setExistingReport] = useState<DocumentReport | null>(null);

  const form = useForm<ReportFormValues>({
    resolver: zodResolver(createDocumentReportSchema(t)),
  });

  useEffect(() => {
    // Get fields that have been touched by the user
    const touchedFields = Object.keys(form.formState.touchedFields);

    // Only trigger validation for fields the user has interacted with
    if (touchedFields.length > 0) {
      form.trigger(touchedFields as any);
    }
  }, [i18n.language]);

  useEffect(() => {
    checkExistingReport();
  }, []);

  useEffect(() => {
    if (open) {
      loadReportTypes();
    }
  }, [open]);

  const loadReportTypes = async () => {
    try {
      const response = await reportService.getDocumentReportTypes();
      setReportTypes(response.data);
    } catch (error) {
      console.error("Error loading report types:", error);
    }
  };

  const checkExistingReport = async () => {
    try {
      const response = await documentReportService.getDocumentUserReport(documentId);
      setExistingReport(response.data);
    } catch (error) {
      if (error.response?.status !== 404) {
        console.error("Error checking existing report:", error);
      }
    }
  };

  const onSubmit = async (values: ReportFormValues) => {
    setLoading(true);
    try {
      const request: CreateReportRequest = {
        reportTypeCode: values.reportTypeCode,
        description: values.description,
      };

      const response = await documentReportService.createDocumentReport(documentId, request);
      setExistingReport(response.data);

      toast({
        title: t("common.success"),
        description: t("document.report.createSuccess"),
        variant: "success",
      });
      setOpen(false);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.report.createError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger asChild>
          <Button variant="outline" size="sm" className="flex items-center gap-2 px-4">
            <Flag className={existingReport ? "h-4 w-4 text-orange-400 fill-orange-400" : "h-4 w-4"} />
            {!iconOnly && (existingReport ? t("document.actions.reported") : t("document.actions.report"))}
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {existingReport ? t("document.report.alreadyReported.title") : t("document.report.title")}
            </DialogTitle>
            <DialogDescription>{documentName}</DialogDescription>
          </DialogHeader>

          {existingReport ? (
            <div className="grid gap-4 mt-2">
              <div className="grid gap-2">
                <h4 className="font-medium">{t("document.report.type")}</h4>
                <p className="text-sm text-muted-foreground">
                  {existingReport.reportTypeTranslation[i18n.language] || existingReport.reportTypeTranslation.en}
                </p>
              </div>

              {existingReport.description && (
                <div className="grid gap-2">
                  <h4 className="font-medium">{t("document.report.reason", { name: documentName })}</h4>
                  <p className="text-sm text-muted-foreground">{existingReport.description}</p>
                </div>
              )}

              <div className="grid gap-2">
                <h4 className="font-medium">{t("document.report.statusLabel")}</h4>
                <p className="text-sm text-muted-foreground">
                  {existingReport.status === "RESOLVED"
                    ? t("document.report.status.resolved")
                    : existingReport.status === "PENDING"
                      ? t("document.report.status.pending")
                      : ""}
                </p>
              </div>
            </div>
          ) : (
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                <FormField
                  control={form.control}
                  name="reportTypeCode"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("document.report.form.type.label")}</FormLabel>
                      <Select onValueChange={field.onChange} defaultValue={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder={t("document.report.form.type.placeholder")} />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {reportTypes.map((type) => (
                            <SelectItem key={type.code} value={type.code}>
                              {type.translations[i18n.language] || type.translations.en}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <FormDescription>{t("document.report.form.type.description")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("document.report.form.description.label")}</FormLabel>
                      <FormControl>
                        <Textarea
                          placeholder={t("document.report.form.description.placeholder")}
                          className="resize-none"
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>{t("document.report.form.description.description")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <DialogFooter>
                  <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                    {t("common.cancel")}
                  </Button>
                  <Button type="submit" variant="destructive" disabled={loading}>
                    {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    {t("document.report.submit")}
                  </Button>
                </DialogFooter>
              </form>
            </Form>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
