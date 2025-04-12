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
import { createCommentReportSchema, ReportFormValues } from "@/schemas/report-schemas";
import { documentReportService } from "@/services/document-report.service";
import { reportService } from "@/services/report.service";
import { ReportType } from "@/types/document-report";

interface ReportCommentDialogProps {
  documentId: string;
  commentId: number;
  commentAuthor: string;
  iconOnly?: boolean;
  isReported?: boolean;
  onReportSuccess?: () => void;
}

export function ReportCommentDialog({
  documentId,
  commentId,
  commentAuthor,
  iconOnly = false,
  isReported = false,
  onReportSuccess,
}: ReportCommentDialogProps) {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();

  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [reportTypes, setReportTypes] = useState<ReportType[]>([]);
  const [existingReport, setExistingReport] = useState<any | null>(null);

  const form = useForm<ReportFormValues>({
    resolver: zodResolver(createCommentReportSchema(t)),
    mode: "onBlur",
  });

  useEffect(() => {
    if (open) {
      loadReportTypes();

      checkReportStatus();
    }
  }, [open]);

  const loadReportTypes = async () => {
    try {
      const response = await reportService.getCommentReportTypes();
      setReportTypes(response.data);
    } catch (error) {
      console.error("Error loading report types:", error);
    }
  };

  const checkReportStatus = async () => {
    try {
      const response = await documentReportService.getCommentUserReport(documentId, commentId);
      setExistingReport(response.data);
    } catch (error) {
      if (error.response?.status !== 404) {
        console.error("Error checking report status:", error);
      }
    }
  };

  const onSubmit = async (values: ReportFormValues) => {
    setLoading(true);
    try {
      const request = {
        reportTypeCode: values.reportTypeCode,
        description: values.description,
      };

      const response = await documentReportService.createCommentReport(documentId, commentId, request);
      setExistingReport(response.data);

      toast({
        title: t("common.success"),
        description: t("comments.report.createSuccess"),
        variant: "success",
      });

      if (onReportSuccess) {
        onReportSuccess();
      }

      setOpen(false);
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("comments.report.createError"),
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
          <Button variant="ghost" size="sm" className={isReported ? "text-orange-500" : ""}>
            <Flag className={isReported ? "h-4 w-4 text-orange-400 fill-orange-400" : "h-4 w-4"} />
            {!iconOnly &&
              (isReported ? t("document.comments.actions.reported") : t("document.comments.actions.report"))}
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {existingReport
                ? t("document.comments.report.alreadyReported.title")
                : t("document.comments.report.title")}
            </DialogTitle>
            <DialogDescription>
              {t("document.comments.report.description", { author: commentAuthor })}
            </DialogDescription>
          </DialogHeader>

          {existingReport ? (
            <div className="grid gap-4 mt-2">
              <div className="grid gap-2">
                <h4 className="font-medium">{t("document.comments.report.type")}</h4>
                <p className="text-sm text-muted-foreground">{existingReport.reportTypeTranslation[i18n.language]}</p>
              </div>

              {existingReport.description && (
                <div className="grid gap-2">
                  <h4 className="font-medium">{t("document.comments.report.reason")}</h4>
                  <p className="text-sm text-muted-foreground">{existingReport.description}</p>
                </div>
              )}

              <div className="grid gap-2">
                <h4 className="font-medium">{t("document.comments.report.statusLabel")}</h4>
                <p className="text-sm text-muted-foreground">
                  {existingReport.resolved
                    ? t("document.comments.report.status.resolved")
                    : t("document.comments.report.status.pending")}
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
                      <FormLabel>{t("document.comments.report.form.type.label")}</FormLabel>
                      <Select onValueChange={field.onChange} defaultValue={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder={t("document.comments.report.form.type.placeholder")} />
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
                      <FormDescription>{t("document.comments.report.form.type.description")}</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("document.comments.report.form.description.label")}</FormLabel>
                      <FormControl>
                        <Textarea
                          placeholder={t("document.comments.report.form.description.placeholder")}
                          className="resize-none"
                          {...field}
                        />
                      </FormControl>
                      <FormDescription>{t("document.comments.report.form.description.description")}</FormDescription>
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
                    {t("document.comments.report.submit")}
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
