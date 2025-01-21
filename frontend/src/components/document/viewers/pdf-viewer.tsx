import { Download } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";

interface PDFViewerProps {
  fileUrl: string;
  mimeType: string;
  fileName: string;
  onDownload: () => void;
}


export const PDFViewer: React.FC<PDFViewerProps> = ({ fileUrl, mimeType, fileName, onDownload }) => {
  const { t } = useTranslation();

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-end p-2 bg-muted">
        <Button onClick={onDownload} variant="outline" size="sm">
          <Download className="h-4 w-4 mr-2" />
          {t("document.viewer.buttons.download")}
        </Button>
      </div>
      <object data={fileUrl} type={mimeType} className="w-full h-full">
        <div className="flex flex-col items-center justify-center h-full gap-4">
          <p>{t("document.viewer.preview.unavailable")}</p>
          <Button onClick={onDownload} variant="outline">
            {t("document.viewer.buttons.download")}
          </Button>
        </div>
      </object>
    </div>
  );
};
