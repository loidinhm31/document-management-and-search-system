import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";

import { DeleteDialog } from "@/components/common/delete-dialog";
import { CommentItem } from "@/components/document/discover/comment/comment-item";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";

const CommentSkeleton = () => (
  <div className="space-y-4">
    <div className="flex items-start gap-4">
      <Skeleton className="h-8 w-8 rounded-full" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-16 w-full" />
      </div>
    </div>
  </div>
);

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

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteInProgress, setDeleteInProgress] = useState(false);
  const [commentToDelete, setCommentToDelete] = useState(null);

  const commentsRef = useRef(comments);
  useEffect(() => {
    commentsRef.current = comments;
  }, [comments]);

  const fetchComments = useCallback(
    async (page = 0) => {
      if (!documentId) return;

      setLoading(true);
      try {
        const response = await documentService.getDocumentComments(documentId, {
          page,
          size: 10,
          sort: "createdAt,desc",
        });
        setComments(response.data.content);
        setTotalPages(response.data.totalPages);
        setCurrentPage(page);
      } catch (error) {
        toast({
          title: t("common.error"),
          description: t("document.comments.fetchError"),
          variant: "destructive",
        });
      } finally {
        setLoading(false);
      }
    },
    [documentId, toast, t],
  );

  // Initial load and document change handler
  useEffect(() => {
    fetchComments(0);
  }, [documentId, fetchComments]);

  const handlePreviousPage = () => {
    if (currentPage > 0) {
      fetchComments(currentPage - 1);
    }
  };

  const handleNextPage = () => {
    if (currentPage < totalPages - 1) {
      fetchComments(currentPage + 1);
    }
  };

  const handleSubmitComment = async () => {
    const commentTrim = commentText.trim();
    if (!commentTrim || loading) return;

    setLoading(true);
    try {
      const response = await documentService.createComment(documentId, {
        content: commentTrim,
        parentId: replyTo?.id || null,
      });

      if (replyTo) {
        // Update comments state with new reply
        setComments((prevComments) =>
          updateCommentInState(prevComments, replyTo.id, {
            replies: [...(replyTo.replies || []), response.data],
          }),
        );
      } else {
        // Add new top-level comment
        setComments((prevComments) => [response.data, ...prevComments]);
      }

      // Clear form
      setCommentText("");
      setReplyTo(null);

      toast({
        title: t("common.success"),
        description: t("document.comments.createSuccess"),
        variant: "success",
      });
    } catch (error) {
      console.info(error);
      if (error.status === 400) {
        if (error.response?.data?.message === "PARENT_COMMENT_DELETED" && replyTo) {
          setReplyTo(null);
        }
      }
      toast({
        title: t("common.error"),
        description: t("document.comments.createError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const removeCommentFromState = (comments, commentId) => {
    return comments.filter((comment) => {
      // If this comment matches the ID, remove it
      if (comment.id === commentId) {
        return false;
      }

      // If this comment has replies, recursively filter them
      if (comment.replies?.length > 0) {
        comment.replies = removeCommentFromState(comment.replies, commentId);
      }
      return true;
    });
  };

  const updateCommentInState = (comments, commentId, updatedFields) => {
    return comments.map((comment) => {
      if (comment.id === commentId) {
        return { ...comment, ...updatedFields };
      }
      if (comment.replies?.length > 0) {
        return {
          ...comment,
          replies: updateCommentInState(comment.replies, commentId, updatedFields),
        };
      }
      return comment;
    });
  };

  // Handle opening the delete dialog
  const handleDeleteClick = useCallback((commentId) => {
    setCommentToDelete(commentId);
    // Use a timeout to ensure React has processed state changes
    setTimeout(() => {
      setDeleteDialogOpen(true);
    }, 0);
  }, []);

  // Handle the actual deletion after confirmation
  const handleConfirmDelete = useCallback(async () => {
    if (!commentToDelete) return;

    setDeleteInProgress(true);
    try {
      await documentService.deleteComment(documentId, commentToDelete);

      // Update the comments state
      setComments((prevComments) => removeCommentFromState(prevComments, commentToDelete));

      // Check if we need to clear the reply state
      if (replyTo?.id === commentToDelete) {
        setReplyTo(null);
      }

      toast({
        title: t("common.success"),
        description: t("document.comments.deleteSuccess"),
        variant: "success",
      });
    } catch (error) {
      console.error("Error deleting comment:", error);
      toast({
        title: t("common.error"),
        description: t("document.comments.deleteError"),
        variant: "destructive",
      });
    } finally {
      // Clean up all deletion-related state
      setDeleteInProgress(false);

      // Close the dialog first
      setDeleteDialogOpen(false);

      // Then clear the comment ID after a short delay
      setTimeout(() => {
        setCommentToDelete(null);
      }, 100);
    }
  }, [commentToDelete, documentId, replyTo, toast, t]);

  // Handle dialog close
  const handleDialogClose = useCallback((open) => {
    if (!open) {
      setDeleteDialogOpen(false);
      // Clear the comment ID after dialog closes
      setTimeout(() => {
        setCommentToDelete(null);
      }, 100);
    }
  }, []);

  const handleEditComment = async (commentId, { content }) => {
    try {
      await documentService.updateComment(documentId, commentId, { content });

      // Update comment locally instead of refetching
      setComments((prevComments) =>
        updateCommentInState(prevComments, commentId, {
          content,
          edited: true,
          updatedAt: new Date().toISOString(),
        }),
      );

      toast({
        title: t("common.success"),
        description: t("document.comments.editSuccess"),
        variant: "success",
      });

      return true;
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.editError"),
        variant: "destructive",
      });
      throw error;
    }
  };

  return (
    <div className="space-y-6" key={documentId}>
      {/* Comment input section */}
      <div className="space-y-4">
        <div className="space-y-2">
          <Textarea
            placeholder={t("document.comments.placeholder")}
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
          />
          {replyTo && (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <span>{t("document.comments.replyingTo", { username: replyTo.username })}</span>
              <Button variant="ghost" size="sm" onClick={() => setReplyTo(null)}>
                {t("document.comments.cancelReply")}
              </Button>
            </div>
          )}
          <Button onClick={handleSubmitComment} disabled={!commentText.trim()}>
            {t("document.comments.submit")}
          </Button>
        </div>

        {/* Comments list */}
        <div className="space-y-4">
          {loading ? (
            <>
              <CommentSkeleton />
              <CommentSkeleton />
              <CommentSkeleton />
            </>
          ) : (
            comments.map((comment) => (
              <CommentItem
                key={comment.id}
                comment={comment}
                currentUser={currentUser}
                onDelete={handleDeleteClick}
                onReply={setReplyTo}
                onEdit={handleEditComment}
                documentId={documentId}
              />
            ))
          )}
        </div>

        {/* Pagination */}
        {totalPages > 1 ? (
          <div className="flex justify-center gap-2 mt-4">
            <Button variant="outline" onClick={handlePreviousPage} disabled={currentPage === 0 || loading}>
              {t("document.comments.previous")}
            </Button>
            <Button variant="outline" onClick={handleNextPage} disabled={currentPage === totalPages - 1 || loading}>
              {t("document.comments.next")}
            </Button>
          </div>
        ) : (
          comments.length === 0 && <p className="flex justify-center">{t("document.comments.empty")}</p>
        )}
      </div>

      {/* Delete confirmation dialog - always render it but control visibility with open prop */}
      <DeleteDialog
        open={deleteDialogOpen}
        onOpenChange={handleDialogClose}
        onConfirm={handleConfirmDelete}
        loading={deleteInProgress}
        description={t("document.comments.confirmDeleteMessage")}
      />
    </div>
  );
};
