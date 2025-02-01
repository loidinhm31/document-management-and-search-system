import React from "react";
import { Clock, MoreHorizontal, User } from "lucide-react";
import { useTranslation } from "react-i18next";

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { ScrollArea } from "@/components/ui/scroll-area";
import { DocumentStatus, DocumentVersion } from "@/types/document";

interface VersionHistoryProps {
  versions: DocumentVersion[];
  currentVersion: number;
  onViewVersion: (version: number) => void;
  onDownloadVersion: (version: number) => void;
}

const DocumentVersionHistory = ({
                          versions,
                          currentVersion,
                          onViewVersion,
                          onDownloadVersion,
                        }: VersionHistoryProps) => {
  const { t } = useTranslation();

  const getStatusColor = (status: DocumentStatus) => {
    switch (status) {
      case DocumentStatus.COMPLETED:
        return "bg-green-50 text-green-700 ring-green-600/20";
      case DocumentStatus.PROCESSING:
        return "bg-yellow-50 text-yellow-700 ring-yellow-600/20";
      case DocumentStatus.FAILED:
        return "bg-red-50 text-red-700 ring-red-600/20";
      default:
        return "bg-gray-50 text-gray-700 ring-gray-600/20";
    }
  };

  const formatDate = (date: Date) => {
    return new Date(date).toLocaleString();
  };

  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  };

  const sortedVersions = [...versions].sort(
    (a, b) => b.versionNumber - a.versionNumber
  );

  return (
    <Accordion type="single" collapsible className="mt-6">
      <AccordionItem value="version-history">
        <AccordionTrigger className="flex items-center gap-2 px-4">
          <div className="flex flex-1 items-center gap-2">
            <Clock className="h-4 w-4" />
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
                          number: version.versionNumber + 1,
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

                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-8 w-8">
                          <MoreHorizontal className="h-4 w-4" />
                          <span className="sr-only">Actions</span>
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end" className="w-48">
                        {version.versionNumber !== currentVersion && (
                          <DropdownMenuItem onClick={() => onViewVersion(version.versionNumber)}>
                            {t("document.versions.actions.view")}
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuItem onClick={() => onDownloadVersion(version.versionNumber)}>
                          {t("document.versions.actions.download")}
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
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
                    <span>
                      {version.originalFilename}
                    </span>
                  </div>
                  <div className="mt-2 flex flex-wrap gap-4 text-sm text-muted-foreground">
                    <span>
                      {version.mimeType} • {formatFileSize(version.fileSize)}
                    </span>
                  </div>

                  {/* Error Message */}
                  {version.processingError && (
                    <div className="mt-2 text-sm text-destructive">
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