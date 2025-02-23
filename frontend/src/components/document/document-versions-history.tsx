import { AlertCircle, Clock, History, Loader2, User } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import DocumentViewerDialog from "@/components/document/viewers/viewer-dialog";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useAuth } from "@/context/auth-context";
import { toast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { useAppSelector } from "@/store/hook";
import { selectProcessingItemByDocumentId } from "@/store/slices/processing-slice";
import { DocumentInformation, DocumentStatus, DocumentVersion } from "@/types/document";

interface VersionHistoryProps {
  versions: DocumentVersion[];
  currentVersion: number;
  documentCreatorId?: string;
  documentId?: string;
  onVersionUpdate?: (updatedDocument: DocumentInformation) => void;
  allowRevert?: boolean;
  onDownloadSuccess?: () => void;
}

const DocumentVersionHistory: React.FC<VersionHistoryProps> = ({
  versions,
  currentVersion,
  documentCreatorId,
  documentId,
  onVersionUpdate,
  allowRevert = false,
  onDownloadSuccess,
}) => {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const { currentUser } = useAuth();
  const isDocumentCreator = currentUser?.userId === documentCreatorId;
  const [selectedVersion, setSelectedVersion] = useState<DocumentVersion | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);

  const documentProcessingStatus = useAppSelector(selectProcessingItemByDocumentId(documentId));

  const fetchDocumentDetails = useCallback(async () => {
    if (!documentId) return;

    setLoading(true);
    try {
      const response = await documentService.getDocumentDetails(documentId);

      // Update parent component with new document data
      if (onVersionUpdate) {
        onVersionUpdate(response.data);
      }
    } catch (error) {
      console.error("Error fetching document details:", error);
    } finally {
      setLoading(false);
    }
  }, [documentId, onVersionUpdate, t, toast]);

  useEffect(() => {
    if (documentProcessingStatus?.status === DocumentStatus.COMPLETED) {
      fetchDocumentDetails();
    }
  }, [documentProcessingStatus?.status]);

  const handleOpenViewVersion = (version: DocumentVersion) => {
    setSelectedVersion(version);
  };

  const handleVersionDownload = async (versionNumber: number, filename: string) => {
    try {
      setIsDownloading(true);
      const response = await documentService.downloadDocumentVersion({
        documentId,
        versionNumber,
        action: "download",
        history: true,
      });
      const url = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);

      // Tracking download
      onDownloadSuccess?.();
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.versions.error.download"),
        variant: "destructive",
      });
    } finally {
      setIsDownloading(false);
    }
  };

  const handleRevertVersion = async (versionNumber: number) => {
    if (!isDocumentCreator) return;

    setLoading(true);
    try {
      const response = await documentService.revertToVersion(documentId, versionNumber);

      // Call the callback with updated document
      if (onVersionUpdate) {
        onVersionUpdate(response.data);
      }

      toast({
        title: t("common.success"),
        description: t("document.versions.revertSuccess"),
        variant: "success",
      });
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.versions.error.revertError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status.toLowerCase()) {
      case "completed":
        return "bg-green-50 text-green-700 ring-green-600/20";
      case "processing":
      case "pending":
        return "bg-yellow-50 text-yellow-700 ring-yellow-600/20";
      case "failed":
        return "bg-red-50 text-red-700 ring-red-600/20";
      default:
        return "bg-gray-50 text-gray-700 ring-gray-600/20";
    }
  };

  const formatDate = (date: Date) => {
    return new Date(date).toLocaleString();
  };

  const sortedVersions = [...versions].sort((a, b) => b.versionNumber - a.versionNumber);

  return (
    <>
      <Accordion type="single" collapsible>
        <AccordionItem value="version-history">
          <AccordionTrigger className="flex items-center gap-2">
            <div className="flex flex-1 items-center gap-2">
              <History className="h-4 w-4" />
              <span>
                {t("document.versions.title")} ({t("document.versions.total", { total: versions.length })})
              </span>
            </div>
          </AccordionTrigger>
          <AccordionContent>
            <ScrollArea className="h-[325px] rounded-md border">
              <div className="space-y-1 p-3">
                {sortedVersions.map((version) => (
                  <div
                    key={version.versionNumber}
                    className="relative flex flex-col gap-1.5 rounded-lg border bg-card p-2 shadow-sm transition-colors hover:bg-accent/5"
                  >
                    {/* Version Header - Made responsive */}
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-medium text-sm">
                          {t("document.versions.versionNumber", {
                            number: version.versionNumber + 1,
                          })}
                        </span>
                        {version.versionNumber === currentVersion && (
                          <span className="rounded bg-primary/10 px-1.5 py-0.5 text-xs text-primary">
                            {t("document.versions.current")}
                          </span>
                        )}
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${getStatusColor(
                            version.status,
                          )}`}
                        >
                          {t(`document.versions.status.${version.status.toLowerCase()}`)}
                        </span>
                      </div>

                      {/* Action Buttons - Wrap on small screens */}
                      <div className="flex flex-wrap items-center gap-1">
                        {allowRevert && version.versionNumber !== currentVersion && isDocumentCreator && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleRevertVersion(version.versionNumber)}
                            disabled={loading}
                            className="h-7 w-full sm:w-auto"
                          >
                            {loading && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
                            {t("document.versions.actions.revert")}
                          </Button>
                        )}
                        {version.versionNumber !== currentVersion && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenViewVersion(version)}
                            className="h-7 w-full sm:w-auto"
                          >
                            {t("document.versions.actions.view")}
                          </Button>
                        )}
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleVersionDownload(version.versionNumber, version.filename)}
                          className="h-7 w-full sm:w-auto"
                          disabled={isDownloading || loading}
                        >
                          {!isDownloading ? t("document.versions.actions.download") : t("document.versions.actions.downloading")}
                        </Button>
                      </div>
                    </div>

                    {/* Version Details - Made responsive */}
                    <div className="grid grid-cols-1 gap-2 text-xs sm:grid-cols-2">
                      <div className="flex items-center gap-1 text-muted-foreground">
                        <User className="h-3 w-3" />
                        <span className="truncate">{version.createdBy}</span>
                      </div>
                      <div className="flex items-center gap-1 text-muted-foreground">
                        <Clock className="h-3 w-3" />
                        <span>{formatDate(version.createdAt)}</span>
                      </div>
                    </div>

                    {/* File Details - Made responsive with truncation */}
                    <div className="mt-1 flex flex-wrap gap-2 text-xs text-muted-foreground">
                      <span className="truncate max-w-full sm:max-w-[200px]">{version.filename}</span>
                      <span className="whitespace-nowrap">
                        {version.mimeType} • {(version.fileSize / 1024).toFixed(3)} KB
                      </span>
                    </div>

                    {/* Error Message - Made responsive */}
                    {version.processingError && (
                      <div className="mt-1 flex items-center gap-1 text-xs text-destructive">
                        <AlertCircle className="h-3 w-3 shrink-0" />
                        <span className="break-words">{version.processingError}</span>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </ScrollArea>
          </AccordionContent>
        </AccordionItem>
      </Accordion>

      {documentId && (
        <DocumentViewerDialog
          open={!!selectedVersion}
          onOpenChange={(open) => !open && setSelectedVersion(null)}
          documentData={selectedVersion}
          documentId={documentId}
          isVersion={true}
        />
      )}
    </>
  );
};

export default DocumentVersionHistory;
