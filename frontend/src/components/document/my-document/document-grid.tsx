import { Loader2 } from "lucide-react";
import React from "react";
import { useTranslation } from "react-i18next";

import { DocumentCard } from "@/components/document/my-document/document-card";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { DocumentInformation } from "@/types/document";

interface DocumentGridProps {
  documents: DocumentInformation[];
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onDelete?: (id: string) => void;
  loading?: boolean;
  onCardClick?: (doc: DocumentInformation) => void;
  className?: string;
}

export const DocumentGrid = React.memo(({
                                          documents,
                                          currentPage,
                                          totalPages,
                                          onPageChange,
                                          onDelete,
                                          loading = false,
                                          onCardClick,
                                          className
                                        }: DocumentGridProps) => {
  const { t } = useTranslation();

  if (loading) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className={cn("space-y-6", className)}>
      <div className="grid grid-cols-1 auto-rows-fr gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {documents.map((doc) => (
          <DocumentCard
            key={doc.id}
            documentInformation={doc}
            onDelete={onDelete ? () => onDelete(doc.id) : undefined}
            onClick={() => onCardClick?.(doc)}
          />
        ))}
      </div>

      <div className="mt-6 flex justify-center gap-2">
        <Button
          variant="outline"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage === 0}
        >
          {t("document.discover.pagination.previous")}
        </Button>
        <span className="flex items-center px-4">
            {t("document.discover.pagination.pageInfo", {
              current: documents.length > 0 ? currentPage + 1 : 0,
              total: totalPages
            })}
        </span>
        <Button
          variant="outline"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage === totalPages - 1}
        >
          {t("document.discover.pagination.next")}
        </Button>
      </div>
    </div>
  );
});