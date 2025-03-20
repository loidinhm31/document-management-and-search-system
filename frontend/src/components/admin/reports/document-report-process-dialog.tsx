import { AlertTriangle, Loader2 } from "lucide-react";
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
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { cn } from "@/lib/utils";
import { DocumentReport, ReportStatus, ReportStatusValues } from "@/types/document-report";

interface DocumentReportProcessDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  report: DocumentReport;
  onResolve: (status: string) => void;
  processing: boolean;
}

const DocumentReportProcessDialog: React.FC<DocumentReportProcessDialogProps> = ({
  open,
  onOpenChange,
  report,
  onResolve,
  processing,
}) => {
  const { t } = useTranslation();
  const [selectedStatus, setSelectedStatus] = useState<ReportStatus>(ReportStatusValues.RESOLVED);

  useEffect(() => {
    if (open) {
      if (report.status !== ReportStatusValues.RESOLVED) {
        setSelectedStatus(ReportStatusValues.RESOLVED);
      } else if (report.status === ReportStatusValues.RESOLVED) {
        setSelectedStatus(ReportStatusValues.REMEDIATED);
      }
    }
  }, [open]);

  const handleSetStatus = (status: ReportStatus) => {
    setSelectedStatus(status);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("admin.reports.documents.dialogs.process.title")}</DialogTitle>
          <DialogDescription>
            {t("admin.reports.documents.dialogs.process.description", {
              document: report.documentTitle,
            })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <RadioGroup value={selectedStatus} onValueChange={handleSetStatus} className="space-y-4">
            <div className="flex items-start space-x-3">
              <RadioGroupItem
                disabled={report.status === ReportStatusValues.RESOLVED}
                value="RESOLVED"
                id="resolved"
                aria-disabled={report.status === ReportStatusValues.RESOLVED}
              />
              <div
                className={cn(
                  "space-y-1",
                  report.status === ReportStatusValues.RESOLVED && "opacity-50 cursor-not-allowed",
                )}
              >
                <Label htmlFor="resolved" className="font-medium">
                  {t("admin.reports.documents.status.resolved")}
                </Label>
                <p className="text-sm text-muted-foreground">
                  {t("admin.reports.documents.dialogs.process.resolvedDescription")}
                </p>
              </div>
            </div>

            <div className="flex items-start space-x-3">
              <RadioGroupItem
                disabled={report.status === ReportStatusValues.PENDING}
                value="REMEDIATED"
                id="remediated"
                aria-disabled={report.status === ReportStatusValues.PENDING}
              />
              <div
                className={cn(
                  "space-y-1",
                  report.status === ReportStatusValues.PENDING && "opacity-50 cursor-not-allowed",
                )}
              >
                <Label htmlFor="remediated" className="font-medium">
                  {t("admin.reports.documents.status.remediated")}
                </Label>
                <p className="text-sm text-muted-foreground">
                  {t("admin.reports.documents.dialogs.process.remediatedDescription")}
                </p>
              </div>
            </div>

            <div className="flex items-start space-x-3">
              <RadioGroupItem
                disabled={report.status === ReportStatusValues.RESOLVED}
                value="REJECTED"
                id="rejected"
                aria-disabled={report.status === ReportStatusValues.RESOLVED}
              />
              <div
                className={cn(
                  "space-y-1",
                  report.status === ReportStatusValues.RESOLVED && "opacity-50 cursor-not-allowed",
                )}
              >
                <Label htmlFor="rejected" className="font-medium">
                  {t("admin.reports.documents.status.rejected")}
                </Label>
                <p className="text-sm text-muted-foreground">
                  {t("admin.reports.documents.dialogs.process.rejectedDescription") ||
                    "Dismiss the report as invalid or not requiring action."}
                </p>
              </div>
            </div>
          </RadioGroup>

          {selectedStatus === ReportStatusValues.RESOLVED && (
            <div className="rounded-md bg-yellow-50 p-4">
              <div className="flex">
                <div className="flex-shrink-0">
                  <AlertTriangle className="h-5 w-5 text-yellow-400" />
                </div>
                <div className="ml-3">
                  <h3 className="text-sm font-medium text-yellow-800">
                    {t("admin.reports.documents.dialogs.resolve.title")}
                  </h3>
                  <div className="mt-2 text-sm text-yellow-700">
                    <p>
                      {t("admin.reports.documents.dialogs.resolve.description", {
                        document: report.documentTitle,
                      })}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={processing}>
            {t("common.cancel")}
          </Button>
          <Button
            variant={selectedStatus === "RESOLVED" ? "destructive" : "default"}
            onClick={() => onResolve(selectedStatus)}
            disabled={processing}
          >
            {processing && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("admin.reports.documents.actions.process")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default DocumentReportProcessDialog;
