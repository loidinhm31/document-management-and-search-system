import { LightbulbIcon, Loader2, Pencil } from "lucide-react";
import moment from "moment-timezone";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { DocumentNoteForm } from "@/components/document/discover/note/document-note-form";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { documentNoteService } from "@/services/document-note.service";
import { NoteResponse } from "@/types/document-note";

interface DocumentNoteListProps {
  documentId: string;
}

export function DocumentNoteList({ documentId }: DocumentNoteListProps) {
  const { t } = useTranslation();
  const { toast } = useToast();
  const { currentUser, role } = useAuth();

  const isMentor = role === "ROLE_MENTOR";

  const [notes, setNotes] = useState<NoteResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [mentorHasNote, setMentorHasNote] = useState(false);
  const [isAdding, setIsAdding] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [checkingStatus, setCheckingStatus] = useState(false);

  const fetchNotes = async () => {
    setLoading(true);
    try {
      const response = await documentNoteService.getAllNotes(documentId);
      setNotes(response.data);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.notes.fetchError"),
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  };

  const checkMentorNoteStatus = async () => {
    if (!isMentor) return;

    setCheckingStatus(true);
    try {
      const response = await documentNoteService.hasNote(documentId);
      setMentorHasNote(response.data);
    } catch (error) {
      console.error("Error checking mentor note status:", error);
    } finally {
      setCheckingStatus(false);
    }
  };

  useEffect(() => {
    fetchNotes();
    checkMentorNoteStatus();
  }, [documentId, isMentor]);

  const handleAddNoteSuccess = () => {
    setIsAdding(false);
    fetchNotes();
    checkMentorNoteStatus();
  };

  const handleEditNoteSuccess = () => {
    setIsEditing(false);
    fetchNotes();
    checkMentorNoteStatus();
  };

  const formatDate = (dateString: string) => {
    return moment(dateString).format("DD/MM/YYYY, h:mm a");
  };

  const renderSkeleton = () => (
    <div className="space-y-4">
      <Skeleton className="h-20 w-full" />
      <Skeleton className="h-20 w-full" />
    </div>
  );

  const renderNoteItem = (note: NoteResponse) => {
    const isCurrentUserNote = currentUser?.userId === note?.mentorId;

    return (
      <Card key={note.id} className="mb-4">
        <CardHeader className="pb-2">
          <div className="flex justify-between items-center">
            <div className="flex items-center gap-2">
              <Avatar className="h-6 w-6">
                <AvatarFallback>{note?.mentorUsername[0]?.toUpperCase()}</AvatarFallback>
              </Avatar>
              <CardTitle className="text-sm font-medium">{note?.mentorUsername}</CardTitle>
            </div>

            {isCurrentUserNote && isMentor && !isEditing && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setIsEditing(true)}
                className="h-8 w-8 p-0"
              >
                <Pencil className="h-4 w-4" />
                <span className="sr-only">{t("document.notes.edit")}</span>
              </Button>
            )}
          </div>
          <CardDescription className="text-xs">
            {formatDate(note.createdAt)}
            {note.edited && (
              <span className="ml-2 italic">
                ({t("document.comments.edited")})
              </span>
            )}
          </CardDescription>
        </CardHeader>

        <CardContent className="pt-0">
          {isCurrentUserNote && isEditing ? (
            <DocumentNoteForm
              documentId={documentId}
              currentNote={note}
              onSuccess={handleEditNoteSuccess}
              onCancel={() => setIsEditing(false)}
            />
          ) : (
            <p className="text-sm">{note.content}</p>
          )}
        </CardContent>
      </Card>
    );
  };

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <LightbulbIcon className="h-5 w-5" />
            {t("document.notes.title")}
          </CardTitle>
          <CardDescription>{t("document.notes.description")}</CardDescription>
        </CardHeader>
        <CardContent>
          {renderSkeleton()}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex justify-between items-center">
          <div>
            <CardTitle className="flex items-center gap-2">
              <LightbulbIcon className="h-5 w-5" />
              {t("document.notes.title")}
            </CardTitle>
            <CardDescription>{t("document.notes.description")}</CardDescription>
          </div>

          {isMentor && !mentorHasNote && !isAdding && (
            <Button
              size="sm"
              onClick={() => setIsAdding(true)}
              disabled={checkingStatus}
            >
              {checkingStatus ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Pencil className="mr-2 h-4 w-4" />
              )}
              {t("document.notes.add")}
            </Button>
          )}
        </div>
      </CardHeader>

      <CardContent>
        {isAdding && (
          <div className="mb-4">
            <Card className="border-primary/50 bg-primary/5">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium">{t("document.notes.addNew")}</CardTitle>
              </CardHeader>
              <CardContent>
                <DocumentNoteForm
                  documentId={documentId}
                  onSuccess={handleAddNoteSuccess}
                  onCancel={() => setIsAdding(false)}
                />
              </CardContent>
            </Card>
          </div>
        )}

        {notes.length > 0 ? (
          notes.map(renderNoteItem)
        ) : (
          <p className="text-center text-muted-foreground py-4">
            {t("document.notes.empty")}
          </p>
        )}
      </CardContent>
    </Card>
  );
}