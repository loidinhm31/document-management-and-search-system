import { ArrowLeft, Loader2, Trash2 } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

import { DeleteDialog } from "@/components/common/delete-dialog";
import { DocumentForm, DocumentFormValues } from "@/components/document/document-form";
import DocumentVersionHistory from "@/components/document/document-versions-history";
import ShareDocumentDialog from "@/components/document/share-document-dialog";
import DocumentViewer from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/context/auth-context";
import { useProcessing } from "@/context/processing-provider";
import { RoutePaths } from "@/core/route-config";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { useAppDispatch } from "@/store/hook";
import { setCurrentDocument } from "@/store/slices/document-slice";
import { DocumentInformation } from "@/types/document";

export default function MyDocumentDetailPage() {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const { documentId } = useParams<{ documentId: string }>();

  const { addProcessingItem } = useProcessing();

  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [documentData, setDocumentData] = useState<DocumentInformation | null>(null);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [fileChange, setFileChange] = useState<boolean>(false);
  const { toast } = useToast();

  const fetchDocument = async () => {
    if (!documentId) return;

    try {
      const response = await documentService.getDocumentDetails(documentId);
      const document = response.data;
      setDocumentData(document);
      dispatch(setCurrentDocument(document));
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.detail.fetchError"),
        variant: "destructive",
      });
      navigate("/documents/me");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDocument();

    return () => {
      dispatch(setCurrentDocument(null));
    };
  }, [documentId]);

  const handleSubmit = async (data: DocumentFormValues, file?: File) => {
    if (!documentId) return;
    setUpdating(true);

    try {
      if (file) {
        // Send both metadata and file in single request
        const formData = new FormData();
        formData.append("file", file);
        if (data.summary) {
          formData.append("summary", data.summary);
        }
        formData.append("courseCode", data.courseCode);
        formData.append("major", data.major);
        formData.append("level", data.level);
        formData.append("category", data.category);
        data.tags.forEach((tag) => formData.append("tags", tag));
        await handleFileUpdate(documentId, formData);
        setFileChange(true);
      } else {
        // Metadata-only update
        await documentService.updateDocument(documentId, {
          summary: data.summary,
          courseCode: data.courseCode,
          major: data.major,
          level: data.level,
          category: data.category,
          tags: data.tags,
        });
        setFileChange(false);
      }

      // Refresh document data
      const response = await documentService.getDocumentDetails(documentId);
      setDocumentData(response.data);

      toast({
        title: t("common.success"),
        description: t("document.detail.updateSuccess"),
        variant: "success",
      });
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.detail.updateError"),
        variant: "destructive",
      });
    } finally {
      setUpdating(false);
    }
  };

  const handleFileUpdate = async (documentId: string, formData: FormData) => {
    try {
      const response = await documentService.updateDocumentWithFile(documentId, formData);
      const document = response.data;

      // Add to processing queue
      addProcessingItem(document.id, document.originalFilename);
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.detail.updateError"),
        variant: "destructive",
      });
    }
  };

  const handleVersionUpdate = (updatedDocument: DocumentInformation) => {
    setDocumentData(updatedDocument);
  };

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  const handleDelete = async () => {
    setDeleteLoading(true);
    try {
      await documentService.deleteDocument(documentData.id);
      toast({
        title: t("common.success"),
        description: t("document.myDocuments.delete.deleteSuccess"),
        variant: "success",
      });
      setShowDeleteDialog(false);
      navigate("/documents/me");
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.myDocuments.delete.deleteError"),
        variant: "destructive",
      });
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <>
      <div className="container mx-auto py-6 space-y-6">
        <Button variant="ghost" onClick={() => navigate(RoutePaths.MY_DOCUMENT)} className="mb-4">
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t("document.detail.backToList")}
        </Button>

        <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
          {/* Document Form */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>{t("document.detail.title")}</CardTitle>
                  <CardDescription>{t("document.detail.description")}</CardDescription>
                </div>
                <div className="flex gap-2">
                  {documentData && (
                    <>
                      {currentUser?.userId === documentData.userId && (
                        <>
                          <ShareDocumentDialog
                            documentId={documentData.id}
                            documentName={documentData.filename}
                            isShared={documentData.sharingType === "PUBLIC"}
                            iconOnly={true}
                          />
                          <Button
                            variant="outline"
                            size="sm"
                            className="flex items-center justify-center w-10 h-10 p-0"
                            onClick={() => {
                              setShowDeleteDialog(true);
                            }}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </>
                      )}
                    </>
                  )}
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {documentData && (
                <DocumentVersionHistory
                  versions={documentData.versions}
                  currentVersion={documentData.currentVersion}
                  documentCreatorId={documentData.userId}
                  documentId={documentData.id}
                  onVersionUpdate={handleVersionUpdate}
                  allowRevert={true}
                />
              )}

              {documentData && (
                <DocumentForm
                  key={`df-${documentData.id}-${documentData.updatedAt}`}
                  initialValues={{
                    summary: documentData?.summary || "",
                    courseCode: documentData?.courseCode || "",
                    major: documentData?.major || "",
                    level: documentData?.courseLevel || "",
                    category: documentData?.category || "",
                    tags: documentData?.tags || [],
                  }}
                  onSubmit={handleSubmit}
                  loading={updating}
                  submitLabel={t("document.detail.buttons.update")}
                  disabled={documentData?.userId !== currentUser?.userId}
                />
              )}
            </CardContent>
          </Card>

          {/* Document Preview */}
          <Card>
            <CardHeader>
              <CardTitle>{documentData?.filename}</CardTitle>
              <CardDescription>
                {documentData?.documentType} - {(documentData?.fileSize / 1024).toFixed(3)} KB
              </CardDescription>
            </CardHeader>
            <CardContent className="h-full max-h-[1000px]">
              {documentData && (
                <DocumentViewer
                  documentId={documentData.id}
                  documentType={documentData.documentType}
                  mimeType={documentData.mimeType}
                  fileName={documentData.filename}
                  fileChange={fileChange}
                />
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {showDeleteDialog && (
        <DeleteDialog
          open={showDeleteDialog}
          onOpenChange={setShowDeleteDialog}
          onConfirm={handleDelete}
          loading={deleteLoading}
          description={t("document.myDocuments.delete.confirmMessage", { name: documentData.filename })}
        />
      )}
    </>
  );
}
