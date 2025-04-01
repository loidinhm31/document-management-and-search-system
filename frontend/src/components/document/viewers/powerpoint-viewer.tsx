import { Download } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { DocumentStatus } from "@/types/document";

interface PowerPointViewerProps {
  isAdmin: boolean;
  documentStatus: DocumentStatus;
  content: string[];
  onDownload: () => void;
  isDownloading?: boolean;
  loading?: boolean;
}

export const PowerPointViewer: React.FC<PowerPointViewerProps> = ({
  isAdmin,
  documentStatus,
  content,
  onDownload,
  isDownloading,
  loading,
}) => {
  const { t } = useTranslation();
  const [currentSlide, setCurrentSlide] = useState(0);

  const handlePrevSlide = () => {
    setCurrentSlide((prev) => (prev > 0 ? prev - 1 : prev));
  };

  const handleNextSlide = () => {
    setCurrentSlide((prev) => (prev < content.length - 1 ? prev + 1 : prev));
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") {
        handlePrevSlide();
      } else if (e.key === "ArrowRight") {
        handleNextSlide();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-between p-2 bg-muted">
        <div className="flex items-center gap-2">
          <Button onClick={handlePrevSlide} disabled={currentSlide === 0} variant="outline" size="sm">
            {t("document.viewer.buttons.previous")}
          </Button>
          <Button onClick={handleNextSlide} disabled={currentSlide === content.length - 1} variant="outline" size="sm">
            {t("document.viewer.buttons.next")}
          </Button>
          <span className="text-sm">
            {t("document.viewer.buttons.slideInfo", {
              current: currentSlide + 1,
              total: content.length,
            })}
          </span>
        </div>
        {!isAdmin && documentStatus !== DocumentStatus.PROCESSING && (
          <Button onClick={onDownload} variant="outline" size="sm" disabled={isDownloading || loading}>
            <Download className="h-4 w-4 mr-2" />
            {!isDownloading ? t("document.viewer.buttons.download") : t("document.viewer.buttons.downloading")}
          </Button>
        )}
      </div>
      <div className="flex-1 overflow-auto bg-background text-foreground p-4">
        <div className="w-full h-full" dangerouslySetInnerHTML={{ __html: content[currentSlide] || "" }} />
      </div>
    </div>
  );
};
