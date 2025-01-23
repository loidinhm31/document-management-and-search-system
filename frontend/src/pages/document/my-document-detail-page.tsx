import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import React, { useEffect, useState } from "react";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { DocumentForm, DocumentFormValues } from "@/components/document/my-document/document-form";
import { ArrowLeft, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import DocumentViewer from "@/components/document/viewers/document-viewer";
import { RoutePaths } from "@/core/route-config";

export default function MyDocumentDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { documentId } = useParams<{ documentId: string }>();
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [documentData, setDocumentData] = useState(null);
  const { toast } = useToast();

  useEffect(() => {
    const fetchDocument = async () => {
      if (!documentId) return;

      try {
        const response = await documentService.getDocumentDetails(documentId);
        setDocumentData(response.data);
      } catch (error) {
        toast({
          title: t("common.error"),
          description: t("document.detail.fetchError"),
          variant: "destructive"
        });
        navigate("/document");
      } finally {
        setLoading(false);
      }
    };

    fetchDocument();
  }, [documentId, navigate, t, toast]);

  const handleSubmit = async (data: DocumentFormValues, file?: File) => {
    if (!documentId) return;
    setUpdating(true);

    try {
      // Update metadata
      await documentService.updateDocument(documentId, {
        courseCode: data.courseCode,
        major: data.major,
        level: data.level,
        category: data.category,
        tags: data.tags
      });

      // Update file if selected
      if (file) {
        const formData = new FormData();
        formData.append("file", file);
        await documentService.updateFile(documentId, formData);

        // Refresh document data to get new file info
        const response = await documentService.getDocumentDetails(documentId);
        setDocumentData(response.data);
      }

      toast({
        title: t("common.success"),
        description: t("document.detail.updateSuccess"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.detail.updateError"),
        variant: "destructive"
      });
    } finally {
      setUpdating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Button variant="ghost" onClick={() => navigate(RoutePaths.MY_DOCUMENT)} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        {t("document.detail.backToList")}
      </Button>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        {/* Document Form */}
        <Card>
          <CardHeader>
            <CardTitle>{t("document.detail.title")}</CardTitle>
            <CardDescription>{t("document.detail.description")}</CardDescription>
          </CardHeader>
          <CardContent>
            <DocumentForm
              initialValues={{
                courseCode: documentData?.courseCode,
                major: documentData?.major,
                level: documentData?.courseLevel,
                category: documentData?.category,
                tags: documentData?.tags || []
              }}
              onSubmit={handleSubmit}
              loading={updating}
              submitLabel={t("document.detail.buttons.update")}
            />
          </CardContent>
        </Card>

        {/* Document Preview */}
        <Card className="xl:h-[800px]">
          <CardHeader>
            <CardTitle>{documentData?.originalFilename}</CardTitle>
            <CardDescription>
              {documentData?.documentType} - {(documentData?.fileSize / 1024).toFixed(2)} KB
            </CardDescription>
          </CardHeader>
          <CardContent className="h-full max-h-[700px]">
            {documentData && (
              <DocumentViewer
                documentId={documentData.id}
                documentType={documentData.documentType}
                mimeType={documentData.mimeType}
                fileName={documentData.filename}
              />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}