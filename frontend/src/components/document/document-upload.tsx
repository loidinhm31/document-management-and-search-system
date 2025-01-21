import React, { useState } from "react";
import { useDropzone } from "react-dropzone";
import { Loader2, Upload } from "lucide-react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import * as z from "zod";

import { Button } from "@/components/ui/button";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { documentService } from "@/services/document.service";
import { useToast } from "@/hooks/use-toast";
import TagInput from "@/components/tag-input";
import { useTranslation } from "react-i18next";

const COURSE_LEVELS = [
  { code: "FUNDAMENTAL", name: "Fundamental" },
  { code: "INTERMEDIATE", name: "Intermediate" },
  { code: "ADVANCED", name: "Advanced" },
  { code: "SPECIALIZED", name: "Specialized" }
];

const MAJORS = [
  { code: "SOFTWARE_ENGINEERING", name: "Software Engineering" },
  { code: "ARTIFICIAL_INTELLIGENCE", name: "Artificial Intelligence" },
  { code: "INFORMATION_SECURITY", name: "Information Security" },
  { code: "IOT", name: "Internet Of Things" }
];

const DOCUMENT_CATEGORIES = [
  { value: "LECTURE", name: "Lecture materials" },
  { value: "EXERCISE", name: "Exercises and assignments" },
  { value: "EXAM", name: "Exam materials" },
  { value: "REFERENCE", name: "Reference materials" },
  { value: "LAB", name: "Lab instructions" },
  { value: "PROJECT", name: "Project examples" }
];

const formSchema = z.object({
  courseCode: z.string().min(1, "Course code is required"),
  major: z.string().min(1, "Major is required"),
  level: z.string().min(1, "Course level is required"),
  category: z.string().min(1, "Document category is required"),
  tags: z.array(z.string()).optional()
});

export const DocumentUpload = () => {
  const { t } = useTranslation();

  const [uploading, setUploading] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const { toast } = useToast();

  const form = useForm({
    resolver: zodResolver(formSchema),
    defaultValues: {
      courseCode: "",
      major: "",
      level: "",
      category: "",
      tags: []
    }
  });

  const onDrop = React.useCallback(acceptedFiles => {
    if (acceptedFiles?.length > 0) {
      setSelectedFile(acceptedFiles[0]);
    }
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
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

  const onSubmit = async (data) => {
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
      formData.append("courseCode", data.courseCode);
      formData.append("major", data.major);
      formData.append("level", data.level);
      formData.append("category", data.category);

      // Clean and handle tags properly
      const cleanedTags = (data.tags || [])
        .map(tag => tag.trim())
        .filter(Boolean);

      if (cleanedTags.length > 0) {
        // Send tags as a simple array, let the service handle the formatting
        formData.append("tags", cleanedTags);
      }

      await documentService.uploadDocument(formData);

      toast({
        title: t("common.success"),
        description: t("document.upload.messages.success"),
        variant: "success"
      });

      form.reset({
        courseCode: "",
        major: "",
        level: "",
        category: "",
        tags: []
      });
      setSelectedFile(null);

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
            <p>Drag & drop a file here, or click to select file</p>
            <p className="text-sm text-muted-foreground">
              {t("document.upload.dropzone.supportedFormats")} PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, CSV, TXT, JSON, XML
            </p>
          </div>
        )}
        {selectedFile && (
          <p className="mt-2 text-sm text-muted-foreground">
            {t("Selected")}: {selectedFile.name}
          </p>
        )}
      </div>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <FormField
            control={form.control}
            name="courseCode"
            render={({ field }) => (
              <FormItem>
                <FormLabel>{t("document.upload.form.courseCode.label")}</FormLabel>
                <FormControl>
                  <Input placeholder={t("document.upload.form.courseCode.placeholder")} {...field} />
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
                    {MAJORS.map((major) => (
                      <SelectItem key={major.code} value={major.code}>
                        {major.name}
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
                    {COURSE_LEVELS.map((level) => (
                      <SelectItem key={level.code} value={level.code}>
                        {level.name}
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
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder={t("document.upload.form.category.placeholder")} />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {DOCUMENT_CATEGORIES.map((category) => (
                      <SelectItem key={category.value} value={category.value}>
                        {category.name}
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
                <FormLabel>{t("document.upload.form.tags.label")}</FormLabel>
                <FormControl>
                  <TagInput
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