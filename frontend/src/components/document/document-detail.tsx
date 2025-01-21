import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Loader2 } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";
import * as z from "zod";

import TagInput from "@/components/tag-input";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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

  useEffect(() => {
    const fetchDocument = async () => {
      if (!documentId) return;

      try {
        const response = await documentService.getDocumentDetails(documentId);
        console.log("response", response.data);
        const document = response.data;

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

    try {
      await documentService.updateDocument(documentId, {
        courseCode: data.courseCode,
        major: data.major,
        level: data.level,
        category: data.category
      });

      // Update tags separately if they've changed
      const currentTags = form.getValues("tags");
      if (JSON.stringify(currentTags) !== JSON.stringify(data.tags)) {
        await documentService.updateTags(documentId, data.tags || []);
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

      <Card>
        <CardHeader>
          <CardTitle>{t("document.detail.title")}</CardTitle>
          <CardDescription>{t("document.detail.description")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
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

              <Button type="submit" className="w-full">{t("document.detail.buttons.update")}</Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
};

export default DocumentDetail;