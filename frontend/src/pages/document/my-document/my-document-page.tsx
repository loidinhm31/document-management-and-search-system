import { Loader2 } from "lucide-react";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import AdvancedSearch, { SearchFilters } from "@/components/document/my-document/advanced-search";
import { DocumentGrid } from "@/components/document/my-document/document-grid";
import DocumentUploadDialog from "@/components/document/my-document/document-upload-dialog";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { searchService } from "@/services/search.service";
import { useAppSelector } from "@/store/hook";
import { selectProcessingItems } from "@/store/slices/processing-slice";
import { DocumentInformation, DocumentStatus } from "@/types/document";

export default function MyDocumentPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [documents, setDocuments] = useState<DocumentInformation[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [currentFilters, setCurrentFilters] = useState<SearchFilters>({});
  const { toast } = useToast();

  const processingItems = useAppSelector(selectProcessingItems);
  const latestProcessingItem = useMemo(() =>
      processingItems.length > 0 ? processingItems[processingItems.length - 1] : null, [processingItems]);

  const fetchUserDocuments = useCallback(async (page: number = 0, filters: SearchFilters = {}) => {
    setLoading(true);
    try {
      const response = await searchService.getUserDocuments(page, 12, filters);
      setDocuments(response.data.content);
      setTotalPages(response.data.totalPages);
      setCurrentPage(page);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.myDocuments.search.error"),
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  }, []);

  const handleSearch = (filters: SearchFilters) => {
    setCurrentFilters(filters);
    setCurrentPage(0);
    fetchUserDocuments(0, filters);
  };

  const handleDelete = async (id: string) => {
    try {
      await documentService.deleteDocument(id);
      toast({
        title: t("common.success"),
        description: t("document.myDocuments.delete.deleteSuccess"),
        variant: "success"
      });
      fetchUserDocuments(currentPage, currentFilters);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.myDocuments.delete.deleteError"),
        variant: "destructive"
      });
    }
  };

  const handlePageChange = (page: number) => {
    fetchUserDocuments(page, currentFilters);
  };

  useEffect(() => {
    fetchUserDocuments(0);
  }, [fetchUserDocuments]);

  useEffect(() => {
    if (latestProcessingItem?.status === DocumentStatus.COMPLETED) {
      // Refresh the document list with current filters and page
      fetchUserDocuments(currentPage, currentFilters);
    }
  }, [latestProcessingItem?.status, currentPage, currentFilters, fetchUserDocuments]);

  return (
    <div className="space-y-6">
      <Card className="overflow-hidden">
        <CardHeader>
          <div className="space-y-4">
            <CardTitle>{t("document.myDocuments.title")}</CardTitle>
            <DocumentUploadDialog
              onUploadSuccess={() => {
                // Reset to first page when new document is uploaded
                setCurrentPage(0);
                fetchUserDocuments(0, currentFilters)
              }} />
          </div>
        </CardHeader>
        <CardContent>
          <AdvancedSearch onSearch={handleSearch} />

          {loading ? (
            <div className="flex justify-center p-8">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          ) : (
            <>
              <DocumentGrid
                documents={documents}
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={handlePageChange}
                onDelete={handleDelete}
                loading={loading}
                onCardClick={(doc) => navigate(`/documents/me/${doc.id}`)}
                className="pt-4"
              />
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}