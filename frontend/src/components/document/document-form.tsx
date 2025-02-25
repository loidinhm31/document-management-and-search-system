import { zodResolver } from "@hookform/resolvers/zod";
import i18n from "i18next";
import { Loader2, Upload } from "lucide-react";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useDropzone } from "react-dropzone";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import * as z from "zod";

import TagInputDebounce from "@/components/common/tag-input-debounce";
import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/master-data-slice";
import { ACCEPT_TYPE_MAP, MAX_FILE_SIZE } from "@/types/document";

const documentSchema = z.object({
  summary: z
    .string()
    .optional()
    .refine(
      (val) => !val || (val.length >= 50 && val.length <= 500),
      (val) => ({
        message:
          val && val.length < 50 ? "Summary must be at least 50 characters" : "Summary must not exceed 500 characters",
      }),
    ),
  courseCode: z.string().optional(),
  major: z.string().min(1, "Major is required"),
  level: z.string().min(1, "Course level is required"),
  category: z.string().min(1, "Document category is required"),
  tags: z.array(z.string()).optional(),
});

export type DocumentFormValues = z.infer<typeof documentSchema>;

interface DocumentFormProps {
  initialValues?: DocumentFormValues;
  onSubmit: (data: DocumentFormValues, file?: File) => Promise<void>;
  loading?: boolean;
  submitLabel?: string;
  disabled?: boolean;
}

export function DocumentForm({ initialValues, onSubmit, submitLabel, loading, disabled }: DocumentFormProps) {
  const { t } = useTranslation();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [sizeError, setSizeError] = useState<string | null>(null);

  const dispatch = useAppDispatch();
  const { majors, courseCodes, levels, categories, loading: masterDataLoading } = useAppSelector(selectMasterData);

  const form = useForm<DocumentFormValues>({
    resolver: zodResolver(documentSchema),
    defaultValues: initialValues || {
      summary: "",
      courseCode: "",
      major: "",
      level: "",
      category: "",
      tags: [],
    },
    mode: "onBlur",
  });

  const selectedMajor = form.watch("major");

  const filteredCourseCodes = useMemo(() => {
    if (!selectedMajor) {
      return [];
    }
    const majorObj = majors.find((m) => m.code === selectedMajor);
    return courseCodes.filter((course) => course.parentId === majorObj?.id);
  }, [selectedMajor, courseCodes, majors]);

  useEffect(() => {
    if (selectedMajor && form.getValues("courseCode")) {
      if (filteredCourseCodes?.length === 0) {
        form.setValue("courseCode", "");
        return;
      }

      // Check if the current course code belongs to the selected major
      const currentCourseCode = form.getValues("courseCode");
      const isValidCourseCode = filteredCourseCodes.some((course) => course.code === currentCourseCode);

      if (!isValidCourseCode) {
        form.setValue("courseCode", "");
      }
    }
  }, [selectedMajor, filteredCourseCodes, form]);

  useEffect(() => {
    if (majors?.length === 0 || courseCodes?.length === 0 || levels?.length === 0 || categories?.length === 0) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, majors?.length, courseCodes?.length, levels?.length, categories?.length]);

  const onDrop = useCallback((acceptedFiles: File[]) => {
    // Clear previous errors
    setSizeError(null);

    if (acceptedFiles?.length > 0) {
      setSelectedFile(acceptedFiles[0]);
    }
  }, []);

  const onDropRejected = useCallback(
    (fileRejections) => {
      const fileSizeError = fileRejections.find((rejection) =>
        rejection.errors.some((error) => error.code === "file-too-large"),
      );

      if (fileSizeError) {
        setSizeError(
          t("document.upload.dropzone.sizeLimitError", {
            maxFileSize: (MAX_FILE_SIZE / (1024 * 1024)).toFixed(3),
            fileSizeError: (fileSizeError.file.size / (1024 * 1024)).toFixed(3),
          }),
        );
        setSelectedFile(null);
      }
    },
    [t],
  );

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    disabled: disabled,
    onDropRejected,
    maxFiles: 1,
    maxSize: MAX_FILE_SIZE,
    accept: ACCEPT_TYPE_MAP,
  });

  const handleSubmit = async (data: DocumentFormValues) => {
    await onSubmit(data, selectedFile || undefined);
    form.reset();
    setSelectedFile(null);
  };

  return (
    <div className="space-y-6">
      {/* File Upload Section */}
      <div
        {...getRootProps()}
        className={cn(
          "border-2 border-dashed rounded-lg p-8 text-center hover:border-primary transition-colors",
          disabled ? "opacity-50 cursor-not-allowed" : "cursor-pointer",
        )}
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
            <p className="text-sm text-muted-foreground">
              {t("document.upload.dropzone.maxFileSize", { fileSize: (MAX_FILE_SIZE / (1024 * 1024)).toFixed(3) })}
            </p>
          </div>
        )}
        {selectedFile && (
          <p className="mt-2 font-bold text-primary bg-primary/10 px-3 py-1 rounded-md inline-block">
            Selected: {selectedFile.name}
          </p>
        )}
      </div>

      {sizeError && (
        <div className="mt-2 text-sm font-medium text-red-600 bg-red-50 px-3 py-2 rounded-md">{sizeError}</div>
      )}

      <div className="space-y-4">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-6">
            <FormField
              control={form.control}
              name="summary"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("document.upload.form.summary.label")}</FormLabel>
                  <div className="space-y-2">
                    <FormControl>
                      <Textarea
                        {...field}
                        placeholder={t("document.upload.form.summary.placeholder")}
                        className="min-h-[150px]"
                        disabled={disabled}
                      />
                    </FormControl>
                    <div className="flex justify-between text-sm text-muted-foreground">
                      <span>
                        {field.value?.length || 0}/500 {t("document.upload.form.summary.count")}
                      </span>
                    </div>
                    <FormMessage />
                  </div>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="major"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("document.upload.form.major.label")}</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    value={field.value}
                    disabled={disabled || majors?.length === 0}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("document.upload.form.major.placeholder")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {majors?.map((major) => (
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
              name="courseCode"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("document.upload.form.courseCode.label")}</FormLabel>
                  <Select
                    onValueChange={field.onChange}
                    value={field.value}
                    disabled={disabled || !selectedMajor || filteredCourseCodes.length === 0}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("document.upload.form.courseCode.placeholder")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {filteredCourseCodes.map((course) => (
                        <SelectItem key={course.code} value={course.code}>
                          {course.translations[i18n.language] || course.translations.en}
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
                  <Select
                    onValueChange={field.onChange}
                    value={field.value}
                    disabled={disabled || levels?.length === 0}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("document.upload.form.level.placeholder")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {levels?.map((level) => (
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
                  <Select
                    onValueChange={field.onChange}
                    value={field.value}
                    disabled={disabled || categories?.length === 0}
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder={t("document.detail.form.category.placeholder")} />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {categories?.map((category) => (
                        <SelectItem key={category.code} value={category.code}>
                          {category.translations[i18n.language] || category.translations.en}
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
              name="tags"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("document.detail.form.tags.label")}</FormLabel>
                  <FormControl>
                    <TagInputDebounce
                      value={field.value || []}
                      onChange={field.onChange}
                      placeholder={t("document.detail.form.tags.placeholder")}
                      disabled={disabled || masterDataLoading}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <Button
              type="submit"
              disabled={masterDataLoading || loading || (!initialValues && !selectedFile)}
              className="w-full"
            >
              {(masterDataLoading || loading) && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {submitLabel}
            </Button>
          </form>
        </Form>
      </div>
    </div>
  );
}
