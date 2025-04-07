import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/hooks/use-toast";
import { createMasterDataFormSchema, MasterDataFormValues } from "@/schemas/master-data-schema";
import { masterDataService } from "@/services/master-data.service";
import { MASTER_DATA_HIERARCHY, MasterData, MasterDataType } from "@/types/master-data";

interface MasterDataDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  type: MasterDataType;
  data?: MasterData | null;
  onSuccess: () => void;
  onClose: () => void;
}

export default function MasterDataDialog({
  open,
  onOpenChange,
  type,
  data,
  onSuccess,
  onClose,
}: MasterDataDialogProps) {
  const { t, i18n } = useTranslation();
  const { toast } = useToast();
  const isEditing = !!data;

  const [potentialParentTypes, setPotentialParentTypes] = useState<MasterDataType[]>([]);
  const [parentOptions, setParentOptions] = useState<MasterData[]>([]);
  const [loadingParents, setLoadingParents] = useState(false);

  const form = useForm<MasterDataFormValues>({
    resolver: zodResolver(createMasterDataFormSchema(t)),
    mode: "onBlur",
    defaultValues: {
      code: "",
      translations: {
        en: "",
        vi: "",
      },
      description: "",
      isActive: true,
      parentId: "",
    },
  });

  useEffect(() => {
    // Get fields that have been touched by the user
    const touchedFields = Object.keys(form.formState.touchedFields);

    // Only trigger validation for fields the user has interacted with
    if (touchedFields.length > 0) {
      form.trigger(touchedFields as any);
    }
  }, [i18n.language]);

  // Check if this type needs a parent type
  useEffect(() => {
    if (type) {
      const parentTypes = MASTER_DATA_HIERARCHY[type] || [];
      setPotentialParentTypes(parentTypes);

      // If this type requires a parent, load potential parents
      if (parentTypes.length > 0) {
        loadParentOptions(parentTypes[0]); // Default to first parent type
      }
    }
  }, [type]);

  const loadParentOptions = async (parentType: MasterDataType) => {
    setLoadingParents(true);
    try {
      const response = await masterDataService.getAllByType(parentType, true);
      setParentOptions(response.data);
    } catch (error) {
      console.info("Error loading parent options:", error);
      toast({
        title: t("common.error"),
        description: t("masterData.fetchParentError"),
        variant: "destructive",
      });
    } finally {
      setLoadingParents(false);
    }
  };

  // Reset form when dialog opens/closes or when data changes
  useEffect(() => {
    if (open) {
      // If editing, set form values from data
      if (data) {
        form.reset({
          code: data.code || "",
          translations: {
            en: data.translations?.en || "",
            vi: data.translations?.vi || "",
          },
          description: data.description || "",
          isActive: data.active ?? true,
          parentId: data.parentId || "none",
        });
      } else {
        // If creating new, reset to default values
        form.reset({
          code: "",
          translations: {
            en: "",
            vi: "",
          },
          description: "",
          isActive: true,
          parentId: "none",
        });
      }
    }
  }, [open, data, form]);

  const onSubmit = async (values: MasterDataFormValues) => {
    try {
      const masterDataItem: MasterData = {
        id: data?.id,
        type,
        code: values.code,
        translations: {
          en: values.translations.en,
          vi: values.translations.vi,
        },
        active: values.isActive,
        description: values.description,
        parentId: values.parentId && values.parentId !== "none" ? values.parentId : undefined,
      };

      const response = await masterDataService.save(masterDataItem);

      if (isEditing) {
        if (response.data.fullUpdate) {
          toast({
            title: t("common.success"),
            description: t("admin.masterData.updateFullSuccess"),
            variant: "success",
          });
        } else {
          toast({
            title: t("common.success"),
            description: t("admin.masterData.updateNonFullSuccess"),
            variant: "success",
          });
        }
      } else {
        toast({
          title: t("common.success"),
          description: t("admin.masterData.createSuccess"),
          variant: "success",
        });
      }

      // Reset form and close dialog before calling onSuccess
      form.reset();
      onOpenChange(false);
      onSuccess();
    } catch (error) {
      if (error.response && error.response.status === 409) {
        if (error.response.data.message === "MASTER_DATA_ALREADY_CREATED") {
          toast({
            title: t("common.error"),
            description: t("admin.masterData.createdError"),
            variant: "destructive",
          });
        } else if (error.response.data.message === "INVALID_PARENT_MASTER_DATA") {
          toast({
            title: t("common.error"),
            description: t("admin.masterData.invalidParentError"),
            variant: "destructive",
          });
        }
      } else {
        toast({
          title: t("common.error"),
          description: t(isEditing ? "masterData.updateError" : "masterData.createError"),
          variant: "destructive",
        });
      }
    }
  };

  const handleClose = () => {
    form.reset();
    onOpenChange(false);
    onClose();
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {isEditing ? t("admin.masterData.dialog.editTitle") : t("admin.masterData.dialog.createTitle")}
          </DialogTitle>
          <DialogDescription>{t("admin.masterData.dialog.description")}</DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="code"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("admin.masterData.dialog.fields.code")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder={t("admin.masterData.dialog.placeholders.code")} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="translations.en"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("admin.masterData.dialog.fields.nameEn")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder={t("admin.masterData.dialog.placeholders.nameEn")} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="translations.vi"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("admin.masterData.dialog.fields.nameVi")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder={t("admin.masterData.dialog.placeholders.nameVi")} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Parent Selection - Only show if this type can have a parent */}
            {potentialParentTypes.length > 0 && (
              <FormField
                control={form.control}
                name="parentId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("admin.masterData.dialog.fields.parent")}</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange} disabled={loadingParents}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue
                            placeholder={
                              loadingParents ? t("common.loading") : t("admin.masterData.dialog.placeholders.parent")
                            }
                          />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="none">{t("admin.masterData.dialog.noParent")}</SelectItem>
                        {parentOptions.map((parent) => (
                          <SelectItem key={parent.id} value={parent.id}>
                            {parent.translations[i18n.language] || parent.translations.en}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("admin.masterData.dialog.fields.description")}</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder={t("admin.masterData.dialog.placeholders.description")} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="isActive"
              render={({ field }) => (
                <FormItem className="flex flex-row items-center justify-between rounded-lg border p-3 shadow-sm">
                  <div className="space-y-0.5">
                    <FormLabel>{t("admin.masterData.dialog.fields.active")}</FormLabel>
                  </div>
                  <FormControl>
                    <Switch checked={field.value} onCheckedChange={field.onChange} />
                  </FormControl>
                </FormItem>
              )}
            />

            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={handleClose}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                {form.formState.isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                {isEditing ? t("common.save") : t("common.create")}
              </Button>
            </div>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
