import { ArrowLeft, Calendar, Languages, Loader2, User } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { MdOutlineFavorite, MdOutlineFavoriteBorder } from "react-icons/md";
import { useNavigate, useParams } from "react-router-dom";

import MultiValueDisplay from "@/components/common/multi-value-display";
import { CommentSection } from "@/components/document/discover/comment/comment-section";
import DocumentStats from "@/components/document/discover/document-stats";
import { DocumentNoteList } from "@/components/document/discover/note/document-note-list";
import { RecommendationButton } from "@/components/document/discover/recommendation-button";
import { RelatedDocuments } from "@/components/document/discover/related-document";
import { ReportDocumentDialog } from "@/components/document/discover/report-document-dialog";
import DocumentVersionHistory from "@/components/document/document-versions-history";
import ShareDocumentDialog from "@/components/document/share-document-dialog";
import DocumentViewer from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { formatDate, getDescriptionType, getMasterDataTranslation } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { setCurrentDocument } from "@/store/slices/document-slice";
import { fetchMasterData, selectMasterData } from "@/store/slices/master-data-slice";
import { DocumentInformation, DocumentStatus, VIEWED_DOCUMENTS_KEY } from "@/types/document";
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

  const [statistics, setStatistics] = useState<{
    viewCount: number;
    downloadCount: number;
    totalInteractions: number;
  } | null>(null);

  const { majors, courseCodes, levels, categories, loading: masterDataLoading } = useAppSelector(selectMasterData);

  // Function to check if the document has been viewed in this session
  const checkIfDocumentViewed = (docId: string): boolean => {
    const viewedDocuments = JSON.parse(sessionStorage.getItem(VIEWED_DOCUMENTS_KEY) || "[]");
    return viewedDocuments.includes(docId);
  };

  // Function to mark document as viewed in this session
  const markDocumentAsViewed = (docId: string): void => {
    const viewedDocuments = JSON.parse(sessionStorage.getItem(VIEWED_DOCUMENTS_KEY) || "[]");
    if (!viewedDocuments.includes(docId)) {
      viewedDocuments.push(docId);
      sessionStorage.setItem(VIEWED_DOCUMENTS_KEY, JSON.stringify(viewedDocuments));
    }
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
          variant: "success",
        });
      } else {
        await documentService.favoriteDocument(documentId);
        setIsFavorited(true);
        toast({
          title: t("common.success"),
          description: t("document.favorite.addSuccess"),
          variant: "success",
        });
      }
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.favorite.error"),
        variant: "destructive",
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
        // Get document details (with a parameter to track view or not)
        const isFirstView = !checkIfDocumentViewed(documentId);
        const docResponse = await documentService.getDocumentDetails(documentId, isFirstView);
        setDocumentData(docResponse.data);
        dispatch(setCurrentDocument(docResponse.data));

        documentService.isDocumentFavorited(documentId).then((favoriteResponse) => {
          setIsFavorited(favoriteResponse.data);
        });

        documentService.getDocumentStatistics(documentId).then((statisticsResponse) => {
          setStatistics(statisticsResponse.data);
        });

        if (isFirstView) {
          // Mark this document as viewed in this session
          markDocumentAsViewed(documentId);
        }
      } catch (error: any) {
        toast({
          title: t("common.error"),
          description: t("document.detail.fetchError"),
          variant: "destructive",
        });

        if (error.response?.status === 400) {
          navigate("/");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchDocument();

    return () => {
      dispatch(setCurrentDocument(null));
    };
  }, [documentId]);

  const handleDownloadSuccess = async () => {
    const statisticsResponse = await documentService.getDocumentStatistics(documentId);
    setStatistics(statisticsResponse.data);
  };

  const isMentor = currentUser?.roles.includes("ROLE_MENTOR");

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
          <Card className="xl:h-[880px] xl:col-span-8">
            <CardHeader>
              <CardTitle>{documentData?.filename}</CardTitle>
              <CardDescription>
                {getDescriptionType(documentData?.documentType)} - {(documentData?.fileSize / 1024).toFixed(3)} KB
              </CardDescription>
            </CardHeader>
            <CardContent className="h-full max-h-[770px]">
              {documentData && (
                <DocumentViewer
                  documentId={documentData.id}
                  documentType={documentData.documentType}
                  mimeType={documentData.mimeType}
                  fileName={documentData.filename}
                  history={true}
                  onDownloadSuccess={handleDownloadSuccess}
                  documentStatus={documentData.status === DocumentStatus.PROCESSING ? documentData.status : null}
                />
              )}
            </CardContent>
          </Card>

          {/* Document Info Section */}
          <Card className="xl:col-span-4">
            <CardContent className="p-4 lg:p-6 space-y-6">
              {/* Document Actions */}
              <div className="flex flex-wrap gap-2">
                {!currentUser.roles.includes("ROLE_ADMIN") && (
                  <Button variant="outline" size="sm" className="gap-2" onClick={handleFavorite}>
                    {isFavorited ? (
                      <>
                        <MdOutlineFavorite className="h-4 w-4 fill-red-500" />
                        {t("document.actions.favorited")}
                      </>
                    ) : (
                      <>
                        <MdOutlineFavoriteBorder className="h-4 w-4" />
                        {t("document.actions.favorite")}
                      </>
                    )}
                  </Button>
                )}

                {/* Add Recommendation Button for Mentors */}
                {isMentor && documentId && <RecommendationButton documentId={documentId} />}

                {currentUser?.userId === documentData.userId && (
                  <ShareDocumentDialog
                    documentId={documentData.id}
                    documentName={documentData.filename}
                    isShared={documentData.sharingType === "PUBLIC"}
                  />
                )}

                {currentUser?.userId !== documentData.userId && !currentUser.roles.includes("ROLE_ADMIN") && (
                  <ReportDocumentDialog documentId={documentId} documentName={documentData.filename} />
                )}
              </div>

              {/* Document Metadata */}
              <div className="space-y-6">
                {/* Document Statistics */}
                {statistics && (
                  <DocumentStats viewCount={statistics.viewCount} downloadCount={statistics.downloadCount} />
                )}

                <div className="space-y-1">
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <User className="h-4 w-4" />
                    {documentData.createdBy}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Calendar className="h-4 w-4" />
                    {formatDate(documentData.createdAt.toString())}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Languages className="h-4 w-4" />
                    {documentData.language ? documentData.language : t("common.noLang")}
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
                      <Label>{t("document.detail.fields.majors")}</Label>
                      <MultiValueDisplay
                        value={documentData.majors}
                        type={MasterDataType.MAJOR}
                        masterData={{ majors }}
                      />
                    </div>

                    {documentData.courseCodes && (
                      <div className="space-y-2">
                        <Label>{t("document.detail.fields.courseCodes")}</Label>
                        <MultiValueDisplay
                          value={documentData.courseCodes}
                          type={MasterDataType.COURSE_CODE}
                          masterData={{ courseCodes }}
                        />
                      </div>
                    )}

                    <div className="space-y-2">
                      <Label>{t("document.detail.fields.level")}</Label>
                      <p className="text-sm text-muted-foreground">
                        {getMasterDataTranslation(documentData.courseLevel, MasterDataType.COURSE_LEVEL, { levels })}
                      </p>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label>{t("document.detail.fields.categories")}</Label>
                    <MultiValueDisplay
                      value={documentData.categories}
                      type={MasterDataType.DOCUMENT_CATEGORY}
                      masterData={{ categories }}
                    />
                  </div>

                  {documentData.tags && documentData.tags.length > 0 && (
                    <div className="space-y-2">
                      <Label>{t("document.detail.fields.tags")}</Label>
                      <MultiValueDisplay
                        value={documentData.tags}
                        pillClassName="inline-flex items-center rounded-md bg-primary/10 px-2 py-1 text-xs font-medium text-primary"
                      />
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
                  allowRevert={false}
                  onDownloadSuccess={handleDownloadSuccess}
                />
              )}
            </CardContent>
          </Card>
        </div>

        {/* Related Documents Section */}
        {!currentUser?.roles.includes("ROLE_ADMIN") && (
          <RelatedDocuments documentId={documentId} onDocumentClick={(doc) => navigate(`/discover/${doc.id}`)} />
        )}

        {/* Mentor Notes Section */}
        <DocumentNoteList documentId={documentId} />

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