import "react-pdf/dist/esm/Page/AnnotationLayer.css";
import "react-pdf/dist/esm/Page/TextLayer.css";

import { Download, Loader2, ZoomIn, ZoomOut } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Document, Page, pdfjs } from "react-pdf";

import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";


// Set the worker source
pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url
).toString();

interface PDFViewerProps {
  fileUrl: string;
  onDownload: () => void;
}

export const PDFViewer: React.FC<PDFViewerProps> = ({
                                                      fileUrl,
                                                      onDownload
                                                    }) => {
  const { t } = useTranslation();
  const [numPages, setNumPages] = useState<number>(0);
  const [pageNumber, setPageNumber] = useState<number>(1);
  const [scale, setScale] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<boolean>(false);
  const [renderedScale, setRenderedScale] = useState<number>(1);

  // Update rendered scale when zooming stops
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      setRenderedScale(scale);
    }, 300); // Debounce scale updates

    return () => clearTimeout(timeoutId);
  }, [scale]);

  const onDocumentLoadSuccess = ({ numPages }: { numPages: number }) => {
    setNumPages(numPages);
    setLoading(false);
  };

  const onDocumentLoadError = (error: Error) => {
    console.error("Error loading PDF:", error);
    setError(true);
    setLoading(false);
  };

  const handlePreviousPage = () => {
    setPageNumber(page => Math.max(1, page - 1));
  };

  const handleNextPage = () => {
    setPageNumber(page => Math.min(numPages, page + 1));
  };

  const handleZoomIn = () => {
    setScale(currentScale => Math.min(2, currentScale + 0.1));
  };

  const handleZoomOut = () => {
    setScale(currentScale => Math.max(0.5, currentScale - 0.1));
  };

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <p className="text-destructive">{t("document.viewer.error.loading")}</p>
        <Button onClick={onDownload} variant="outline">
          {t("document.viewer.buttons.downloadInstead")}
        </Button>
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-end p-2 bg-muted">
        <Button onClick={onDownload} variant="outline" size="sm">
          <Download className="h-4 w-4 mr-2" />
          {t("document.viewer.buttons.download")}
        </Button>
      </div>
      {/* Toolbar */}
      <div className="flex justify-between items-center p-2">
        <div className="flex items-center gap-2">
          {/* Page Navigation */}
          <Button
            variant="outline"
            size="sm"
            onClick={handlePreviousPage}
            disabled={pageNumber <= 1}
          >
            {t("document.viewer.buttons.previous")}
          </Button>
          <span className="text-sm">
            {t("document.viewer.pageInfo", {
              current: pageNumber,
              total: numPages || "?"
            })}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={handleNextPage}
            disabled={pageNumber >= numPages}
          >
            {t("document.viewer.buttons.next")}
          </Button>

          {/* Zoom Controls */}
          <div className="flex items-center gap-2 ml-4">
            <Button
              variant="outline"
              size="icon"
              onClick={handleZoomOut}
              disabled={scale <= 0.5}
            >
              <ZoomOut className="h-4 w-4" />
            </Button>
            <div className="w-32">
              <Slider
                value={[scale * 100]}
                min={50}
                max={200}
                step={10}
                onValueChange={(value) => setScale(value[0] / 100)}
              />
            </div>
            <Button
              variant="outline"
              size="icon"
              onClick={handleZoomIn}
              disabled={scale >= 2}
            >
              <ZoomIn className="h-4 w-4" />
            </Button>
            <span className="text-sm ml-2">{Math.round(scale * 100)}%</span>
          </div>
        </div>

      </div>

      {/* PDF Viewer */}
      <div className="flex-1 overflow-auto bg-muted/30 flex items-center justify-center">
        <div className="relative">
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
              className="shadow-lg"
            />
          </Document>
        </div>
      </div>
    </div>
  );
};
