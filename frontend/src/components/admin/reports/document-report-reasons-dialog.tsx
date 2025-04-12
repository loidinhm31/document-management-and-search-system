import { UserRound } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { reportService } from "@/services/report.service";
import { ReportReason, ReportStatus, ReportType } from "@/types/document-report";

interface DocumentReportReasonsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  documentId: string;
  documentTitle: string;
  reportTypes: ReportType[];
  status: ReportStatus;
}

const DocumentReportReasonsDialog: React.FC<DocumentReportReasonsDialogProps> = ({
  open,
  onOpenChange,
  documentId,
  documentTitle,
  reportTypes,
  status,
}) => {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const [loading, setLoading] = useState(true);
  const [reportReasons, setReportReasons] = useState<ReportReason[]>([]);

  useEffect(() => {
    if (open) {
      fetchReportReasons();
    }
  }, [open, documentId]);

  const fetchReportReasons = async () => {
    setLoading(true);
    try {
      const response = await reportService.getDocumentReportDetail(documentId, status);
      setReportReasons(response.data || []);
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("admin.reports.documents.reasonsError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleString();
  };

  const mapReportType = (reportTypeCode: string) => {
    return reportTypes?.find((type) => type?.code === reportTypeCode);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[85vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>
            {t("admin.reports.documents.dialogs.reasons.title", {
              document: documentTitle,
            })}
          </DialogTitle>
          <DialogDescription>{t("admin.reports.documents.dialogs.reasons.description")}</DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-hidden">
          <ScrollArea className="h-[50vh]">
            {loading ? (
              <div className="space-y-4 p-4">
                {Array(3)
                  .fill(null)
                  .map((_, index) => (
                    <div key={index} className="space-y-2">
                      <Skeleton className="h-4 w-1/3" />
                      <Skeleton className="h-4 w-full" />
                      <Skeleton className="h-4 w-2/3" />
                    </div>
                  ))}
              </div>
            ) : reportReasons.length > 0 ? (
              <div className="space-y-6 p-4">
                {reportReasons.map((reason, index) => (
                  <div key={index} className="space-y-2 rounded-lg border p-4">
                    <div className="flex items-center justify-between">
                      <div className="font-medium">
                        {mapReportType(reason.reportTypeCode)?.translations[i18n.language] ||
                          mapReportType(reason.reportTypeCode)?.translations.en}
                      </div>
                      <div className="text-sm text-muted-foreground">{formatDate(reason.createdAt)}</div>
                    </div>

                    <div className="flex items-center text-sm text-muted-foreground">
                      <UserRound className="mr-2 h-4 w-4" />
                      {t("admin.reports.documents.dialogs.reasons.reportedBy", {
                        user: reason.reporterUsername,
                      })}
                    </div>

                    {reason.description && (
                      <div className="mt-2 rounded-md bg-muted p-3 text-sm">{reason.description}</div>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex h-full items-center justify-center p-4">
                <p className="text-muted-foreground">{t("admin.reports.documents.dialogs.reasons.noReasons")}</p>
              </div>
            )}
          </ScrollArea>
        </div>

        <div className="flex justify-end pt-4">
          <Button onClick={() => onOpenChange(false)}>{t("common.close")}</Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default DocumentReportReasonsDialog;
