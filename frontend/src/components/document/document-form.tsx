import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Upload } from "lucide-react";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useDropzone } from "react-dropzone";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

import TagInput from "@/components/common/tag-input";
import TagInputDebounce from "@/components/common/tag-input-debounce";
import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";
import { createDocumentSchema, DocumentFormValues } from "@/schemas/document-schema";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/master-data-slice";
import { ACCEPT_TYPE_MAP, MAX_FILE_SIZE } from "@/types/document";

interface DocumentFormProps {
  initialValues?: DocumentFormValues;
  onSubmit: (data: DocumentFormValues, file?: File) => Promise<void>;
  loading?: boolean;
  submitLabel?: string;
  disabled?: boolean;
  polling?: boolean;
}

export function DocumentForm({ initialValues, onSubmit, submitLabel, loading, disabled, polling }: DocumentFormProps) {
  const { t, i18n } = useTranslation();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [sizeError, setSizeError] = useState<string | null>(null);
  const [hasFormChanges, setHasFormChanges] = useState(false);

  const dispatch = useAppDispatch();
  const { majors, courseCodes, levels, categories, loading: masterDataLoading } = useAppSelector(selectMasterData);

  const form = useForm<DocumentFormValues>({
    resolver: zodResolver(createDocumentSchema(t)),
    defaultValues: initialValues || {
      summary: "",
      majors: [],
      courseCodes: [],
      level: "",
      categories: [],
      tags: [],
    },
    mode: "onBlur",
  });

  const formValues = form.watch();
  const selectedMajors = form.watch("majors");

  useEffect(() => {
    // Get fields that have been touched by the user
    const touchedFields = Object.keys(form.formState.touchedFields);

    // Only trigger validation for fields the user has interacted with
    if (touchedFields.length > 0) {
      form.trigger(touchedFields as any);
    }
  }, [i18n.language]);

  // Check if form values are different from initial values
  useEffect(() => {
    if (!initialValues) {
      // If no initial values, then any change makes the form valid for submission
      setHasFormChanges(true);
      return;
    }

    // Compare current form values with initial values
    const hasChanges =
      formValues.summary !== initialValues.summary ||
      !areArraysEqual(formValues.majors, initialValues.majors) ||
      !areArraysEqual(formValues.courseCodes || [], initialValues.courseCodes || []) ||
      formValues.level !== initialValues.level ||
      !areArraysEqual(formValues.categories, initialValues.categories) ||
      !areArraysEqual(formValues.tags || [], initialValues.tags || []);

    setHasFormChanges(hasChanges || !!selectedFile);
  }, [formValues, initialValues, selectedFile]);

  // Helper function to compare arrays
  const areArraysEqual = (arr1: string[], arr2: string[]): boolean => {
    if (arr1.length !== arr2.length) return false;
    const sortedArr1 = [...arr1].sort();
    const sortedArr2 = [...arr2].sort();
    return sortedArr1.every((value, index) => value === sortedArr2[index]);
  };

  // Get all available course codes for the selected majors
  const filteredCourseCodes = useMemo(() => {
    if (!selectedMajors || selectedMajors.length === 0) {
      return [];
    }

    // Find all parent IDs for the selected major codes
    const majorParentIds = majors.filter((major) => selectedMajors.includes(major.code)).map((major) => major.id);

    // Return course codes that belong to any of the selected majors
    return courseCodes.filter((course) => majorParentIds.includes(course.parentId));
  }, [selectedMajors, courseCodes, majors]);

  // When majors change, update course codes if necessary
  useEffect(() => {
    const currentCourseCodes = form.getValues("courseCodes") || [];

    if (selectedMajors && currentCourseCodes.length > 0) {
      // Get the valid course code options for current selected majors
      const validCourseCodeOptions = filteredCourseCodes.map((course) => course.code);

      // Filter out any course codes that are no longer valid based on selected majors
      const validCourseCodes = currentCourseCodes.filter((code) => validCourseCodeOptions.includes(code));

      // If the valid list is different from current list, update form value
      if (validCourseCodes.length !== currentCourseCodes.length) {
        form.setValue("courseCodes", validCourseCodes);
      }
    }
  }, [selectedMajors, filteredCourseCodes, form]);

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
    setHasFormChanges(false);
  };

  // Helper function to get display values for tags
  const getTagDisplay = (tag: string) => {
    // Check in each master data type
    const majorItem = majors?.find((m) => m.code === tag);
    if (majorItem) return majorItem.translations[i18n.language] || majorItem.translations.en;

    const courseCodeItem = courseCodes?.find((m) => m.code === tag);
    if (courseCodeItem) return courseCodeItem.translations[i18n.language] || courseCodeItem.translations.en;

    const levelItem = levels?.find((l) => l.code === tag);
    if (levelItem) return levelItem.translations[i18n.language] || levelItem.translations.en;

    const categoryItem = categories?.find((c) => c.code === tag);
    if (categoryItem) return categoryItem.translations[i18n.language] || categoryItem.translations.en;

    return tag;
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
              name="majors"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("document.upload.form.majors.label")}</FormLabel>
                  <FormControl>
                    <TagInput
                      value={field.value || []}
                      onChange={field.onChange}
                      recommendedTags={majors?.map((major) => major.code) || []}
                      getTagDisplay={getTagDisplay}
                      disabled={disabled || majors?.length === 0}
                      placeholder={t("document.upload.form.majors.placeholder")}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="courseCodes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("document.upload.form.courseCodes.label")}</FormLabel>
                  <FormControl>
                    <TagInput
                      value={field.value || []}
                      onChange={field.onChange}
                      recommendedTags={filteredCourseCodes.map((course) => course.code)}
                      getTagDisplay={getTagDisplay}
                      disabled={disabled || !selectedMajors.length || filteredCourseCodes.length === 0}
                      placeholder={t("document.upload.form.courseCodes.placeholder")}
                    />
                  </FormControl>
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
              name="categories"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("document.upload.form.categories.label")}</FormLabel>
                  <FormControl>
                    <TagInput
                      value={field.value || []}
                      onChange={field.onChange}
                      recommendedTags={categories?.map((category) => category.code) || []}
                      getTagDisplay={getTagDisplay}
                      disabled={disabled || categories?.length === 0}
                      placeholder={t("document.detail.form.categories.placeholder")}
                    />
                  </FormControl>
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
              disabled={masterDataLoading || loading || !hasFormChanges || (hasFormChanges && polling) || disabled}
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
