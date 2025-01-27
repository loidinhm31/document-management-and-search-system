import { Upload } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";

import { DocumentUpload } from "@/components/document/my-document/document-upload";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog";

interface DocumentUploadDialogProps {
  onUploadSuccess?: () => void;
  trigger?: React.ReactNode;
}

export default function DocumentUploadDialog({ onUploadSuccess, trigger }: DocumentUploadDialogProps) {
  const { t } = useTranslation();
  const [open, setOpen] = React.useState(false);

  const handleUploadSuccess = () => {
    setOpen(false);
    onUploadSuccess?.();
  };

  const defaultTrigger = (
    <Button>
      <Upload className="mr-2 h-4 w-4" />
      {t("document.upload.buttons.upload")}
    </Button>
  );

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        {trigger || defaultTrigger}
      </DialogTrigger>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto"
                     onInteractOutside={(e) => e.preventDefault()}>
        <DialogHeader>
          <DialogTitle>{t("document.upload.title")}</DialogTitle>
          <DialogDescription>
            {t("document.upload.description")}
          </DialogDescription>
        </DialogHeader>
        <DocumentUpload onUploadSuccess={handleUploadSuccess} />
      </DialogContent>
    </Dialog>
  );
}