import { Check, Edit2, MoreHorizontal, Reply, Trash, X } from "lucide-react";
import moment from "moment-timezone";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { ReportCommentDialog } from "@/components/document/discover/report-comment-dialog";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";
import { Comment, CommentEditData, User } from "@/types/comment";
import { formatDateMoment } from "@/lib/utils";

interface CommentItemProps {
  comment: Comment;
  currentUser: User | null;
  onDelete: (commentId: number) => void;
  onReply: (comment: Comment) => void;
  onEdit: (commentId: number, data: CommentEditData) => Promise<boolean>;
  documentId: string;
}

export const CommentItem: React.FC<CommentItemProps> = ({
  comment,
  currentUser,
  onDelete,
  onReply,
  onEdit,
  documentId,
}) => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const isAuthor = currentUser?.username === comment.username;
  const [isEditing, setIsEditing] = useState(false);
  const [editedContent, setEditedContent] = useState(comment.content);

  const [isReportedByUser, setIsReportedByUser] = useState(false);

  const isCommentResolved = comment.flag === -1;

  useEffect(() => {
    if (!isReportedByUser) {
      setIsReportedByUser(comment.reportedByUser && comment.flag !== -1);
    }
  }, [comment.reportedByUser, isReportedByUser]);

  const handleEditSubmit = async (): Promise<void> => {
    if (!editedContent.trim()) return;

    try {
      await onEdit(comment.id, { content: editedContent });
      setIsEditing(false);
      toast({
        title: t("common.success"),
        description: t("document.comments.editSuccess"),
        variant: "success",
      });
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.editError"),
        variant: "destructive",
      });
    }
  };

  const handleCancelEdit = (): void => {
    setEditedContent(comment.content);
    setIsEditing(false);
  };

  const handleReportSuccess = (): void => {
    setIsReportedByUser(true);

    toast({
      title: t("common.success"),
      description: t("document.comments.reportSuccess"),
      variant: "success",
    });
  };

  return (
    <div className="space-y-4">
      <div className={`flex items-start gap-4 ${isCommentResolved ? "opacity-60" : ""}`}>
        <Avatar className="h-8 w-8">
          <AvatarFallback>{comment.username[0].toUpperCase()}</AvatarFallback>
        </Avatar>

        <div className="flex-1 space-y-2">
          <div className="flex items-center gap-2">
            <span className="font-medium">{comment.username}</span>
            <span className="text-sm text-muted-foreground">{formatDateMoment(comment.updatedAt ?  comment.updatedAt.toString() : comment.createdAt.toString())}</span>
            {comment.edited && <span className="text-xs text-muted-foreground">({t("document.comments.edited")})</span>}
            {isCommentResolved && (
              <span className="text-xs bg-red-100 text-red-800 px-2 py-0.5 rounded-full">
                {t("document.comments.report.status.resolved")}
              </span>
            )}
          </div>

          {isEditing ? (
            <div className="space-y-2">
              <Textarea
                value={editedContent}
                onChange={(e) => setEditedContent(e.target.value)}
                className="min-h-[100px]"
              />
              <div className="flex gap-2">
                <Button
                  variant="default"
                  size="sm"
                  onClick={handleEditSubmit}
                  disabled={!editedContent.trim() || editedContent === comment.content}
                >
                  <Check className="mr-2 h-4 w-4" />
                  {t("document.comments.save")}
                </Button>
                <Button variant="outline" size="sm" onClick={handleCancelEdit}>
                  <X className="mr-2 h-4 w-4" />
                  {t("document.comments.cancel")}
                </Button>
              </div>
            </div>
          ) : (
            <p className="text-sm">
              {isCommentResolved ? t("document.comments.report.removedContent") : comment.content}
            </p>
          )}

          {!isEditing && !isCommentResolved && (
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" className="h-8" onClick={() => onReply(comment)}>
                <Reply className="mr-2 h-4 w-4" />
                {t("document.comments.reply")}
              </Button>

              {!isAuthor && (
                <ReportCommentDialog
                  documentId={documentId}
                  commentId={comment.id}
                  commentAuthor={comment.username}
                  isReported={isReportedByUser}
                  onReportSuccess={handleReportSuccess}
                />
              )}

              {isAuthor && (
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm" className="h-8">
                      <MoreHorizontal className="h-4 w-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem onClick={() => setIsEditing(true)}>
                      <Edit2 className="mr-2 h-4 w-4" />
                      {t("document.comments.edit")}
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={() => onDelete(comment.id)}>
                      <Trash className="mr-2 h-4 w-4" />
                      {t("document.comments.delete")}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Replies */}
      {comment.replies?.length > 0 && (
        <div className="ml-12 space-y-4">
          {comment.replies.map((reply) => (
            <CommentItem
              key={reply.id}
              comment={reply}
              currentUser={currentUser}
              onDelete={onDelete}
              onReply={onReply}
              onEdit={onEdit}
              documentId={documentId}
            />
          ))}
        </div>
      )}
    </div>
  );
};
