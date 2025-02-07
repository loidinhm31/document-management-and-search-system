import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { CommentItem } from "@/components/document/discover/comment-item";
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

  const handleSubmitComment = async () => {
    const commentTrim = commentText.trim();
    if (!commentTrim || loading) return;

    setLoading(true);
    try {
      const response = await documentService.createComment(documentId, {
        content: commentTrim,
        parentId: replyTo?.id || null
      });

      if (replyTo) {
        // Helper function to update nested comments
        const updateCommentsWithReply = (commentsList) => {
          return commentsList.map(comment => {
            if (comment.id === replyTo.id) {
              // Found the parent comment, add the reply
              return {
                ...comment,
                replies: [...(comment.replies || []), response.data]
              };
            }
            // If this comment has replies, recursively search them
            if (comment.replies?.length > 0) {
              return {
                ...comment,
                replies: updateCommentsWithReply(comment.replies)
              };
            }
            return comment;
          });
        };

        // Update the comments state with the new reply
        setComments(prevComments => updateCommentsWithReply(prevComments));
      } else {
        // For top-level comments, just add to the start of the list
        setComments(prevComments => [response.data, ...prevComments]);
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
    } finally {
      setLoading(false);
    }
  };

  const removeCommentFromList = (comments, commentId) => {
    return comments.filter(comment => {
      // If this comment matches the ID, remove it
      if (comment.id === commentId) {
        return false;
      }

      // If this comment has replies, recursively filter them
      if (comment.replies?.length > 0) {
        comment.replies = removeCommentFromList(comment.replies, commentId);
      }

      return true;
    });
  };

  const handleDeleteComment = async (commentId) => {
    try {
      await documentService.deleteComment(commentId);

      // Update state with the filtered comments
      setComments(prevComments => removeCommentFromList(prevComments, commentId));

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
      await documentService.updateComment(commentId, { content });
      fetchComments(); // Refresh comments after edit
      return true;
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.comments.editError"),
        variant: "destructive"
      });
      throw error;
    }
  };

  useEffect(() => {
    // Reset to first page when documentId changes
    setCurrentPage(0);
    fetchComments();
  }, [documentId]);

  useEffect(() => {
    if (documentId) {
      fetchComments();
    }
  }, [currentPage]);

  return (
    <div className="space-y-6">
      <div className="space-y-4">
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
                onDelete={handleDeleteComment}
                onReply={setReplyTo}
                onEdit={handleEditComment}
              />
            ))
          )}
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