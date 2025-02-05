import React from "react";

import { DocumentViewer } from "@/components/document/viewers/document-viewer";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { DocumentInformation, DocumentType, DocumentVersion } from "@/types/document";

interface DocumentViewerDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  documentData: DocumentInformation | DocumentVersion | null;
  documentId: string;
  isVersion?: boolean;
}

const DocumentViewerDialog: React.FC<DocumentViewerDialogProps> = ({
                                                                     open,
                                                                     onOpenChange,
                                                                     documentData,
                                                                     documentId,
                                                                     isVersion = false,
                                                                   }) => {
  if (!documentData) return null;

  const getFileSize = () => {
    const size = documentData.fileSize / 1024;
    return size >= 1024 ? `${(size / 1024).toFixed(2)} MB` : `${size.toFixed(2)} KB`;
  };

  const getDocumentType = (mimeType: string): DocumentType => {
    switch (mimeType) {
      case "application/pdf":
        return DocumentType.PDF;
      case "application/msword":
        return DocumentType.WORD;
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
        return DocumentType.WORD_DOCX;
      case "application/vnd.ms-excel":
        return DocumentType.EXCEL;
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
        return DocumentType.EXCEL_XLSX;
      case "application/vnd.ms-powerpoint":
        return DocumentType.POWERPOINT;
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
        return DocumentType.POWERPOINT_PPTX;
      case "text/plain":
        return DocumentType.TEXT_PLAIN;
      case "application/rtf":
        return DocumentType.RTF;
      case "text/csv":
        return DocumentType.CSV;
      case "application/xml":
        return DocumentType.XML;
      case "application/json":
        return DocumentType.JSON;
      default:
        if (mimeType.includes("pdf")) return DocumentType.PDF;
        if (mimeType.includes("word")) return DocumentType.WORD;
        if (mimeType.includes("excel")) return DocumentType.EXCEL;
        if (mimeType.includes("powerpoint")) return DocumentType.POWERPOINT;
        if (mimeType.includes("text/plain")) return DocumentType.TEXT_PLAIN;
        console.warn(`Unrecognized MIME type: ${mimeType}`);
        return DocumentType.TEXT_PLAIN; // Fallback to text viewer
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-w-4xl flex-col gap-0 p-0">
        <DialogHeader className="px-6 py-4">
          <DialogTitle className="pr-8">
            {documentData?.filename}
          </DialogTitle>
          <DialogDescription className="text-sm">
            {isVersion && `Version ${(documentData as DocumentVersion).versionNumber + 1} - `}
            {documentData.mimeType} - {getFileSize()}
          </DialogDescription>
        </DialogHeader>
        <div className="flex-1 overflow-hidden rounded-b-lg">
          <div className="h-[calc(80vh-6rem)]">
            <DocumentViewer
              documentId={documentId}
              documentType={getDocumentType(documentData.mimeType)}
              mimeType={documentData.mimeType}
              fileName={isVersion ? documentData.filename : documentData.filename}
              versionNumber={isVersion ? (documentData as DocumentVersion).versionNumber : undefined}
            />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default DocumentViewerDialog;