import { Check, Edit2, MoreHorizontal, Reply, Trash, X } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/use-toast";

export const CommentItem = ({ comment, currentUser, onDelete, onReply, onEdit }) => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const isAuthor = currentUser?.username === comment.username;
  const [isEditing, setIsEditing] = useState(false);
  const [editedContent, setEditedContent] = useState(comment.content);

  const formatDate = (date) => {
    return new Date(date).toLocaleString();
  };

  const handleEditSubmit = async () => {
    if (!editedContent.trim()) return;

    try {
      await onEdit(comment.id, { content: editedContent });
      setIsEditing(false);
      toast({
        title: t("common.success"),
        description: t("document.comments.editSuccess"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.editError"),
        variant: "destructive"
      });
    }
  };

  const handleCancelEdit = () => {
    setEditedContent(comment.content);
    setIsEditing(false);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-start gap-4">
        <Avatar className="h-8 w-8">
          <AvatarFallback>{comment.username[0].toUpperCase()}</AvatarFallback>
        </Avatar>

        <div className="flex-1 space-y-2">
          <div className="flex items-center gap-2">
            <span className="font-medium">{comment.username}</span>
            <span className="text-sm text-muted-foreground">
                {formatDate(comment.createdAt)}
              </span>
            {comment.edited && (
              <span className="text-xs text-muted-foreground">
                  ({t("document.comments.edited")})
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
                <Button
                  variant="outline"
                  size="sm"
                  onClick={handleCancelEdit}
                >
                  <X className="mr-2 h-4 w-4" />
                  {t("document.comments.cancel")}
                </Button>
              </div>
            </div>
          ) : (
            <p className="text-sm">{comment.content}</p>
          )}

          {!isEditing && (
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                className="h-8"
                onClick={() => onReply(comment)}
              >
                <Reply className="mr-2 h-4 w-4" />
                {t("document.comments.reply")}
              </Button>

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
            />
          ))}
        </div>
      )}
    </div>
  );
};