import { Check, Loader2, X } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";
import { documentNoteService } from "@/services/document-note.service";
import { NoteResponse } from "@/types/document-note";

interface DocumentNoteFormProps {
  documentId: string;
  currentNote?: NoteResponse;
  onSuccess: () => void;
  onCancel: () => void;
}

export function DocumentNoteForm({ documentId, currentNote, onSuccess, onCancel }: DocumentNoteFormProps) {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [content, setContent] = useState(currentNote?.content || "");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isEditing = !!currentNote;
  const MAX_CHARS = 200;

  const handleSubmit = async () => {
    if (!content.trim()) return;

    if (content.length > MAX_CHARS) {
      toast({
        title: t("common.error"),
        description: t("document.notes.error.tooLong", { max: MAX_CHARS }),
        variant: "destructive",
      });
      return;
    }

    setIsSubmitting(true);

    try {
      await documentNoteService.createOrUpdateNote(documentId, { content: content.trim() });

      toast({
        title: t("common.success"),
        description: isEditing ? t("document.notes.editSuccess") : t("document.notes.addSuccess"),
        variant: "success",
      });

      onSuccess();
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: isEditing ? t("document.notes.editError") : t("document.notes.addError"),
        variant: "destructive",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      <Textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={t("document.notes.placeholder")}
        className="min-h-[120px]"
      />

      <div className="flex justify-between items-center">
        <div className="text-sm text-muted-foreground">
          {content.length} / {MAX_CHARS} {t("document.notes.characters")}
          {content.length > MAX_CHARS && <span className="ml-1 text-destructive">({t("document.notes.tooLong")})</span>}
        </div>

        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onCancel} disabled={isSubmitting}>
            <X className="mr-2 h-4 w-4" />
            {t("common.cancel")}
          </Button>

          <Button
            size="sm"
            onClick={handleSubmit}
            disabled={!content.trim() || content.length > MAX_CHARS || isSubmitting}
          >
            {isSubmitting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Check className="mr-2 h-4 w-4" />}
            {isEditing ? t("document.notes.update") : t("document.notes.save")}
          </Button>
        </div>
      </div>
    </div>
  );
}
