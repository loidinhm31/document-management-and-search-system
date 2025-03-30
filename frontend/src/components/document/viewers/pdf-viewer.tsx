import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";

import { Download, Loader2, ZoomIn, ZoomOut } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Document, Page, pdfjs } from "react-pdf";

import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { useIsMobile } from "@/hooks/use-mobile";
import { cn } from "@/lib/utils";
import { DocumentStatus } from "@/types/document";

// Set the worker source
pdfjs.GlobalWorkerOptions.workerSrc = new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();

interface PDFViewerProps {
  documentStatus: DocumentStatus;
  fileUrl: string;
  onDownload: () => void;
  isDownloading?: boolean;
  loading?: boolean;
}

export const PDFViewer: React.FC<PDFViewerProps> = ({
  documentStatus,
  fileUrl,
  onDownload,
  isDownloading,
  loading,
}) => {
  const { t } = useTranslation();
  const isMobile = useIsMobile();
  const [numPages, setNumPages] = useState<number>(0);
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [scale, setScale] = useState<number>(isMobile ? 0.6 : 1);
  const [error, setError] = useState<boolean>(false);
  const [renderedScale, setRenderedScale] = useState<number>(isMobile ? 0.6 : 1);

  // Update scale when device type changes
  useEffect(() => {
    setScale(isMobile ? 0.6 : 1);
    setRenderedScale(isMobile ? 0.6 : 1);
  }, [isMobile]);

  // Debounced scale update
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setRenderedScale(scale);
    }, 300);

    return () => clearTimeout(timeoutId);
  }, [scale]);

  const onDocumentLoadSuccess = useCallback(({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
  }, []);

  const onDocumentLoadError = useCallback((error: Error) => {
    console.info("Error loading PDF:", error);
    setError(true);
  }, []);

  const handlePreviousPage = useCallback(() => {
    setPageNumber((page) => Math.max(1, page - 1));
  }, []);

  const handleNextPage = useCallback(() => {
    setPageNumber((page) => Math.min(numPages, page + 1));
  }, [numPages]);

  const handleZoomIn = useCallback(() => {
    setScale((currentScale) => Math.min(2, currentScale + 0.1));
  }, []);

  const handleZoomOut = useCallback(() => {
    setScale((currentScale) => Math.max(0.5, currentScale - 0.1));
  }, []);

  console.log("daa", documentStatus);

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <p className="text-destructive">{t("document.viewer.error.loading")}</p>
        <Button onClick={onDownload} variant="outline" disabled={isDownloading || loading}>
          <Download className="h-4 w-4 mr-2" />
          {!isDownloading ? t("document.viewer.buttons.downloadInstead") : t("document.viewer.buttons.downloading")}
        </Button>
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col">
      {/* Header with download button */}
      <div className="flex justify-end p-2 bg-muted">
        {documentStatus !== DocumentStatus.PROCESSING && (
          <Button onClick={onDownload} variant="outline" size="sm" disabled={isDownloading || loading}>
            <Download className="h-4 w-4 mr-2" />
            {!isDownloading ? t("document.viewer.buttons.download") : t("document.viewer.buttons.downloading")}
          </Button>
        )}
      </div>

      {/* Controls - Responsive layout */}
      <div
        className={cn(
          "flex p-2 gap-2 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60",
          "flex-col sm:flex-row items-stretch sm:items-center",
        )}
      >
        {/* Page Navigation */}
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={handlePreviousPage}
            disabled={pageNumber <= 1}
            className="flex-1 sm:flex-none"
          >
            {t("document.viewer.buttons.previous")}
          </Button>
          <span className="text-sm whitespace-nowrap">
            {t("document.viewer.buttons.pageInfo", {
              current: pageNumber,
              total: numPages || "?",
            })}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={handleNextPage}
            disabled={pageNumber >= numPages}
            className="flex-1 sm:flex-none"
          >
            {t("document.viewer.buttons.next")}
          </Button>
        </div>

        {/* Zoom Controls */}
        <div className="flex items-center gap-2 mt-2 sm:mt-0 sm:ml-4">
          <Button variant="outline" size="icon" onClick={handleZoomOut} disabled={scale <= 0.5} className="h-8 w-8">
            <ZoomOut className="h-4 w-4" />
          </Button>
          <div className="flex-1 sm:w-32">
            <Slider
              value={[scale * 100]}
              min={50}
              max={200}
              step={10}
              onValueChange={(value) => setScale(value[0] / 100)}
              className="w-full"
            />
          </div>
          <Button variant="outline" size="icon" onClick={handleZoomIn} disabled={scale >= 2} className="h-8 w-8">
            <ZoomIn className="h-4 w-4" />
          </Button>
          <span className="text-sm whitespace-nowrap ml-2 hidden sm:inline">{Math.round(scale * 100)}%</span>
        </div>
      </div>

      {/* PDF Document */}
      <div className="flex-1 overflow-auto bg-muted/30 flex items-center justify-center">
        <div className="relative max-w-full">
          {loading && (
            <div className="absolute inset-0 flex items-center justify-center bg-background/50">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          )}
          <Document
            file={fileUrl}
            onLoadSuccess={onDocumentLoadSuccess}
            onLoadError={onDocumentLoadError}
            loading={
              <div className="flex items-center justify-center p-8">
                <Loader2 className="h-8 w-8 animate-spin" />
              </div>
            }
            className="flex justify-center"
          >
            <Page
              key={`${pageNumber}_${renderedScale}`}
              pageNumber={pageNumber}
              scale={scale}
              loading={
                <div className="flex items-center justify-center p-8">
                  <Loader2 className="h-8 w-8 animate-spin" />
                </div>
              }
              className="shadow-lg max-w-full"
            />
          </Document>
        </div>
      </div>
    </div>
  );
};
