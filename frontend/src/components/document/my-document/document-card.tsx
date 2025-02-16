import { Download, Eye, Trash2 } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { LazyThumbnail } from "@/components/document/my-document/lazy-thumbnail";
import ShareDocumentDialog from "@/components/document/share-document-dialog";
import DocumentViewerDialog from "@/components/document/viewers/viewer-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";

import { DeleteDialog } from "../../common/delete-dialog";

interface DocumentCardProps {
  documentInformation: any;
  onDelete?: () => void;
  onClick?: () => void;
}

export const DocumentCard = React.memo(({ documentInformation, onDelete, onClick }: DocumentCardProps) => {
  const { t } = useTranslation();
  const { currentUser } = useAuth();
  const [showPreview, setShowPreview] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const { toast } = useToast();

  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const response = await documentService.downloadDocument(documentInformation.id, "download");
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

  const handleDeleteClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowDeleteDialog(true);
  };

  const handleConfirmDelete = async () => {
    setDeleteLoading(true);
    try {
      await onDelete?.();
      setShowDeleteDialog(false);
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <>
      <Card className="h-full flex flex-col overflow-hidden">
        <CardHeader>
          <CardTitle
            className="truncate text-base cursor-pointer hover:text-primary"
            onClick={() => onClick?.()}
          >
            {documentInformation.filename}
          </CardTitle>
        </CardHeader>
        <CardContent className="flex-1 min-h-0">
          <div className="relative w-full h-40 overflow-hidden rounded-lg">
            <LazyThumbnail documentInformation={documentInformation} />
          </div>
          {documentInformation.sharedWith.includes(currentUser?.userId) && (
            <div className="mt-4">
              <div className="flex items-center gap-2">
                <span className="text-sm text-muted-foreground">Shared by:</span>
                <span className="text-sm font-medium">{documentInformation.createdBy}</span>
              </div>
            </div>
          )}
        </CardContent>
        <CardFooter className="flex justify-center">
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              className="flex items-center justify-center w-10 h-10 p-0"
              onClick={handlePreview}
            >
              <Eye className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="flex items-center justify-center w-10 h-10 p-0"
              onClick={handleDownload}
            >
              <Download className="h-4 w-4" />
            </Button>

            {currentUser?.userId === documentInformation.userId && (
              <ShareDocumentDialog
                documentId={documentInformation.id}
                documentName={documentInformation.originalFilename}
                isShared={documentInformation.sharingType === "PUBLIC"}
                iconOnly={true}
              />
            )}

            {onDelete && currentUser?.userId === documentInformation.userId && (
              <Button
                variant="outline"
                size="sm"
                className="flex items-center justify-center w-10 h-10 p-0"
                onClick={handleDeleteClick}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            )}
          </div>
        </CardFooter>
      </Card>

      {showPreview && (
        <DocumentViewerDialog
          open={showPreview}
          onOpenChange={setShowPreview}
          documentData={documentInformation}
          documentId={documentInformation.id}
          isVersion={false}
        />
      )}

      <DeleteDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        onConfirm={handleConfirmDelete}
        loading={deleteLoading}
        description={t("document.myDocuments.delete.confirmMessage", { name: documentInformation.filename })}
      />
    </>
  );
});