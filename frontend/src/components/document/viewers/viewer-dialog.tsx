import React from "react";

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
  if (!documentData) return null;

  const getFileSize = () => {
    const size = documentData.fileSize / 1024;
    return size >= 1024 ? `${(size / 1024).toFixed(2)} MB` : `${size.toFixed(2)} KB`;
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