import { ChevronLeft, ChevronRight } from "lucide-react";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { LazyThumbnail } from "@/components/document/my-document/lazy-thumbnail";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";
import { getMasterDataTranslation } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { useAppSelector } from "@/store/hook";
import { selectMasterData } from "@/store/slices/masterDataSlice";
import { DocumentInformation } from "@/types/document";
import { Label } from "@radix-ui/react-label";

interface RelatedDocumentsProps {
  documentId: string;
  onDocumentClick?: (document: DocumentInformation) => void;
}

export function RelatedDocuments({
                                   documentId,
                                   onDocumentClick
                                 }: RelatedDocumentsProps) {
  const { t } = useTranslation();
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const { majors, categories } = useAppSelector(selectMasterData);
  const { toast } = useToast();

  const [documents, setDocuments] = useState<DocumentInformation[]>([]);
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [currentIndex, setCurrentIndex] = useState(0);

  const ITEMS_PER_PAGE = 6;
  const VISIBLE_ITEMS = 4; // Number of items visible at once

  const fetchInitialDocuments = useCallback(async () => {
    if (!documentId) return;

    setLoading(true);
    try {
      const response = await documentService.getRecommendationDocuments(documentId, ITEMS_PER_PAGE, 0);
      const newDocs = response.data.content;

      setDocuments(newDocs);
      setHasMore(newDocs.length === ITEMS_PER_PAGE);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.related.error"),
        variant: "destructive"
      });
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }, [documentId, t, toast]);

  const fetchMoreDocuments = useCallback(async () => {
    if (!documentId || !hasMore || loading) return;

    setLoading(true);
    try {
      const response = await documentService.getRecommendationDocuments(documentId, ITEMS_PER_PAGE, currentPage);
      const newDocs = response.data.content;

      if (!newDocs.length) {
        setHasMore(false);
        return;
      }

      setDocuments(prev => {
        const existingIds = new Set(prev.map(doc => doc.id));
        const uniqueNewDocs = newDocs.filter(doc => !existingIds.has(doc.id));
        return [...prev, ...uniqueNewDocs];
      });
      setHasMore(newDocs.length === ITEMS_PER_PAGE);
    } catch (error) {
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }, [documentId, currentPage, hasMore, loading, t, toast]);

  useEffect(() => {
    console.log("Document ID changed, resetting state");
    setDocuments([]);
    setCurrentPage(0);
    setHasMore(true);
    setLoading(false);
    setCurrentIndex(0);
    fetchInitialDocuments();
  }, [documentId, fetchInitialDocuments]);

  useEffect(() => {
    if (currentPage > 0) {
      console.log("Page changed, fetching more:", currentPage);
      fetchMoreDocuments();
    }
  }, [currentPage, fetchMoreDocuments]);

  const handleScroll = useCallback((direction: "left" | "right") => {
    const newIndex = direction === "left"
      ? Math.max(0, currentIndex - VISIBLE_ITEMS)
      : currentIndex + VISIBLE_ITEMS;

    console.log("Handle scroll:", {
      direction,
      currentIndex,
      newIndex,
      documentsLength: documents.length
    });

    setCurrentIndex(newIndex);

    // Load more data if needed
    if (direction === "right" && newIndex + VISIBLE_ITEMS >= documents.length && hasMore) {
      setCurrentPage(prev => prev + 1);
    }
  }, [currentIndex, documents.length, hasMore]);

  if (!documents.length && !loading) return null;

  const showLeftButton = currentIndex > 0;
  const showRightButton = currentIndex + VISIBLE_ITEMS < documents.length || hasMore;

  return (
    <div className="relative w-full">
      <div className="flex items-center justify-between mb-4">
        <div className="px-6">
          <Label className="text-2xl font-semibold">{t("document.related.title")}</Label>
        </div>
      </div>

      <div className="relative">
        {/* Navigation buttons */}
        {showLeftButton && (
          <Button
            variant="outline"
            size="icon"
            className="absolute left-0 top-1/2 -translate-y-1/2 z-10 bg-background"
            onClick={() => handleScroll("left")}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
        )}

        {showRightButton && (
          <Button
            variant="outline"
            size="icon"
            className="absolute right-0 top-1/2 -translate-y-1/2 z-10 bg-background"
            onClick={() => handleScroll("right")}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        )}

        {/* Content container */}
        <div className="overflow-hidden mx-8">
          <div
            ref={scrollContainerRef}
            className="flex gap-4 transition-transform duration-300 ease-in-out"
            style={{
              transform: `translateX(-${currentIndex * (280 + 16)}px)` // card width (280px) + gap (16px)
            }}
          >
            {documents.map((doc) => (
              <Card
                key={doc.id}
                className="flex-none w-64 cursor-pointer transition-transform hover:scale-105"
                onClick={() => onDocumentClick?.(doc)}
              >
                <div className="p-3 space-y-2">
                  <div className="aspect-video relative overflow-hidden rounded-md">
                    <LazyThumbnail documentInformation={doc} />
                  </div>
                  <div>
                    <h4 className="font-medium line-clamp-2 text-sm">
                      {doc.filename}
                    </h4>
                    <p className="text-xs text-muted-foreground mt-1">
                      {getMasterDataTranslation(doc.major, "major", { majors })} -{" "}
                      {getMasterDataTranslation(doc.category, "category", { categories })}
                    </p>
                  </div>
                </div>
              </Card>
            ))}
            {loading && (
              <div className="flex-none w-64 flex items-center justify-center">
                <div className="animate-pulse">Loading...</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}