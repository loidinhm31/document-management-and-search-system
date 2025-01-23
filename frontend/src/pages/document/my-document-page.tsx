import { Loader2 } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import AdvancedSearch, { SearchFilters } from "@/components/document/my-document/advanced-search";
import { DocumentGrid } from "@/components/document/my-document/document-grid";
import DocumentUploadDialog from "@/components/document/my-document/document-upload-dialog";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { DocumentInformation } from "@/types/document";

export default function MyDocumentPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [documents, setDocuments] = useState<DocumentInformation[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [currentFilters, setCurrentFilters] = useState<SearchFilters>({});
  const { toast } = useToast();

  const fetchUserDocuments = useCallback(async (page: number = 0, filters: SearchFilters = {}) => {
    setLoading(true);
    try {
      const response = await documentService.getUserDocuments(page, 12, filters);
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
  }, [t, toast]);

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

  return (
    <div className="space-y-6">
      <Card className="overflow-hidden">
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>{t("document.myDocuments.title")}</CardTitle>
          <DocumentUploadDialog onUploadSuccess={() => fetchUserDocuments(0, currentFilters)} />
        </CardHeader>
        <CardContent>
          <AdvancedSearch onSearch={handleSearch} />

          {loading ? (
            <div className="flex justify-center p-8">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          ) : (
            <>
              {documents.length > 0 && (
                <DocumentGrid
                  documents={documents}
                  currentPage={currentPage}
                  totalPages={totalPages}
                  onPageChange={handlePageChange}
                  onDelete={handleDelete}
                  loading={loading}
                  onCardClick={(doc) => navigate(`/document/me/${doc.id}`)}
                  className="pt-4"
                />
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}