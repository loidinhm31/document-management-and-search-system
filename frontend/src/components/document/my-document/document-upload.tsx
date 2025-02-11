import { zodResolver } from "@hookform/resolvers/zod";
import i18n from "i18next";
import { Loader2, Upload } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useDropzone } from "react-dropzone";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import * as z from "zod";

import TagInputDebounce from "@/components/common/tag-input-debounce";
import { CategoryPredictions } from "@/components/document/my-document/confidence-to-color";
import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useProcessing } from "@/context/processing-provider";
import { useLanguageDetection } from "@/hooks/use-language-detection";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { masterDataService } from "@/services/master-data.service";
import { ACCEPT_TYPE_MAP } from "@/types/document";
import { MasterData, MasterDataType } from "@/types/master-data";

const formSchema = z.object({
  summary: z.string()
    .optional()
    .refine(
      (val) => !val || (val.length >= 50 && val.length <= 500),
      (val) => ({
        message: val && val.length < 50
          ? "Summary must be at least 50 characters"
          : "Summary must not exceed 500 characters"
      })
    ),
  courseCode: z.string().min(1, "Course code is required"),
  major: z.string().min(1, "Major is required"),
  level: z.string().min(1, "Course level is required"),
  category: z.string().min(1, "Document category is required"),
  tags: z.array(z.string()).optional()
});

interface DocumentUploadProps {
  onUploadSuccess?: () => void;
}

