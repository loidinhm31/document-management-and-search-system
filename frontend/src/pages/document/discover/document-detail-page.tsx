import { ArrowLeft, Bookmark, BookmarkPlus, Calendar, FileBox, Loader2, User } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

import DocumentViewer from "@/components/document/viewers/document-viewer";
import ShareDocumentDialog from "@/components/document/my-document/share-document-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { masterDataService, MasterDataType } from "@/services/master-data.service";
import { DocumentInformation, MasterData } from "@/types/document";
import { useAuth } from "@/context/auth-context";
import i18n from "i18next";

export default function DocumentDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { documentId } = useParams<{ documentId: string }>();
  const { currentUser } = useAuth();
  const { toast } = useToast();

  const [loading, setLoading] = useState(true);
  const [document, setDocument] = useState<DocumentInformation | null>(null);
  const [isBookmarked, setIsBookmarked] = useState(false);
  const [masterData, setMasterData] = useState<{
    majors: MasterData[];
    levels: MasterData[];
    categories: MasterData[];
  }>({
    majors: [],
    levels: [],
    categories: []
  });

  useEffect(() => {
    const fetchMasterData = async () => {
      try {
        const [majorsResponse, levelsResponse, categoriesResponse] = await Promise.all([
          masterDataService.getByType(MasterDataType.MAJOR),
          masterDataService.getByType(MasterDataType.COURSE_LEVEL),
          masterDataService.getByType(MasterDataType.DOCUMENT_CATEGORY)
        ]);

        setMasterData({
          majors: majorsResponse.data,
          levels: levelsResponse.data,
          categories: categoriesResponse.data
        });
      } catch (error) {
        console.error('Error fetching master data:', error);
      }
    };

    fetchMasterData();
  }, []);

  useEffect(() => {
    const fetchDocument = async () => {
      if (!documentId) return;

      try {
        const [docResponse, bookmarkResponse] = await Promise.all([
          documentService.getDocumentDetails(documentId),
          documentService.isDocumentBookmarked(documentId)
        ]);

        setDocument(docResponse.data);
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

  const getMasterDataTranslation = (code: string, type: 'major' | 'level' | 'category') => {
    let data: MasterData[] = [];
    switch (type) {
      case 'major':
        data = masterData.majors;
        break;
      case 'level':
        data = masterData.levels;
        break;
      case 'category':
        data = masterData.categories;
        break;
    }
    const item = data.find(item => item.code === code);
    return item ? (item.translations[i18n.language] || item.translations.en) : code;
  };

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (!document) return null;

  const isDocumentCreator = currentUser?.username === document.createdBy;

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
              {document.originalFilename}
            </CardTitle>
            <CardDescription>
              {document.documentType} - {(document.fileSize / 1024).toFixed(2)} KB
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Document Metadata */}
            <div className="space-y-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <User className="h-4 w-4" />
                {t("document.detail.fields.uploadedBy")}: {document.createdBy}
              </div>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Calendar className="h-4 w-4" />
                {t("document.detail.fields.uploadDate")}: {formatDate(document.createdAt.toString())}
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

                {currentUser?.username === document.createdBy && (
                  <ShareDocumentDialog
                    documentId={document.id}
                    documentName={document.originalFilename}
                    isShared={true}
                    onShareToggle={() => {}}
                  />
                )}
              </div>
            </div>

            <Separator />

            {/* Document Information */}
            <div className="grid gap-4">
              <div className="space-y-2">
                <Label>{t("document.detail.fields.summary")}</Label>
                <p className="text-sm text-muted-foreground">{document.summary}</p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.courseCode")}</Label>
                <p className="text-sm text-muted-foreground">{document.courseCode}</p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.major")}</Label>
                <p className="text-sm text-muted-foreground">
                  {getMasterDataTranslation(document.major, 'major')}
                </p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.level")}</Label>
                <p className="text-sm text-muted-foreground">
                  {getMasterDataTranslation(document.courseLevel, 'level')}
                </p>
              </div>

              <div className="space-y-2">
                <Label>{t("document.detail.fields.category")}</Label>
                <p className="text-sm text-muted-foreground">
                  {getMasterDataTranslation(document.category, 'category')}
                </p>
              </div>

              {document.tags && document.tags.length > 0 && (
                <div className="space-y-2">
                  <Label>{t("document.detail.fields.tags")}</Label>
                  <div className="flex flex-wrap gap-2">
                    {document.tags.map((tag, index) => (
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
        </Card>

        {/* Document Preview */}
        <Card className="xl:h-[800px]">
          <CardHeader>
            <CardTitle>{document.originalFilename}</CardTitle>
            <CardDescription>
              {document.documentType} - {(document.fileSize / 1024).toFixed(2)} KB
            </CardDescription>
          </CardHeader>
          <CardContent className="h-full max-h-[700px]">
            <DocumentViewer
              documentId={document.id}
              documentType={document.documentType}
              mimeType={document.mimeType}
              fileName={document.filename}
            />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}