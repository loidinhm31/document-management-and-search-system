import { AlertCircle, ChevronDown, ChevronUp, Clock, Download, History, Loader2, User, X } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { toast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger
} from "@/components/ui/accordion";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useAuth } from "@/context/auth-context";
import { DocumentVersion } from "@/types/document";

interface VersionHistoryProps {
  versions: DocumentVersion[];
  currentVersion: number;
  onViewVersion: (version: number) => void;
  onDownloadVersion: (version: number) => void;
  documentCreator?: string;
  documentId?: string;
}

const DocumentVersionHistory: React.FC<VersionHistoryProps> = ({
                                                                 versions,
                                                                 currentVersion,
                                                                 onViewVersion,
                                                                 onDownloadVersion,
                                                                 documentCreator,
                                                                 documentId
                                                               }) => {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const { currentUser } = useAuth();
  const isDocumentCreator = currentUser?.username === documentCreator;

  const handleRevertVersion = async (versionNumber: number) => {
    if (!isDocumentCreator) return;

    setLoading(true);
    try {
      await documentService.revertToVersion(documentId, versionNumber);
      toast({
        title: t("common.success"),
        description: t("document.versions.revertSuccess"),
        variant: "success"
      });
      window.location.reload(); // Refresh to show changes
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.versions.error.revertError"),
        variant: "destructive"
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
          <ScrollArea className="h-[400px] rounded-md border">
            <div className="space-y-4 p-4">
              {sortedVersions.map((version) => (
                <div
                  key={version.versionNumber}
                  className="relative flex flex-col gap-2 rounded-lg border bg-card p-4 shadow-sm transition-colors hover:bg-accent/5"
                >
                  {/* Version Header */}
                  <div className="flex items-center justify-between gap-4">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">
                        {t("document.versions.versionNumber", {
                          number: version.versionNumber + 1
                        })}
                      </span>
                      {version.versionNumber === currentVersion && (
                        <span className="rounded bg-primary/10 px-2 py-0.5 text-xs text-primary">
                          {t("document.versions.current")}
                        </span>
                      )}
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ring-1 ring-inset ${getStatusColor(
                          version.status
                        )}`}
                      >
                        {t(`document.versions.status.${version.status.toLowerCase()}`)}
                      </span>
                    </div>

                    <div className="flex items-center gap-2">
                      {version.versionNumber !== currentVersion && isDocumentCreator && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleRevertVersion(version.versionNumber)}
                          disabled={loading}
                        >
                          {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                          {t("document.versions.actions.revert")}
                        </Button>
                      )}
                      {version.versionNumber !== currentVersion && (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onViewVersion(version.versionNumber)}
                        >
                          {t("document.versions.actions.view")}
                        </Button>
                      )}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onDownloadVersion(version.versionNumber)}
                      >
                        {t("document.versions.actions.download")}
                      </Button>
                    </div>
                  </div>

                  {/* Version Details */}
                  <div className="mt-2 grid grid-cols-1 gap-4 text-sm md:grid-cols-2">
                    <div className="flex items-center gap-2 text-muted-foreground">
                      <User className="h-4 w-4" />
                      <span>{version.createdBy}</span>
                    </div>
                    <div className="flex items-center gap-2 text-muted-foreground">
                      <Clock className="h-4 w-4" />
                      <span>{formatDate(version.createdAt)}</span>
                    </div>
                  </div>

                  {/* File Details */}
                  <div className="mt-2 flex flex-wrap gap-4 text-sm text-muted-foreground">
                    <span>{version.originalFilename}</span>
                    <span>
                      {version.mimeType} • {(version.fileSize / 1024).toFixed(2)} KB
                    </span>
                  </div>

                  {/* Error Message */}
                  {version.processingError && (
                    <div className="mt-2 flex items-center gap-2 text-sm text-destructive">
                      <AlertCircle className="h-4 w-4" />
                      {version.processingError}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </ScrollArea>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
};

export default DocumentVersionHistory;