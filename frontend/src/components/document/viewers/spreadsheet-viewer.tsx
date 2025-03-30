import { ChevronLeft, ChevronRight, Download } from "lucide-react";
import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { DocumentStatus } from "@/types/document";

interface SpreadsheetViewerProps {
  documentStatus: DocumentStatus;
  sheets: Array<{
    name: string;
    data: Array<Array<string | number | boolean | Date | null>>;
  }>;
  activeSheet: number;
  onSheetChange: (index: number) => void;
  onDownload: () => void;
  isDownloading?: boolean;
  loading?: boolean;
}

export const SpreadsheetViewer: React.FC<SpreadsheetViewerProps> = ({
  documentStatus,
  sheets,
  activeSheet,
  onSheetChange,
  onDownload,
  isDownloading,
  loading,
}) => {
  const { t } = useTranslation();
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const [showLeftScroll, setShowLeftScroll] = useState(false);
  const [showRightScroll, setShowRightScroll] = useState(false);

  const SHEET_THRESHOLD = 5;
  const useDropdown = sheets.length > SHEET_THRESHOLD;

  const currentSheet = sheets[activeSheet];
  const headerRow = currentSheet?.data[0] || [];
  const dataRows = currentSheet?.data.slice(1);

  // Check if scroll is needed
  useEffect(() => {
    const checkScroll = () => {
      if (scrollRef.current) {
        const scrollElement = scrollRef.current.querySelector("[data-radix-scroll-area-viewport]") as HTMLElement;
        if (scrollElement) {
          const { scrollLeft, scrollWidth, clientWidth } = scrollElement;
          setShowLeftScroll(scrollLeft > 0);
          setShowRightScroll(scrollLeft < scrollWidth - clientWidth);
        }
      }
    };

    checkScroll();
    window.addEventListener("resize", checkScroll);

    return () => {
      window.removeEventListener("resize", checkScroll);
    };
  }, [sheets]);

  // Handle scroll buttons
  const handleScroll = (direction: "left" | "right") => {
    if (scrollRef.current) {
      const scrollElement = scrollRef.current.querySelector("[data-radix-scroll-area-viewport]") as HTMLElement;
      if (scrollElement) {
        const scrollAmount = 200; // pixels to scroll
        const currentScroll = scrollElement.scrollLeft;
        scrollElement.scrollTo({
          left: direction === "left" ? currentScroll - scrollAmount : currentScroll + scrollAmount,
          behavior: "smooth",
        });
      }
    }
  };

  // Listen for scroll events to update button visibility
  const handleScrollEvent = () => {
    if (scrollRef.current) {
      const scrollElement = scrollRef.current.querySelector("[data-radix-scroll-area-viewport]") as HTMLElement;
      if (scrollElement) {
        const { scrollLeft, scrollWidth, clientWidth } = scrollElement;
        setShowLeftScroll(scrollLeft > 0);
        setShowRightScroll(scrollLeft < scrollWidth - clientWidth);
      }
    }
  };

  const formatCellValue = (value: string | number | boolean | Date | null): string => {
    if (value instanceof Date) {
      return value.toLocaleString();
    }
    if (value === null || value === undefined) {
      return "";
    }
    return String(value);
  };

  return (
    <div className="h-full flex flex-col">
      <div className="flex justify-between items-center p-2 bg-muted">
        {useDropdown ? (
          <div className="flex items-center gap-2">
            <Select value={activeSheet.toString()} onValueChange={(value) => onSheetChange(parseInt(value))}>
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder={t("document.viewer.selectSheet")} />
              </SelectTrigger>
              <SelectContent>
                {sheets.map((sheet, index) => (
                  <SelectItem key={sheet.name} value={index.toString()}>
                    {sheet.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <span className="text-sm text-muted-foreground">
              {t("document.viewer.sheetInfo", { current: activeSheet + 1, total: sheets.length })}
            </span>
          </div>
        ) : (
          <div className="flex items-center gap-2 relative">
            {showLeftScroll && (
              <Button
                variant="outline"
                size="icon"
                className="absolute left-0 z-10 bg-background/80 backdrop-blur-sm"
                onClick={() => handleScroll("left")}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
            )}

            <ScrollArea className="max-w-[500px] px-8" onScroll={handleScrollEvent} ref={scrollRef}>
              <div className="flex gap-2">
                {sheets.map((sheet, index) => (
                  <Button
                    key={sheet.name}
                    variant={index === activeSheet ? "default" : "outline"}
                    size="sm"
                    onClick={() => onSheetChange(index)}
                    className="whitespace-nowrap"
                  >
                    {sheet.name}
                  </Button>
                ))}
              </div>
            </ScrollArea>

            {showRightScroll && (
              <Button
                variant="outline"
                size="icon"
                className="absolute right-0 z-10 bg-background/80 backdrop-blur-sm"
                onClick={() => handleScroll("right")}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            )}
          </div>
        )}

        {documentStatus !== DocumentStatus.PROCESSING && (
          <Button onClick={onDownload} variant="outline" size="sm" disabled={isDownloading || loading}>
            <Download className="h-4 w-4 mr-2" />
            {!isDownloading ? t("document.viewer.buttons.download") : t("document.viewer.buttons.downloading")}
          </Button>
        )}
      </div>
      <div className="flex-1 overflow-auto bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              {headerRow.map((cell, index) => (
                <TableHead key={index} className="whitespace-nowrap">
                  {formatCellValue(cell)}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {dataRows.map((row, rowIndex) => (
              <TableRow key={rowIndex}>
                {row.map((cell, cellIndex) => (
                  <TableCell key={cellIndex}>{formatCellValue(cell)}</TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
};
