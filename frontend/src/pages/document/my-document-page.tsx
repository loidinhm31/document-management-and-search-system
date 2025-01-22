import { Loader2 } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { DocumentGrid } from "@/components/document/document-grid";
import { DocumentUpload } from "@/components/document/document-upload";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
  const { toast } = useToast();

  const fetchUserDocuments = useCallback(async (page: number = 0) => {
    setLoading(true);
    try {
      const response = await documentService.getUserDocuments(page);
      setDocuments(response.data.content);
      setTotalPages(response.data.totalPages);
      setCurrentPage(page);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.discovery.error"),
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  }, [t, toast]);

  const handleDelete = async (id: string) => {
    try {
      await documentService.deleteDocument(id);
      toast({
        title: t("common.success"),
        description: t("document.discovery.deleteSuccess"),
        variant: "success"
      });
      fetchUserDocuments(currentPage);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.discovery.deleteError"),
        variant: "destructive"
      });
    }
  };

  const handlePageChange = (page: number) => {
    fetchUserDocuments(page);
  };

  useEffect(() => {
    fetchUserDocuments(0);
  }, [fetchUserDocuments]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>{t("document.upload.title")}</CardTitle>
          <CardDescription>{t("document.upload.description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <DocumentUpload onUploadSuccess={() => fetchUserDocuments(0)} />
        </CardContent>
      </Card>

      <Card className="overflow-hidden">
        <CardHeader>
          <CardTitle>{t("document.myDocuments.title")}</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex justify-center p-8">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          ) : (
            <DocumentGrid
              documents={documents}
              currentPage={currentPage}
              totalPages={totalPages}
              onPageChange={handlePageChange}
              onDelete={handleDelete}
              loading={loading}
              onCardClick={(doc) => navigate(`/document/me/${doc.id}`)}
              className="p-4"
            />
          )}
        </CardContent>
      </Card>
    </div>
  );
}