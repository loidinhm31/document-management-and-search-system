import { UserRound } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { reportService } from "@/services/report.service";
import { CommentReportDetail, ReportStatus, ReportType } from "@/types/document-report";

interface CommentReportReasonsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  commentId: number;
  commentContent: string;
  status: ReportStatus;
  reportTypes: ReportType[];
}

const CommentReportReasonsDialog: React.FC<CommentReportReasonsDialogProps> = ({
  open,
  onOpenChange,
  commentId,
  status,
  reportTypes,
}) => {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const [loading, setLoading] = useState(true);
  const [reportDetails, setReportDetails] = useState<CommentReportDetail[]>([]);

  useEffect(() => {
    if (open) {
      fetchReportDetails();
    }
  }, [open, commentId]);

  const fetchReportDetails = async () => {
    setLoading(true);
    try {
      const response = await reportService.getCommentReportDetail(commentId, status);
      console.log("rers", response.data);
      setReportDetails(response.data);
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("admin.reports.comments.reasonsError"),
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

  const mapReportType = (reportTypeCode: string): ReportType => {
    return reportTypes?.find((type: ReportType) => type?.code === reportTypeCode);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[85vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <DialogTitle>{t("admin.reports.comments.dialogs.reasons.title")}</DialogTitle>
          <DialogDescription>{t("admin.reports.comments.dialogs.reasons.description")}</DialogDescription>
        </DialogHeader>

        <div className="flex-1 overflow-hidden">
          {loading ? (
            <div className="p-4 space-y-4">
              <Skeleton className="h-6 w-full" />
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-6 w-1/2" />
            </div>
          ) : reportDetails ? (
            <div>
              <div className="px-1 py-2 space-y-4">
                <div className="rounded-lg border p-4">
                  <div className="space-y-2">
                    <h3 className="font-medium">{t("admin.reports.comments.dialogs.reasons.comment")}</h3>
                    <div className="bg-muted p-3 rounded-md">{reportDetails[0].commentContent}</div>
                  </div>
                </div>
              </div>

              <ScrollArea className="h-[50vh] px-1">
                {reportDetails.map((report, index) => {
                  return (
                    <div key={index} className="space-y-4 py-1">
                      <div className="rounded-lg border p-4">
                        <div className="space-y-2">
                          <h3 className="font-medium">
                            {mapReportType(report.reportTypeCode)?.translations[i18n.language] ||
                              mapReportType(report.reportTypeCode)?.translations.en}
                          </h3>
                          <div className="text-sm text-muted-foreground flex items-center">
                            <UserRound className="mr-2 h-4 w-4" />
                            {t("admin.reports.comments.dialogs.reasons.reportedBy", {
                              user: report.reporterUsername,
                            })}
                          </div>
                          {report.description && (
                            <div className="bg-muted p-3 rounded-md text-sm mt-2">{report.description}</div>
                          )}
                          <div className="flex justify-between text-sm mt-2">
                            <span>{formatDate(report.createdAt)}</span>
                          </div>
                          {report.resolved && report.resolvedBy && (
                            <div className="text-sm text-muted-foreground">
                              {t("admin.reports.comments.dialogs.reasons.resolvedBy", {
                                user: report.resolvedBy,
                              })}
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </ScrollArea>
            </div>
          ) : (
            <div className="flex items-center justify-center h-[200px]">
              <p className="text-muted-foreground">{t("admin.reports.comments.dialogs.reasons.noReasons")}</p>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button onClick={() => onOpenChange(false)}>{t("common.close")}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default CommentReportReasonsDialog;
