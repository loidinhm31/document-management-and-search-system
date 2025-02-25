import { Download } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

interface SpreadsheetViewerProps {
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
  sheets,
  activeSheet,
  onSheetChange,
  onDownload,
  isDownloading,
  loading,
}) => {
  const { t } = useTranslation();
  const currentSheet = sheets[activeSheet];
  const headerRow = currentSheet.data[0] || [];
  const dataRows = currentSheet.data.slice(1);

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
        <div className="flex gap-2">
          {sheets.map((sheet, index) => (
            <Button
              key={sheet.name}
              variant={index === activeSheet ? "default" : "outline"}
              size="sm"
              onClick={() => onSheetChange(index)}
            >
              {sheet.name}
            </Button>
          ))}
        </div>
        <Button onClick={onDownload} variant="outline" size="sm" disabled={isDownloading || loading}>
          <Download className="h-4 w-4 mr-2" />
          {!isDownloading ? t("document.viewer.buttons.download") : t("document.viewer.buttons.downloading")}
        </Button>
      </div>
      <div className="flex-1 overflow-auto">
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
