import { Check, Edit2, MoreHorizontal, Reply, Trash, X } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";

export const CommentSection = ({ documentId }) => {
  const { t } = useTranslation();
  const [comments, setComments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [commentText, setCommentText] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [replyTo, setReplyTo] = useState(null);
  const { currentUser } = useAuth();
  const { toast } = useToast();

  const fetchComments = async () => {
    setLoading(true);
    try {
      const response = await documentService.getDocumentComments(documentId, {
        page: currentPage,
        size: 10,
        sort: "createdAt,desc"
      });
      setComments(response.data.content);
      setTotalPages(response.data.totalPages);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.fetchError"),
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  };

  const CommentItem = ({ comment, currentUser, onDelete, onReply, onEdit }) => {
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

  useEffect(() => {
    fetchComments();
  }, [documentId, currentPage]);

  const handleSubmitComment = async () => {
    if (!commentText.trim()) return;

    try {
      const response = await documentService.createComment(documentId, {
        content: commentText,
        parentId: replyTo?.id || null
      });

      // Add new comment to the list
      if (replyTo) {
        setComments(comments.map(comment =>
          comment.id === replyTo.id
            ? { ...comment, replies: [...comment.replies, response.data] }
            : comment
        ));
      } else {
        setComments([response.data, ...comments]);
      }

      // Clear form
      setCommentText("");
      setReplyTo(null);

      toast({
        title: t("common.success"),
        description: t("document.comments.createSuccess"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.createError"),
        variant: "destructive"
      });
    }
  };

  const handleDeleteComment = async (commentId) => {
    try {
      await documentService.deleteComment(documentId, commentId);

      // Remove comment from the list
      setComments(comments.filter(c => c.id !== commentId));

      toast({
        title: t("common.success"),
        description: t("document.comments.deleteSuccess"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.deleteError"),
        variant: "destructive"
      });
    }
  };

  const handleEditComment = async (commentId, { content }) => {
    try {
      const response = await documentService.updateComment(documentId, commentId, {
        content
      });

      // Update the comment in the tree
      setComments(comments.map(comment => {
        if (comment.id === commentId) {
          return response.data;
        }
        if (comment.replies) {
          return {
            ...comment,
            replies: comment.replies.map(reply =>
              reply.id === commentId ? response.data : reply
            )
          };
        }
        return comment;
      }));

    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.editError"),
        variant: "destructive"
      });
      throw error; // Re-throw to handle in the CommentItem component
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <Label>{t("document.comments.title")}</Label>

        {/* Comment input */}
        <div className="space-y-2">
          <Textarea
            placeholder={t("document.comments.placeholder")}
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
          />
          {replyTo && (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <span>{t("document.comments.replyingTo", { username: replyTo.username })}</span>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setReplyTo(null)}
              >
                {t("document.comments.cancelReply")}
              </Button>
            </div>
          )}
          <Button
            onClick={handleSubmitComment}
            disabled={!commentText.trim()}
          >
            {t("document.comments.submit")}
          </Button>
        </div>

        {/* Comments list */}
        <div className="space-y-4">
          {comments.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              currentUser={currentUser}
              onDelete={handleDeleteComment}
              onReply={setReplyTo}
              onEdit={handleEditComment}
            />
          ))}
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex justify-center gap-2 mt-4">
            <Button
              variant="outline"
              onClick={() => setCurrentPage(prev => Math.max(0, prev - 1))}
              disabled={currentPage === 0}
            >
              {t("document.comments.previous")}
            </Button>
            <Button
              variant="outline"
              onClick={() => setCurrentPage(prev => Math.min(totalPages - 1, prev + 1))}
              disabled={currentPage === totalPages - 1}
            >
              {t("document.comments.next")}
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};