import { Loader2 } from "lucide-react";
import React, { useState } from "react";
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
import { CommentReport, ReportStatus, ReportStatusValues } from "@/types/document-report";

interface CommentReportResolveDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  report: CommentReport;
  onResolve: (status: string) => void;
  resolving: boolean;
}

const CommentReportProcessDialog: React.FC<CommentReportResolveDialogProps> = ({
  open,
  onOpenChange,
  report,
  onResolve,
  resolving,
}) => {
  const { t } = useTranslation();
  const [selectedStatus, setSelectedStatus] = useState<ReportStatus>(ReportStatusValues.RESOLVED);

  const handleSetStatus = (status: ReportStatus) => {
    setSelectedStatus(status);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("admin.reports.comments.dialogs.process.title")}</DialogTitle>
          <DialogDescription className="mt-2 mb-4 text-sm font-medium bg-muted p-3 rounded-md">
            {report.commentContent}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <RadioGroup value={selectedStatus} onValueChange={handleSetStatus} className="space-y-4">
            <div className="flex items-start space-x-3">
              <RadioGroupItem value="RESOLVED" id="resolved" />
              <div className="space-y-1">
                <Label htmlFor="resolved" className="font-medium">
                  {t("admin.reports.comments.dialogs.process.resolve")}
                </Label>
                <p className="text-sm text-muted-foreground">
                  {t("admin.reports.comments.dialogs.process.resolvedDescription")}
                </p>
              </div>
            </div>

            <div className="flex items-start space-x-3">
              <RadioGroupItem value="REJECTED" id="rejected" />
              <div className="space-y-1">
                <Label htmlFor="rejected" className="font-medium">
                  {t("admin.reports.comments.dialogs.process.reject")}
                </Label>
                <p className="text-sm text-muted-foreground">
                  {t("admin.reports.comments.dialogs.process.rejectedDescription") ||
                    "Dismiss the report as invalid or not requiring action."}
                </p>
              </div>
            </div>
          </RadioGroup>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={resolving}>
            {t("common.cancel")}
          </Button>
          <Button variant="destructive" onClick={() => onResolve(selectedStatus)} disabled={resolving}>
            {resolving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("common.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default CommentReportProcessDialog;
