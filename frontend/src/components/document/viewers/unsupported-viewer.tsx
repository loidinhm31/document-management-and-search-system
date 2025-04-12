import { Download, FileText } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";

interface UnsupportedViewerProps {
  isAdmin: boolean;
  documentType: string;
  onDownload: () => void;
  isDownloading?: boolean;
  loading?: boolean;
}

export const UnsupportedViewer: React.FC<UnsupportedViewerProps> = ({
  isAdmin,
  documentType,
  onDownload,
  isDownloading,
  loading,
}) => {
  const { t } = useTranslation();

  return (
    <div className="h-full flex flex-col items-center justify-center gap-4">
      <FileText className="h-16 w-16 text-muted-foreground" />
      <p className="text-muted-foreground">
        {t("document.viewer.error.unsupported", { type: documentType.toLowerCase() })}
      </p>
      {!isAdmin && (
        <Button onClick={onDownload} variant="outline" disabled={isDownloading || loading}>
          <Download className="h-4 w-4 mr-2" />
          {!isDownloading ? t("document.viewer.buttons.downloadInstead") : t("document.viewer.buttons.downloading")}
        </Button>
      )}
    </div>
  );
};
