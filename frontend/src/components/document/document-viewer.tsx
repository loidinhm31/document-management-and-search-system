import { Download, FileText, Loader2 } from "lucide-react";
import mammoth from "mammoth";
import React, { useEffect, useState } from "react";
import * as XLSX from "xlsx";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { DocumentType } from "@/types/document";

interface DocumentViewerProps {
  documentId: string;
  mimeType: string;
  documentType: DocumentType;
  fileName: string;
}

interface ExcelSheet {
  name: string;
  data: Array<Array<string | number | boolean | Date | null>>;
}

export const DocumentViewer = ({ documentId, mimeType, documentType, fileName }: DocumentViewerProps) => {
  const [loading, setLoading] = useState(true);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [wordContent, setWordContent] = useState<string | null>(null);
  const [textContent, setTextContent] = useState<string | null>(null);
  const [excelContent, setExcelContent] = useState<ExcelSheet[]>([]);
  const [activeSheet, setActiveSheet] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    const loadDocument = async () => {
      setLoading(true);
      setError(null);

      try {
        const response = await documentService.downloadDocument(documentId);
        const blob = new Blob([response.data], { type: mimeType });

        // Handle different document types
        if (documentType === DocumentType.PDF) {
          const url = URL.createObjectURL(blob);
          setFileUrl(url);
        } else if (documentType === DocumentType.WORD || documentType === DocumentType.WORD_DOCX) {
          const arrayBuffer = await blob.arrayBuffer();
          const result = await mammoth.convertToHtml({ arrayBuffer });
          setWordContent(result.value);
        } else if (documentType === DocumentType.EXCEL || documentType === DocumentType.EXCEL_XLSX) {
          const arrayBuffer = await blob.arrayBuffer();
          const workbook = XLSX.read(arrayBuffer, {
            type: "array",
            cellDates: true,
            cellStyles: true
          });

          const sheets: ExcelSheet[] = workbook.SheetNames.map(sheetName => {
            const worksheet = workbook.Sheets[sheetName];
            // Convert sheet data to 2D array with proper typing
            const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 }) as Array<Array<string | number | boolean | Date | null>>;
            return {
              name: sheetName,
              data: jsonData
            };
          });

          setExcelContent(sheets);
        } else if (mimeType.startsWith("text/")) {
          const text = await blob.text();
          setTextContent(text);
        }
      } catch (err) {
        console.error("Error loading document:", err);
        setError("Failed to load document");
        toast({
          title: "Error",
          description: "Failed to load document preview",
          variant: "destructive"
        });
      } finally {
        setLoading(false);
      }
    };

    loadDocument();

    // Cleanup
    return () => {
      if (fileUrl) {
        URL.revokeObjectURL(fileUrl);
      }
    };
  }, [documentId, mimeType, documentType]);

  const downloadFile = async () => {
    try {
      const response = await documentService.downloadDocument(documentId);
      const blob = new Blob([response.data], { type: mimeType });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", fileName);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast({
        title: "Error",
        description: "Failed to download file",
        variant: "destructive"
      });
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

  if (loading) {
    return (
      <div className="flex items-center justify-center h-full">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-4">
        <p className="text-destructive">{error}</p>
        <Button onClick={downloadFile} variant="outline">
          Download Instead
        </Button>
      </div>
    );
  }

  const renderContent = () => {
    // For PDFs
    if (documentType === DocumentType.PDF && fileUrl) {
      return (
        <div className="h-full flex flex-col">
          <div className="flex justify-end p-2 bg-muted">
            <Button onClick={downloadFile} variant="outline" size="sm">
              <Download className="h-4 w-4 mr-2" />
              Download
            </Button>
          </div>
          <object
            data={fileUrl}
            type={mimeType}
            className="w-full h-full"
          >
            <div className="flex flex-col items-center justify-center h-full gap-4">
              <p>PDF preview not available in your browser</p>
              <Button onClick={downloadFile} variant="outline">
                Download PDF
              </Button>
            </div>
          </object>
        </div>
      );
    }

    // For Word documents
    if ((documentType === DocumentType.WORD || documentType === DocumentType.WORD_DOCX) && wordContent) {
      return (
        <div className="h-full flex flex-col">
          <div className="flex justify-end p-2 bg-muted">
            <Button onClick={downloadFile} variant="outline" size="sm">
              <Download className="h-4 w-4 mr-2" />
              Download
            </Button>
          </div>
          <div
            className="flex-1 overflow-auto bg-white p-4"
            dangerouslySetInnerHTML={{ __html: wordContent }}
          />
        </div>
      );
    }

    // For Excel files
    if ((documentType === DocumentType.EXCEL || documentType === DocumentType.EXCEL_XLSX) && excelContent.length > 0) {
      const currentSheet = excelContent[activeSheet];
      const headerRow = currentSheet.data[0] || [];
      const dataRows = currentSheet.data.slice(1);

      return (
        <div className="h-full flex flex-col">
          <div className="flex justify-between items-center p-2 bg-muted">
            <div className="flex gap-2">
              {excelContent.map((sheet, index) => (
                <Button
                  key={sheet.name}
                  variant={index === activeSheet ? "default" : "outline"}
                  size="sm"
                  onClick={() => setActiveSheet(index)}
                >
                  {sheet.name}
                </Button>
              ))}
            </div>
            <Button onClick={downloadFile} variant="outline" size="sm">
              <Download className="h-4 w-4 mr-2" />
              Download
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
                      <TableCell key={cellIndex}>
                        {formatCellValue(cell)}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      );
    }

    // For text files
    if (documentType === DocumentType.TEXT_PLAIN && textContent) {
      return (
        <div className="h-full flex flex-col">
          <div className="flex justify-end p-2 bg-muted">
            <Button onClick={downloadFile} variant="outline" size="sm">
              <Download className="h-4 w-4 mr-2" />
              Download
            </Button>
          </div>
          <div className="flex-1 overflow-auto bg-white p-4">
            <pre className="whitespace-pre-wrap font-mono text-sm">
              {textContent}
            </pre>
          </div>
        </div>
      );
    }

    // Default fallback for unsupported types
    return (
      <div className="h-full flex flex-col items-center justify-center gap-4">
        <FileText className="h-16 w-16 text-muted-foreground" />
        <p className="text-muted-foreground">
          Preview not available for {documentType.toLowerCase()}
        </p>
        <Button onClick={downloadFile} variant="outline">
          Download File
        </Button>
      </div>
    );
  };

  return renderContent();
};

export default DocumentViewer;