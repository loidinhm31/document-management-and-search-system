import { Download, Eye, Trash2 } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { LazyThumbnail } from "@/components/document/my-document/lazy-thumbnail";
import ShareDocumentDialog from "@/components/document/my-document/share-document-dialog";
import { DocumentViewer } from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";

interface DocumentCardProps {
  documentInformation: any;
  onDelete?: () => void;
  isShared?: boolean;
  onClick?: () => void;
}

export const DocumentCard = React.memo(({ documentInformation, onDelete, isShared, onClick }: DocumentCardProps) => {
  const { t } = useTranslation();
  const [showPreview, setShowPreview] = useState(false);
  const { toast } = useToast();

  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const response = await documentService.downloadDocument(documentInformation.id);
      const url = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", documentInformation.filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.viewer.error.download"),
        variant: "destructive"
      });
    }
  };

  const handlePreview = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowPreview(true);
  };

  const handleDelete = (e: React.MouseEvent) => {
    e.stopPropagation();
    onDelete?.();
  };

  return (
    <Card className="h-full flex flex-col overflow-hidden" onClick={onClick}>
      <CardHeader>
        <CardTitle className="truncate text-base">{documentInformation.originalFilename}</CardTitle>
      </CardHeader>
      <CardContent className="flex-1 min-h-0">
        <div className="relative w-full h-40 overflow-hidden rounded-lg">
          <LazyThumbnail documentInformation={documentInformation} />
        </div>
        {isShared && (
          <div className="mt-4">
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Shared by:</span>
              <span className="text-sm font-medium">{documentInformation.createdBy}</span>
            </div>
          </div>
        )}
      </CardContent>
      <CardFooter onClick={(e) => e.stopPropagation()}>
        <div className="grid grid-cols-4 w-full gap-2">
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={handlePreview}
          >
            <Eye className="mr-2 h-4 w-4" />
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="w-full"
            onClick={handleDownload}
          >
            <Download className="mr-2 h-4 w-4" />
          </Button>

          {/* Share Dialog */}
          <div onClick={(e) => e.stopPropagation()}>
            <ShareDocumentDialog
              documentId={documentInformation.id}
              documentName={documentInformation.originalFilename}
              isShared={documentInformation.isShared}
              onShareToggle={(isShared) => {
                documentInformation.isShared = isShared;
              }}
            />
          </div>

          {onDelete && (
            <Button
              variant="outline"
              size="sm"
              className="w-full"
              onClick={handleDelete}
            >
              <Trash2 className="mr-2 h-4 w-4" />
            </Button>
          )}
        </div>
      </CardFooter>

      {showPreview && (
        <Dialog open={showPreview} onOpenChange={setShowPreview}>
          <DialogContent className="max-w-4xl h-[80vh]">
            <DialogHeader>
              <DialogTitle>{documentInformation.filename}</DialogTitle>
              <DialogDescription>
                {documentInformation.mimeType} - {(documentInformation.fileSize / 1024).toFixed(2)} KB
              </DialogDescription>
            </DialogHeader>
            <div className="flex-1 overflow-auto">
              <DocumentViewer
                documentId={documentInformation.id}
                documentType={documentInformation.documentType}
                mimeType={documentInformation.mimeType}
                fileName={documentInformation.filename}
              />
            </div>
          </DialogContent>
        </Dialog>
      )}
    </Card>
  );
});