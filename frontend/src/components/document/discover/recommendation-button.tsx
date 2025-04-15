import { StarIcon } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { documentRecommendationService } from "@/services/document-recommendation.service";

interface RecommendationButtonProps {
  documentId: string;
}

export function RecommendationButton({ documentId }: RecommendationButtonProps) {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [isRecommended, setIsRecommended] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // Check if the document is already recommended
    const checkRecommendationStatus = async () => {
      try {
        const response = await documentRecommendationService.isDocumentRecommended(documentId);
        setIsRecommended(response.data);
      } catch (error) {
        console.error("Error checking recommendation status:", error);
      }
    };

    checkRecommendationStatus();
  }, [documentId]);

  const handleToggleRecommendation = async () => {
    setLoading(true);
    try {
      if (isRecommended) {
        await documentRecommendationService.unrecommendDocument(documentId);
        toast({
          title: t("common.success"),
          description: t("document.recommendation.removeSuccess"),
          variant: "success",
        });
      } else {
        await documentRecommendationService.recommendDocument(documentId);
        toast({
          title: t("common.success"),
          description: t("document.recommendation.addSuccess"),
          variant: "success",
        });
      }
      setIsRecommended(!isRecommended);
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.recommendation.error"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Button
      variant="outline"
      size="sm"
      className="flex items-center gap-2"
      onClick={handleToggleRecommendation}
      disabled={loading}
    >
      <StarIcon className={isRecommended ? "h-4 w-4 fill-amber-500 text-amber-500" : "h-4 w-4"} />
      {isRecommended ? t("document.actions.recommended") : t("document.actions.recommend")}
    </Button>
  );
}
