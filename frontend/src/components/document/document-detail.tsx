import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Loader2, Upload } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useDropzone } from "react-dropzone";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import * as z from "zod";

import { DocumentViewer } from "@/components/document/document-viewer";
import TagInput from "@/components/tag-input";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";

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

const documentSchema = z.object({
  courseCode: z.string().min(1, "Course code is required"),
  major: z.string().min(1, "Major is required"),
  level: z.string().min(1, "Course level is required"),
  category: z.string().min(1, "Document category is required"),
  tags: z.array(z.string()).optional()
});

type DocumentFormValues = z.infer<typeof documentSchema>;

const DocumentDetail = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { documentId } = useParams<{ documentId: string }>();
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState(false);
  const [documentData, setDocumentData] = useState(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const { toast } = useToast();

  const form = useForm<DocumentFormValues>({
    resolver: zodResolver(documentSchema),
    defaultValues: {
      courseCode: "",
      major: "",
      level: "",
      category: "",
      tags: []
    }
  });

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

  useEffect(() => {
    const fetchDocument = async () => {
      if (!documentId) return;

      try {
        const response = await documentService.getDocumentDetails(documentId);
        const document = response.data;
        setDocumentData(document);

        form.reset({
          courseCode: document.courseCode,
          major: document.major,
          level: document.courseLevel,
          category: document.category,
          tags: document.tags || []
        });
      } catch (error) {
        toast({
          title: t("common.error"),
          description: t("document.detail.fetchError"),
          variant: "destructive"
        });
        navigate("/document");
      } finally {
        setLoading(false);
      }
    };

    fetchDocument();
  }, [documentId, form, navigate, t, toast]);

  const onSubmit = async (data: DocumentFormValues) => {
    if (!documentId) return;
    setUpdating(true);

    try {
      // Update metadata
      await documentService.updateDocument(documentId, {
        courseCode: data.courseCode,
        major: data.major,
        level: data.level,
        category: data.category,
        tags: data.tags
      });

      // Update file if selected
      if (selectedFile) {
        const formData = new FormData();
        formData.append('file', selectedFile);
        await documentService.updateFile(documentId, formData);
        setSelectedFile(null);

        // Refresh document data to get new file info
        const response = await documentService.getDocumentDetails(documentId);
        setDocumentData(response.data);
      }

      toast({
        title: t("common.success"),
        description: t("document.detail.updateSuccess"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.detail.updateError"),
        variant: "destructive"
      });
    } finally {
      setUpdating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Button variant="ghost" onClick={() => navigate("/document")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        {t("document.detail.backToList")}
      </Button>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        {/* Document Form */}
        <Card>
          <CardHeader>
            <CardTitle>{t("document.detail.title")}</CardTitle>
            <CardDescription>{t("document.detail.description")}</CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                {/* File Upload Section */}
                <div className="space-y-4">
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
                          {t("document.upload.dropzone.supportedFormats")} PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, CSV, TXT, JSON, XML
                        </p>
                      </div>
                    )}
                    {selectedFile && (
                      <p className="mt-2 text-sm text-muted-foreground">
                        Selected: {selectedFile.name}
                      </p>
                    )}
                  </div>
                </div>

                <Separator />

                {/* Metadata Form Fields */}
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
                      <FormLabel>{t("document.detail.form.major.label")}</FormLabel>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder={t("document.detail.form.major.placeholder")} />
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
                      <FormLabel>{t("document.detail.form.level.label")}</FormLabel>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder={t("document.detail.form.level.placeholder")} />
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
                      <FormLabel>{t("document.detail.form.category.label")}</FormLabel>
                      <Select onValueChange={field.onChange} value={field.value}>
                        <FormControl>
                          <SelectTrigger>
                            <SelectValue placeholder={t("document.detail.form.category.placeholder")} />
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
                      <FormLabel>{t("document.detail.form.tags.label")}</FormLabel>
                      <FormControl>
                        <TagInput
                          value={field.value || []}
                          onChange={field.onChange}
                          placeholder={t("document.detail.form.tags.placeholder")}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <Button type="submit" className="w-full" disabled={updating}>
                  {updating && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  {t("document.detail.buttons.update")}
                </Button>
              </form>
            </Form>
          </CardContent>
        </Card>

        {/* Document Preview */}
        <Card className="xl:h-[800px]">
          <CardHeader>
            <CardTitle>{documentData?.originalFilename}</CardTitle>
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
      </div>
    </div>
  );
};

export default DocumentDetail;