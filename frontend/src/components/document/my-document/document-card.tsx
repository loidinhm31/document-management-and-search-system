import { Download, Eye } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { LazyThumbnail } from "@/components/document/my-document/lazy-thumbnail";
import DocumentViewerDialog from "@/components/document/viewers/viewer-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { DocumentInformation } from "@/types/document";
import moment from "moment-timezone";
import { Separator } from "@/components/ui/separator";

interface DocumentCardProps {
  documentInformation: DocumentInformation;
  onClick?: () => void;
}

export const DocumentCard = React.memo(({ documentInformation, onClick }: DocumentCardProps) => {
  const { t } = useTranslation();
  const { currentUser } = useAuth();
  const [showPreview, setShowPreview] = useState(false);

  const { toast } = useToast();

  const handleDownload = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const response = await documentService.downloadDocument({
        id: documentInformation.id,
        action: "download",
        history: true,
      });
      const url = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", documentInformation.filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.viewer.error.download"),
        variant: "destructive",
      });
    }
  };

  const handlePreview = (e: React.MouseEvent) => {
    e.stopPropagation();
    setShowPreview(true);
  };

  return (
    <>
      <Card className="h-full flex flex-col overflow-hidden">
        <CardHeader>
          <CardTitle className="truncate text-base cursor-pointer hover:text-primary" onClick={() => onClick?.()}>
            {documentInformation.filename}
          </CardTitle>
          <Separator />
        </CardHeader>

        <CardContent className="flex-1 min-h-0">
          <div className="relative w-full h-40 overflow-hidden rounded-lg">
            <LazyThumbnail documentInformation={documentInformation} />
          </div>
          <div className="mt-2">
            <div className="mt-2 flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Created at:</span>
              <span className="text-sm font-semibold">
                {moment(documentInformation?.createdAt).format("DD/MM/YYYY, h:mm a")}
              </span>
            </div>
            <div className="mt-2 flex items-center gap-2">
              <span className="text-sm text-muted-foreground">File size:</span>
              <span className="text-sm font-semibold">
                {(documentInformation.fileSize / (1024 * 1024)).toFixed(3)} MB
              </span>
            </div>
            <div className="mt-2 flex items-center gap-2">
              <span className="text-sm text-muted-foreground">Language:</span>
              <span className="text-sm font-semibold">
                {(documentInformation.language)}
              </span>
            </div>
            {documentInformation.sharedWith.includes(currentUser?.userId) && (
              <div className="mt-2 flex items-center gap-2">
                <span className="text-sm text-muted-foreground">Shared by:</span>
                <span className="text-sm font-semibold">{documentInformation.createdBy}</span>
              </div>
            )}
          </div>
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
    </>
  );
});