export const DocumentUpload: React.FC<DocumentUploadProps> = ({ onUploadSuccess }) => {
  const { t } = useTranslation();
  const { addProcessingItem } = useProcessing();

  const [uploading, setUploading] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const { toast } = useToast();

  const { detectLanguage, detectedLanguage, detectingLanguage } = useLanguageDetection();

  const [courseLevels, setCourseLevels] = useState<MasterData[]>([]);
  const [majors, setMajors] = useState<MasterData[]>([]);
  const [categories, setCategories] = useState<MasterData[]>([]);
  const [loading, setLoading] = useState(true);
  const [shouldFetchPredictions, setShouldFetchPredictions] = useState(false);

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      summary: "",
      courseCode: "",
      major: "",
      level: "",
      category: "",
      tags: []
    }
  });

  const onDrop = React.useCallback((acceptedFiles: File[]) => {
    if (acceptedFiles?.length > 0) {
      setSelectedFile(acceptedFiles[0]);
    }
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    maxFiles: 1,
    accept: ACCEPT_TYPE_MAP
  });

  const onSubmit = async (data: z.infer<typeof formSchema>) => {
    if (!selectedFile) {
      toast({
        title: "Error",
        description: t("document.upload.messages.fileRequired"),
        variant: "destructive"
      });
      return;
    }

    setUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", selectedFile);
      if (data.summary) {
        formData.append("summary", data.summary);
      }
      formData.append("courseCode", data.courseCode);
      formData.append("major", data.major);
      formData.append("level", data.level);
      formData.append("category", data.category);

      const cleanedTags = (data.tags || [])
        .map(tag => tag.trim())
        .filter(Boolean);

      if (cleanedTags.length > 0) {
        formData.append("tags", cleanedTags.join(","));
      }

      handleUpload(formData);

      toast({
        title: t("common.success"),
        description: t("document.upload.messages.success"),
        variant: "success"
      });

      // Reset form and state
      form.reset({
        summary: "",
        courseCode: "",
        major: "",
        level: "",
        category: "",
        tags: []
      });
      setSelectedFile(null);

      // Call the success callback if provided
      onUploadSuccess?.();

    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.upload.messages.error"),
        variant: "destructive"
      });
    } finally {
      setUploading(false);
    }
  };

  const handleUpload = async (formData: FormData) => {
    try {
      const response = await documentService.uploadDocument(formData);
      const document = response.data;

      // Add to processing queue
      addProcessingItem(document.id, document.filename);

      // Close dialog or continue with your existing flow
      onUploadSuccess?.();

    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.upload.messages.error"),
        variant: "destructive"
      });
    }
  };

  useEffect(() => {
    const fetchMasterData = async () => {
      try {
        const [levelsResponse, majorsResponse, categoriesResponse] = await Promise.all([
          masterDataService.getAllActiveByType(MasterDataType.COURSE_LEVEL),
          masterDataService.getAllActiveByType(MasterDataType.MAJOR),
          masterDataService.getAllActiveByType(MasterDataType.DOCUMENT_CATEGORY)
        ]);

        setCourseLevels(levelsResponse.data);
        setMajors(majorsResponse.data);
        setCategories(categoriesResponse.data);
      } catch (error) {
        console.error("Error fetching master data:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchMasterData();
  }, []);

  // Watch summary field changes for language detection
  useEffect(() => {
    const subscription = form.watch((value, { name }) => {
      if (name === "summary" && value.summary) {
        detectLanguage(value.summary);
      }
    });
    return () => subscription.unsubscribe();
  }, [form.watch, detectLanguage]);

  return (
    <div className="space-y-6">
      <div
        {...getRootProps()}
        className="border-2 border-dashed rounded-lg p-8 text-center cursor-pointer hover:border-primary transition-colors"
      >
        <input {...getInputProps()} />
        <Upload className="w-12 h-12 mx-auto mb-4 text-muted-foreground" />
        {isDragActive ? (
          <p>{t("document.upload.dropzone.prompt")}</p>
        ) : (
          <div className="space-y-2">
            <p>{t("document.upload.dropzone.info")}</p>
            <p className="text-sm text-muted-foreground">
              {t("document.upload.dropzone.supportedFormats")} PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, CSV, TXT, JSON,
              XML, MARKDOWN
            </p>
          </div>
        )}
        {selectedFile && (
          <p className="mt-2 text-sm text-muted-foreground">
            Selected: {selectedFile.name}
          </p>
        )}
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="summary"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Summary (50-500 characters)</FormLabel>
                <div className="space-y-2">
                  <FormControl>
                    <Textarea
                      {...field}
                      placeholder="Enter document summary..."
                      className="min-h-[150px]"
                      onBlur={() => {
                        field.onBlur();
                        setShouldFetchPredictions(true);
                      }}
                    />
                  </FormControl>
                  <div className="flex justify-between text-sm text-muted-foreground">
                    <span>{field.value?.length || 0}/500 characters</span>
                    {detectingLanguage ? (
                      <span>Detecting language...</span>
                    ) : detectedLanguage && (
                      <span>Detected language: {detectedLanguage}</span>
                    )}
                  </div>
                  <FormMessage />
                </div>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="courseCode"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("document.detail.form.courseCode.label")}</FormLabel>
                <FormControl>
                  <Input placeholder={t("document.detail.form.courseCode.placeholder")} {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="major"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("document.upload.form.major.label")}</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder={t("document.upload.form.major.placeholder")} />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {majors.map((major) => (
                      <SelectItem key={major.code} value={major.code}>
                        {major.translations[i18n.language] || major.translations.en}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="level"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("document.upload.form.level.label")}</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder={t("document.upload.form.level.placeholder")} />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {courseLevels.map((level) => (
                      <SelectItem key={level.code} value={level.code}>
                        {level.translations[i18n.language] || level.translations.en}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="category"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("document.upload.form.category.label")}</FormLabel>
                <CategoryPredictions
                  text={form.watch("summary")}
                  filename={selectedFile?.name || ""}
                  language={detectedLanguage || "en"}
                  value={field.value}
                  categories={categories}
                  onValueChange={field.onChange}
                  shouldFetchPredictions={shouldFetchPredictions}
                  setShouldFetchPredictions={setShouldFetchPredictions}
                  disabled={loading}
                />
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="tags"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("document.upload.form.tags.label")}</FormLabel>
                <FormControl>
                  <TagInputDebounce
                    value={field.value || []}
                    onChange={field.onChange}
                    placeholder={t("document.upload.form.tags.placeholder")}
                    disabled={uploading}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <Button type="submit" disabled={uploading || !selectedFile} className="w-full">
            {uploading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("document.upload.buttons.upload")}
          </Button>
        </form>
      </Form>
    </div>
  );
};

export default DocumentUpload;