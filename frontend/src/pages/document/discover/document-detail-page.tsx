import { ArrowLeft, Calendar, Loader2, User } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { MdOutlineFavorite, MdOutlineFavoriteBorder } from "react-icons/md";
import { useNavigate, useParams } from "react-router-dom";

import { CommentSection } from "@/components/document/discover/comment-section";
import { RelatedDocuments } from "@/components/document/discover/related-document";
import DocumentVersionHistory from "@/components/document/document-versions-history";
import ShareDocumentDialog from "@/components/document/share-document-dialog";
import DocumentViewer from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { getMasterDataTranslation } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { setCurrentDocument } from "@/store/slices/document-slice";
import { fetchMasterData, selectMasterData } from "@/store/slices/master-data-slice";
import { DocumentInformation } from "@/types/document";
import { MasterDataType } from "@/types/master-data";

export default function DocumentDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { documentId } = useParams<{ documentId: string }>();
  const { currentUser } = useAuth();
  const { toast } = useToast();
  const dispatch = useAppDispatch();

  const [loading, setLoading] = useState(true);
  const [documentData, setDocumentData] = useState<DocumentInformation | null>(null);
  const [isFavorited, setIsFavorited] = useState(false);

  const { majors, courseCodes, levels, categories, loading: masterDataLoading } = useAppSelector(selectMasterData);

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  const handleFavorite = async () => {
    if (!documentId) return;

    try {
      if (isFavorited) {
        await documentService.unfavoriteDocument(documentId);
        setIsFavorited(false);
        toast({
          title: t("common.success"),
          description: t("document.favorite.removeSuccess"),
          variant: "success"
        });
      } else {
        await documentService.favoriteDocument(documentId);
        setIsFavorited(true);
        toast({
          title: t("common.success"),
          description: t("document.favorite.addSuccess"),
          variant: "success"
        });
      }
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.favorite.error"),
        variant: "destructive"
      });
    }
  };

  const handleVersionUpdate = (updatedDocument: DocumentInformation) => {
    setDocumentData(updatedDocument);
  };

  useEffect(() => {
    if (majors.length === 0 || levels.length === 0 || categories.length === 0) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, majors.length, levels.length, categories.length]);

  useEffect(() => {
    const fetchDocument = async () => {
      if (!documentId) return;

      try {
        const [docResponse, favoriteResponse] = await Promise.all([
          documentService.getDocumentDetails(documentId),
          documentService.isDocumentFavorited(documentId)
        ]);

        setDocumentData(docResponse.data);
        setIsFavorited(favoriteResponse.data);
        dispatch(setCurrentDocument(docResponse.data));
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

    return () => {
      dispatch(setCurrentDocument(null));
    };
  }, [documentId]);

  if (loading || masterDataLoading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (!documentData) return null;

  return (
    <div className="container mx-auto py-6 space-y-6">
      <Button variant="ghost" onClick={() => navigate(-1)} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        {t("document.detail.backToList")}
      </Button>

      <div className="grid grid-cols-1 gap-6">
        {/* Main Content Grid */}
        <div className="grid grid-cols-1 gap-6 xl:grid-cols-12">
          {/* Preview Section */}
          <Card className="xl:h-[800px] xl:col-span-8">
            <CardHeader>
              <CardTitle>{documentData?.filename}</CardTitle>
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

          {/* Document Info Section */}
          <Card className="xl:col-span-4">
            <CardContent className="p-4 lg:p-6 space-y-6">
              {/* Document Actions */}
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="gap-2"
                  onClick={handleFavorite}
                >
                  {isFavorited ? (
                    <>
                      <MdOutlineFavorite className="h-4 w-4 fill-current" />
                      {t("document.actions.favorited")}
                    </>
                  ) : (
                    <>
                      <MdOutlineFavoriteBorder className="h-4 w-4" />
                      {t("document.actions.favorite")}
                    </>
                  )}
                </Button>

                {currentUser?.username === documentData.createdBy && (
                  <ShareDocumentDialog
                    documentId={documentData.id}
                    documentName={documentData.filename}
                    isShared={documentData.sharingType === "PUBLIC"}
                  />
                )}
              </div>

              {/* Document Metadata */}
              <div className="space-y-6">
                <div className="space-y-1">
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <User className="h-4 w-4" />
                    {documentData.createdBy}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Calendar className="h-4 w-4" />
                    {formatDate(documentData.createdAt.toString())}
                  </div>
                </div>

                {/* Document Details */}
                <div className="space-y-4">
                  {documentData.summary && (
                    <div className="space-y-2">
                      <Label>{t("document.detail.fields.summary")}</Label>
                      <p className="text-sm text-muted-foreground">{documentData.summary}</p>
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label>{t("document.detail.fields.major")}</Label>
                      <p className="text-sm text-muted-foreground">
                        {getMasterDataTranslation(documentData.major, MasterDataType.MAJOR, { majors })}
                      </p>
                    </div>

                    <div className="space-y-2">
                      <Label>{t("document.detail.fields.courseCode")}</Label>
                      <p className="text-sm text-muted-foreground">
                        {getMasterDataTranslation(documentData.courseCode, MasterDataType.COURSE_CODE, { courseCodes })}
                      </p>
                    </div>

                    <div className="space-y-2">
                      <Label>{t("document.detail.fields.level")}</Label>
                      <p className="text-sm text-muted-foreground">
                        {getMasterDataTranslation(documentData.courseLevel, MasterDataType.COURSE_LEVEL, { levels })}
                      </p>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label>{t("document.detail.fields.category")}</Label>
                    <p className="text-sm text-muted-foreground">
                      {getMasterDataTranslation(documentData.category, MasterDataType.DOCUMENT_CATEGORY, { categories })}
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
              </div>

              {/* Document Versions */}
              {documentData && (
                <DocumentVersionHistory
                  versions={documentData.versions}
                  currentVersion={documentData.currentVersion}
                  documentCreatorId={documentData.userId}
                  documentId={documentData.id}
                  onVersionUpdate={handleVersionUpdate}
                />
              )}
            </CardContent>
          </Card>
        </div>

        {/* Related Documents Section */}
        <RelatedDocuments
          documentId={documentId}
          onDocumentClick={(doc) => navigate(`/discover/${doc.id}`)}
        />

        {/* Comments Section */}
        <Card>
          <CardHeader>
            <CardTitle>{t("document.comments.title")}</CardTitle>
            <CardDescription>{t("document.comments.description")}</CardDescription>
          </CardHeader>
          <CardContent>
            <CommentSection documentId={documentId} />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}