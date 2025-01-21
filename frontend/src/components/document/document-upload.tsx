import { useTranslation } from "react-i18next";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";

import { DocumentForm, DocumentFormValues } from "./document-form";

export function DocumentUpload() {
  const { t } = useTranslation();
  const { toast } = useToast();

  const handleSubmit = async (data: DocumentFormValues, file?: File) => {
    if (!file) {
      toast({
        title: t("common.error"),
        description: t("document.upload.messages.fileRequired"),
        variant: "destructive"
      });
      return;
    }

    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("courseCode", data.courseCode);
      formData.append("major", data.major);
      formData.append("level", data.level);
      formData.append("category", data.category);

      if (data.tags && data.tags.length > 0) {
        const cleanedTags = data.tags
          .map(tag => tag.trim())
          .filter(Boolean);

        formData.append("tags", cleanedTags.join(","));
      }

      await documentService.uploadDocument(formData);

      toast({
        title: t("common.success"),
        description: t("document.upload.messages.success"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.upload.messages.error"),
        variant: "destructive"
      });
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("document.upload.title")}</CardTitle>
        <CardDescription>{t("document.upload.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <DocumentForm
          onSubmit={handleSubmit}
          submitLabel={t("document.upload.buttons.upload")}
        />
      </CardContent>
    </Card>
  );
}

export default DocumentUpload;