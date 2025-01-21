import { FileText } from "lucide-react";
import React from "react";

import { Button } from "@/components/ui/button";
import { useTranslation } from "react-i18next";

interface UnsupportedViewerProps {
  documentType: string;
  onDownload: () => void;
}

export const UnsupportedViewer: React.FC<UnsupportedViewerProps> = ({ documentType, onDownload }) => {
  const { t } = useTranslation();

  return (
    <div className="h-full flex flex-col items-center justify-center gap-4">
      <FileText className="h-16 w-16 text-muted-foreground" />
      <p className="text-muted-foreground">
        {t("document.viewer.error.unsupported", { type: documentType.toLowerCase() })}
      </p>
      <Button onClick={onDownload} variant="outline">
        {t("document.viewer.buttons.downloadInstead")}
      </Button>
    </div>
  );
};