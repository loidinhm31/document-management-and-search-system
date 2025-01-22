import { Share2 } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";

interface ShareDocumentDialogProps {
  documentId: string;
  documentName: string;
  isShared: boolean;
  onShareToggle: (isShared: boolean) => void;
}

export default function ShareDocumentDialog({
                                              documentId,
                                              documentName,
                                              isShared,
                                              onShareToggle
                                            }: ShareDocumentDialogProps) {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);

  const handleSharingToggle = async (enabled: boolean) => {
    setIsUpdating(true);
    try {
      const response = await documentService.toggleSharing(documentId, enabled);
      if (response.data) {
        onShareToggle(enabled);
        toast({
          title: t("common.success"),
          description: enabled ? t("document.myDocuments.share.enableSuccess") : t("document.myDocuments.share.disableSuccess"),
          variant: "success"
        });
      }
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.share.error"),
        variant: "destructive"
      });
      // Force switch back to previous state
      onShareToggle(!enabled);
    } finally {
      setIsUpdating(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="w-full"
          onClick={(e) => {
            e.stopPropagation();
          }}
        >
          <Share2 className="mr-2 h-4 w-4" />
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("document.myDocuments.share.title")}</DialogTitle>
          <DialogDescription>
            {t("document.myDocuments.share.description", { name: documentName })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <h4 className="text-sm font-medium">{t("document.myDocuments.share.toggle.title")}</h4>
              <p className="text-sm text-muted-foreground">
                {t("document.myDocuments.share.toggle.description")}
              </p>
            </div>
            <Switch
              checked={isShared}
              disabled={isUpdating}
              onCheckedChange={handleSharingToggle}
            />
          </div>

          <div className="border-t pt-4">
            <p className="text-sm text-muted-foreground">
              {isShared
                ? t("document.myDocuments.share.status.enabled")
                : t("document.myDocuments.share.status.disabled")}
            </p>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}