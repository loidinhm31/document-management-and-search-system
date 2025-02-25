import React, { useEffect } from "react";

import { DocumentViewer } from "@/components/document/viewers/document-viewer";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { DocumentInformation, DocumentVersion } from "@/types/document";

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
  useEffect(() => {
    if (open) {
      // Prevent right-click context menu
      const handleContextMenu = (e: MouseEvent) => {
        e.preventDefault();
      };

      // Prevent copy/cut
      const handleCopy = (e: ClipboardEvent) => {
        e.preventDefault();
      };

      // Prevent keyboard shortcuts
      const handleKeyDown = (e: KeyboardEvent) => {
        if ((e.ctrlKey || e.metaKey) && (e.key === "c" || e.key === "C" || e.key === "x" || e.key === "X")) {
          e.preventDefault();
        }
      };

      document.addEventListener("contextmenu", handleContextMenu);
      document.addEventListener("copy", handleCopy);
      document.addEventListener("cut", handleCopy);
      document.addEventListener("keydown", handleKeyDown);

      return () => {
        document.removeEventListener("contextmenu", handleContextMenu);
        document.removeEventListener("copy", handleCopy);
        document.removeEventListener("cut", handleCopy);
        document.removeEventListener("keydown", handleKeyDown);
      };
    }
  }, [open]);

  if (!documentData) return null;

  const getFileSize = () => {
    const size = documentData.fileSize / 1024;
    return size >= 1024 ? `${(size / 1024).toFixed(3)} MB` : `${size.toFixed(3)} KB`;
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="flex max-w-4xl flex-col gap-0 p-0 select-none"
        onCopy={(e) => e.preventDefault()}
        onCut={(e) => e.preventDefault()}
        onPaste={(e) => e.preventDefault()}
        style={{ WebkitUserSelect: "none", MozUserSelect: "none", msUserSelect: "none", userSelect: "none" }}
      >
        <DialogHeader className="px-6 py-4">
          <DialogTitle className="pr-8">{documentData?.filename}</DialogTitle>
          <DialogDescription className="text-sm">
            {isVersion && `Version ${(documentData as DocumentVersion).versionNumber + 1} - `}
            {documentData.mimeType} - {getFileSize()}
          </DialogDescription>
        </DialogHeader>
        <div className="flex-1 overflow-hidden rounded-b-lg">
          <div className="h-[calc(80vh-6rem)]">
            <DocumentViewer
              documentId={documentId}
              documentType={documentData.documentType}
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
