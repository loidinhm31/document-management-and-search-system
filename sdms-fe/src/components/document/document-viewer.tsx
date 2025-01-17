import { FileText, Loader2 } from "lucide-react";
import React, { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { DocumentType } from "@/types/document";

interface DocumentViewerProps {
  documentId: string;
  mimeType: string;
  documentType: DocumentType;
  fileName: string;
}


export const DocumentViewer = ({ documentId, mimeType, documentType, fileName }: DocumentViewerProps) => {
  const [loading, setLoading] = useState(true);
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [textContent, setTextContent] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { toast } = useToast();

  useEffect(() => {
    const loadDocument = async () => {
      setLoading(true);
      setError(null);
      try {
        const response = await documentService.downloadDocument(documentId);
        const blob = new Blob([response.data], { type: mimeType });

        // For text files, read the content
        if (mimeType.startsWith("text/")) {
          const text = await blob.text();
          setTextContent(text);
        } else {
          const url = URL.createObjectURL(blob);
          setFileUrl(url);
        }
      } catch (err) {
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

    console.log("documentType", documentType);
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
    if (documentType === DocumentType.PDF) {
      return (
        <div className="h-full flex flex-col">
          <div className="flex justify-end p-2 bg-muted">
            <Button onClick={downloadFile} variant="outline" size="sm">
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

    // For text files
    if (documentType === DocumentType.TEXT) {
      return (
        <div className="h-full flex flex-col">
          <div className="flex justify-end p-2 bg-muted">
            <Button onClick={downloadFile} variant="outline" size="sm">
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

    // For office documents and other types
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