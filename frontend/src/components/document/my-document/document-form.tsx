import { zodResolver } from "@hookform/resolvers/zod";
import i18n from "i18next";
import { Loader2, Upload } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useDropzone } from "react-dropzone";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import * as z from "zod";

import { CategoryPredictions } from "@/components/document/my-document/confidence-to-color";
import TagInput from "@/components/tag-input";
import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useLanguageDetection } from "@/hooks/use-language-detection";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/masterDataSlice";


const documentSchema = z.object({
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

export type DocumentFormValues = z.infer<typeof documentSchema>;

interface DocumentFormProps {
  initialValues?: DocumentFormValues;
  onSubmit: (data: DocumentFormValues, file?: File) => Promise<void>;
  loading?: boolean;
  submitLabel?: string;
}

export function DocumentForm({ initialValues, onSubmit, submitLabel = "Upload" }: DocumentFormProps) {
  const { t } = useTranslation();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const { detectLanguage, detectedLanguage, detectingLanguage } = useLanguageDetection();

  const [shouldFetchPredictions, setShouldFetchPredictions] = useState(false);

  const dispatch = useAppDispatch();
  const { majors, levels, categories, loading: masterDataLoading } = useAppSelector(selectMasterData);


  const form = useForm<DocumentFormValues>({
    resolver: zodResolver(documentSchema),
    defaultValues: initialValues || {
      summary: "",
      courseCode: "",
      major: "",
      level: "",
      category: "",
      tags: []
    }
  });

  useEffect(() => {
    if (majors.length === 0 || levels.length === 0 || categories.length === 0) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, majors.length, levels.length, categories.length]);

  // Watch summary field changes for language detection
  useEffect(() => {
    const subscription = form.watch((value, { name }) => {
      if (name === "summary" && value.summary) {
        detectLanguage(value.summary);
      }
    });
    return () => subscription.unsubscribe();
  }, [form.watch, detectLanguage]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: acceptedFiles => {
      if (acceptedFiles?.length > 0) {
        setSelectedFile(acceptedFiles[0]);
      }
    },
    maxFiles: 1,
    accept: {
      "application/pdf": [".pdf"],
      "application/msword": [".doc"],
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document": [".docx"],
      "application/vnd.ms-excel": [".xls"],
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": [".xlsx"],
      "text/plain": [".txt"],
      "text/csv": [".csv"],
      "application/json": [".json"],
      "application/xml": [".xml"],
      "application/vnd.ms-powerpoint": [".pptx"]
    }
  });

  const handleSubmit = async (data: DocumentFormValues) => {
    await onSubmit(data, selectedFile || undefined);
    if (!initialValues) {
      form.reset();
      setSelectedFile(null);
    }
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-6">
        {/* File Upload Section */}
        <div
          {...getRootProps()}
          className="border-2 border-dashed rounded-lg p-6 text-center cursor-pointer hover:border-primary transition-colors"
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
                XML
              </p>
            </div>
          )}
          {selectedFile && (
            <p className="mt-2 text-sm text-muted-foreground">
              Selected: {selectedFile.name}
            </p>
          )}
        </div>

        <div className="space-y-4">
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
                        field.onBlur(); // Call the original onBlur
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
                    {levels.map((level) => (
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
                  disabled={masterDataLoading}
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
                <FormLabel>{t("document.detail.form.tags.label")}</FormLabel>
                <FormControl>
                  <TagInput
                    value={field.value || []}
                    onChange={field.onChange}
                    placeholder={t("document.detail.form.tags.placeholder")}
                    disabled={masterDataLoading}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <Button type="submit" disabled={masterDataLoading || (!initialValues && !selectedFile)} className="w-full">
            {masterDataLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {submitLabel}
          </Button>
        </div>
      </form>
    </Form>
  );
}