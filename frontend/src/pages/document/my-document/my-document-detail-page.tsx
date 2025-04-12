import {
  ArrowLeft,
  CalendarPlus2Icon,
  FilePenLineIcon,
  FileType2Icon,
  Loader2,
  Package2Icon,
  Trash2,
} from "lucide-react";
import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import { v4 as uuidv4 } from "uuid";

import { DeleteDialog } from "@/components/common/delete-dialog";
import { DocumentForm } from "@/components/document/document-form";
import DocumentVersionHistory from "@/components/document/document-versions-history";
import ShareDocumentDialog from "@/components/document/share-document-dialog";
import DocumentViewer from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/context/auth-context";
import { RoutePaths } from "@/core/route-config";
import { useToast } from "@/hooks/use-toast";
import { getDescriptionType } from "@/lib/utils";
import { DocumentFormValues } from "@/schemas/document-schema";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { setCurrentDocument } from "@/store/slices/document-slice";
import { addProcessingItem, selectProcessingItems } from "@/store/slices/processing-slice";
import { DocumentInformation, DocumentStatus, ProcessingItem } from "@/types/document";

export default function MyDocumentDetailPage() {
  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const { documentId } = useParams<{ documentId: string }>();

  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [documentData, setDocumentData] = useState<DocumentInformation | null>(null);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const { toast } = useToast();
  const [pollingItem, setPollingItem] = useState<ProcessingItem>();
  const [fileChange, setFileChange] = useState<boolean>(false);

  const processingItems = useAppSelector(selectProcessingItems);
  const latestProcessingItem = useMemo(
    () => (processingItems.length > 0 ? processingItems[processingItems.length - 1] : null),
    [processingItems],
  );

  // Prevent access share document public for my document
  useEffect(() => {
    if (
      currentUser?.userId &&
      documentData &&
      documentData.userId !== currentUser.userId &&
      !documentData?.sharedWith.includes(currentUser.userId)
    ) {
      toast({
        title: t("common.error"),
        description: t("document.detail.fetchError"),
        variant: "destructive",
      });
      navigate("/documents/me");
    }
  }, [documentData, currentUser]);

  useEffect(() => {
    if (latestProcessingItem) {
      setPollingItem(latestProcessingItem);
      if (latestProcessingItem?.status === DocumentStatus.COMPLETED) {
        fetchDocument();
        setFileChange(true);
      }
    }
  }, [latestProcessingItem?.status, documentId]);

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

    let updateResponse;
    try {
      if (file) {
        // Send both metadata and file in single request
        const formData = new FormData();
        formData.append("file", file);

        if (data.summary) {
          formData.append("summary", data.summary);
        }

        // Handle majors (multiple values)
        if (data.majors && data.majors.length > 0) {
          data.majors.forEach((major: string) => formData.append("majors", major));
        }

        // Handle course codes (multiple values)
        if (data.courseCodes && data.courseCodes.length > 0) {
          data.courseCodes.forEach((courseCode: string) => formData.append("courseCodes", courseCode));
        }

        // Single value fields
        formData.append("level", data.level);

        // Handle categories (multiple values)
        if (data.categories && data.categories.length > 0) {
          data.categories.forEach((category: string) => formData.append("categories", category));
        }

        // Tags (multiple values)
        if (data.tags && data.tags.length > 0) {
          data.tags.forEach((tag: string) => formData.append("tags", tag));
        }

        updateResponse = await handleFileUpdate(documentId, formData);
      } else {
        // Metadata-only update
        updateResponse = await documentService.updateDocument(documentId, {
          summary: data.summary,
          majors: data.majors,
          courseCodes: data.courseCodes,
          level: data.level,
          categories: data.categories,
          tags: data.tags,
        });
      }

      if (updateResponse && updateResponse.data) {
        // Update the document data with the new response
        const updatedDocument = updateResponse.data;
        setDocumentData(updatedDocument);
        dispatch(setCurrentDocument(updatedDocument));

        toast({
          title: t("common.success"),
          description: t("document.detail.updateSuccess"),
          variant: "success",
        });
      }
    } catch (error) {
      console.error(error);
    } finally {
      setUpdating(false);
    }
  };

  const handleFileUpdate = async (documentId: string, formData: FormData) => {
    try {
      const response = await documentService.updateDocumentWithFile(documentId, formData);
      const document = response.data;

      // Add to processing queue using Redux directly
      dispatch(
        addProcessingItem({
          id: uuidv4(),
          documentId: document.id,
          filename: document.filename,
          status: DocumentStatus.PENDING,
          addedAt: new Date().getTime(),
        }),
      );

      return response;
    } catch (error) {
      console.log(error);
      throw error;
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

  // Format dates for display
  const formatDate = (date: Date) => {
    if (!date) return "";
    const formatDate = new Date(date);
    return formatDate.toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
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
                    majors: documentData?.majors || [],
                    courseCodes: documentData?.courseCodes || [],
                    level: documentData?.courseLevel || "",
                    categories: documentData?.categories || [],
                    tags: documentData?.tags || [],
                  }}
                  onSubmit={handleSubmit}
                  loading={updating}
                  submitLabel={t("document.detail.buttons.update")}
                  disabled={documentData?.userId !== currentUser?.userId}
                  documentId={documentData.id}
                  pollingItem={pollingItem}
                />
              )}
            </CardContent>
          </Card>

          {/* Document Preview */}
          <Card>
            <CardHeader>
              <div>
                <CardTitle className="truncate mb-2">{documentData?.filename}</CardTitle>
                <div className="grid grid-cols-2 gap-y-2 text-sm text-muted-foreground">
                  <div className="flex items-center gap-1.5">
                    <FileType2Icon className="mr-2 h-4 w-4" />
                    <span>{getDescriptionType(documentData?.documentType)}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Package2Icon className="mr-2 h-4 w-4" />
                    <span>{(documentData?.fileSize / 1024).toFixed(3)} KB</span>
                  </div>
                  {documentData?.createdAt && (
                    <div className="flex items-center gap-1.5">
                      <CalendarPlus2Icon className="mr-2 h-4 w-4" />
                      <span>
                        {t("common.created")}: {formatDate(documentData.createdAt)}
                      </span>
                    </div>
                  )}
                  {documentData?.updatedAt && (
                    <div className="flex items-center gap-1.5">
                      <FilePenLineIcon className="mr-2 h-4 w-4" />
                      <span>
                        {t("common.updated")}: {formatDate(documentData.updatedAt)}
                      </span>
                    </div>
                  )}
                </div>
              </div>
            </CardHeader>
            <CardContent className="h-full max-h-[900px]">
              {documentData && (
                <DocumentViewer
                  documentId={documentData.id}
                  documentType={documentData.documentType}
                  mimeType={documentData.mimeType}
                  fileName={documentData.filename}
                  fileChange={fileChange}
                  setFileChange={setFileChange}
                  documentStatus={documentData.status === DocumentStatus.PROCESSING ? documentData.status : null}
                  bypass={true}
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
