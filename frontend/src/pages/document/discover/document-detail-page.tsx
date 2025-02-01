import { ArrowLeft, Bookmark, BookmarkPlus, Calendar, FileBox, Loader2, User } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

import DocumentVersionHistory from "@/components/document/document-versions";
import ShareDocumentDialog from "@/components/document/my-document/share-document-dialog";
import DocumentViewer from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { getMasterDataTranslation } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/masterDataSlice";
import { DocumentInformation } from "@/types/document";

export default function DocumentDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { documentId } = useParams<{ documentId: string }>();
  const { currentUser } = useAuth();
  const { toast } = useToast();
  const dispatch = useAppDispatch();

  const [loading, setLoading] = useState(true);
  const [documentData, setDocumentData] = useState<DocumentInformation | null>(null);
  const [isBookmarked, setIsBookmarked] = useState(false);

  const { majors, levels, categories, loading: masterDataLoading } = useAppSelector(selectMasterData);

  useEffect(() => {
    // Only fetch master data if not already loaded
    if (majors.length === 0 || levels.length === 0 || categories.length === 0) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, majors.length, levels.length, categories.length]);

  useEffect(() => {
    const fetchDocument = async () => {
      if (!documentId) return;

      try {
        const [docResponse, bookmarkResponse] = await Promise.all([
          documentService.getDocumentDetails(documentId),
          documentService.isDocumentBookmarked(documentId)
        ]);

        setDocumentData(docResponse.data);
        setIsBookmarked(bookmarkResponse.data);
      } catch (error) {
        toast({
          title: t("common.error"),
          description: t("document.detail.fetchError"),
          variant: "destructive"
        });
      } finally {
        setLoading(false);
      }
    };

    fetchDocument();
  }, [documentId, navigate, t, toast]);

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  const handleBookmark = async () => {
    if (!documentId) return;

    try {
      if (isBookmarked) {
        await documentService.unbookmarkDocument(documentId);
        setIsBookmarked(false);
        toast({
          title: t("common.success"),
          description: t("document.bookmark.removeSuccess"),
          variant: "success"
        });
      } else {
        await documentService.bookmarkDocument(documentId);
        setIsBookmarked(true);
        toast({
          title: t("common.success"),
          description: t("document.bookmark.addSuccess"),
          variant: "success"
        });
      }
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.bookmark.error"),
        variant: "destructive"
      });
    }
  };

  const handleVersionView = async (versionNumber: number) => {
    try {
      const response = await documentService.getDocumentVersion(documentId, versionNumber);
      setDocumentData(response.data);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.versions.error.load"),
        variant: "destructive"
      });
    }
  };

  const handleVersionDownload = async (versionNumber: number) => {
    try {
      const response = await documentService.downloadDocumentVersion(
        documentId,
        versionNumber
      );
      const url = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", documentData.filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.versions.error.download"),
        variant: "destructive"
      });
    }
  };

  if (loading || masterDataLoading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (!documentData) return null;

  return (
    <div className="space-y-6">
      <Button variant="ghost" onClick={() => navigate("/home")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        {t("document.detail.backToList")}
      </Button>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        {/* Document Properties */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FileBox className="h-5 w-5" />
              {documentData.originalFilename}
            </CardTitle>
            <CardDescription>
              {documentData.documentType} - {(documentData.fileSize / 1024).toFixed(2)} KB
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Document Metadata */}
            <div className="space-y-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <User className="h-4 w-4" />
                {t("document.detail.fields.uploadedBy")}: {documentData.createdBy}
              </div>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Calendar className="h-4 w-4" />
                {t("document.detail.fields.uploadDate")}: {formatDate(documentData.createdAt.toString())}
              </div>

              {/* Document Actions */}
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-2"
                  onClick={() => handleBookmark()}
                >
                  {isBookmarked ? (
                    <>
                      <Bookmark className="h-4 w-4 fill-current" />
                      {t("document.actions.bookmarked")}
                    </>
                  ) : (
                    <>
                      <BookmarkPlus className="h-4 w-4" />
                      {t("document.actions.bookmark")}
                    </>
                  )}
                </Button>

                {currentUser?.username === documentData.createdBy && (
                  <ShareDocumentDialog
                    documentId={documentData.id}
                    documentName={documentData.originalFilename}
                    isShared={true}
                    onShareToggle={() => {
                    }}
                  />
                )}
              </div>
            </div>

            <Separator />

            {/* Document Information */}
            <div className="grid gap-4">
              <div className="space-y-2">
                <Label>{t("document.detail.fields.summary")}</Label>
                <p className="text-sm text-muted-foreground">{documentData.summary}</p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.courseCode")}</Label>
                <p className="text-sm text-muted-foreground">{documentData.courseCode}</p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.major")}</Label>
                <p className="text-sm text-muted-foreground">
                  {getMasterDataTranslation(documentData.major, "major", { majors })}
                </p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.level")}</Label>
                <p className="text-sm text-muted-foreground">
                  {getMasterDataTranslation(documentData.courseLevel, "level", { levels })}
                </p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.category")}</Label>
                <p className="text-sm text-muted-foreground">
                  {getMasterDataTranslation(documentData.category, "category", { categories })}
                </p>
              </div>

              {documentData.tags && documentData.tags.length > 0 && (
                <div className="space-y-2">
                  <Label>{t("document.detail.fields.tags")}</Label>
                  <div className="flex flex-wrap gap-2">
                    {documentData.tags.map((tag, index) => (
                      <span
                        key={index}
                        className="inline-flex items-center rounded-md bg-primary/10 px-2 py-1 text-xs font-medium text-primary"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </CardContent>

          {documentData && (
            <DocumentVersionHistory
              versions={documentData.versions}
              currentVersion={documentData.currentVersion}
              onViewVersion={handleVersionView}
              onDownloadVersion={handleVersionDownload}
            />
          )}
        </Card>

        {/* Document Preview */}
        <Card className="xl:h-[800px]">
          <CardHeader>
            <CardTitle>{documentData.originalFilename}</CardTitle>
            <CardDescription>
              {documentData.documentType} - {(documentData.fileSize / 1024).toFixed(2)} KB
            </CardDescription>
          </CardHeader>
          <CardContent className="h-full max-h-[700px]">
            <DocumentViewer
              documentId={documentData.id}
              documentType={documentData.documentType}
              mimeType={documentData.mimeType}
              fileName={documentData.filename}
            />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}