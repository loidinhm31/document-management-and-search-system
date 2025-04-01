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
import { Comment, CommentCreateData, CommentEditData, PaginationParams } from "@/types/comment";

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

interface CommentSectionProps {
  documentId: string;
}

// Number of comments to load per page
const COMMENTS_PER_PAGE = 10;

export const CommentSection: React.FC<CommentSectionProps> = ({ documentId }) => {
  const { t } = useTranslation();
  const [comments, setComments] = useState<Comment[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [commentText, setCommentText] = useState<string>("");
  const [hasMore, setHasMore] = useState<boolean>(false);
  const [page, setPage] = useState<number>(0);
  const [replyTo, setReplyTo] = useState<Comment | null>(null);
  const { currentUser } = useAuth();
  const { toast } = useToast();

  const [deleteDialogOpen, setDeleteDialogOpen] = useState<boolean>(false);
  const [deleteInProgress, setDeleteInProgress] = useState<boolean>(false);
  const [commentToDelete, setCommentToDelete] = useState<number | null>(null);

  // Refs
  const commentsRef = useRef<Comment[]>(comments);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);

  // Update ref when comments change
  useEffect(() => {
    commentsRef.current = comments;
  }, [comments]);

  // Transform flat comments into a hierarchical structure
  const buildCommentTree = useCallback((flatComments: Comment[], existingComments: Comment[] = []): Comment[] => {
    // Create a map for quick access to existing comments by id
    const existingCommentMap = new Map<number, Comment>();

    // Helper function to populate the map with existing comments and their replies
    const populateCommentMap = (comments: Comment[]) => {
      comments.forEach((comment) => {
        existingCommentMap.set(comment.id, comment);
        if (comment.replies && comment.replies.length > 0) {
          populateCommentMap(comment.replies);
        }
      });
    };

    // Populate map with existing comments
    populateCommentMap(existingComments);

    // Create a map for new comments
    const newCommentMap = new Map<number, Comment>();
    const rootComments: Comment[] = [...existingComments]; // Start with existing comments

    // First pass: add all new comments to the map
    flatComments.forEach((comment) => {
      // Skip if comment already exists in the tree
      if (existingCommentMap.has(comment.id)) {
        return;
      }

      // Initialize replies array if not present
      const commentWithReplies = {
        ...comment,
        replies: [] as Comment[],
      };
      newCommentMap.set(comment.id, commentWithReplies);
    });

    // Second pass: build the tree structure for new comments
    for (const [id, comment] of newCommentMap.entries()) {
      if (comment.parentId) {
        // This is a reply - check if parent exists in either existing or new comments
        const parentComment =
          existingCommentMap.get(Number(comment.parentId)) || newCommentMap.get(Number(comment.parentId));

        if (parentComment) {
          // Parent found, add as reply
          parentComment.replies = parentComment.replies || [];
          parentComment.replies.push(comment);
        } else {
          // Parent not found, add as root
          rootComments.push(comment);
        }
      } else {
        // This is a root comment
        rootComments.push(comment);
      }
    }

    // Sort root comments by created date (newest first)
    return rootComments.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, []);

  const fetchComments = useCallback(
    async (pageNum = 0, append = false) => {
      if (!documentId) return;

      if (pageNum === 0) {
        setLoading(true);
      } else {
        setLoadingMore(true);
      }

      try {
        const response = await documentService.getDocumentComments(documentId, {
          page: pageNum,
          size: COMMENTS_PER_PAGE,
        } as PaginationParams);

        // Save the current scroll position before adding new comments
        const scrollPosition = scrollContainerRef.current?.scrollTop || 0;

        if (append && pageNum > 0) {
          // Use the current comments as the existing tree when appending
          setComments((prevComments) => {
            // Integrate new comments with the existing tree
            return buildCommentTree(response.data.content, prevComments);
          });

          // After state update, scroll to show a bit of the new content
          setTimeout(() => {
            if (scrollContainerRef.current) {
              // Scroll to show just a bit of the new content (e.g., 100px into the new content)
              scrollContainerRef.current.scrollTop = scrollPosition + 80;
            }
          }, 200);
        } else {
          // For initial load, build a fresh tree
          setComments(buildCommentTree(response.data.content));
        }

        // Update pagination state
        setPage(pageNum);
        setHasMore(pageNum < response.data.totalPages - 1);
      } catch (_error) {
        toast({
          title: t("common.error"),
          description: t("document.comments.fetchError"),
          variant: "destructive",
        });
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [documentId, toast, t, buildCommentTree],
  );

  const loadMoreComments = useCallback(() => {
    if (hasMore && !loadingMore) {
      fetchComments(page + 1, true);
    }
  }, [fetchComments, hasMore, loadingMore, page]);

  // Initial load and document change handler
  useEffect(() => {
    fetchComments(0);
  }, [documentId, fetchComments]);

  const handleSubmitComment = async () => {
    const commentTrim = commentText.trim();
    if (!commentTrim || loading) return;

    setLoading(true);
    try {
      const payload: CommentCreateData = {
        content: commentTrim,
        parentId: replyTo?.id || null,
      };

      const response = await documentService.createComment(documentId, payload);
      const newComment = response.data;

      if (replyTo) {
        // Update state to add the new reply
        setComments((prevComments) => {
          // Clone the current comment tree to avoid direct state mutations
          const updatedComments = [...prevComments];

          // Helper function to recursively find and update the parent comment
          const addReplyToComment = (comments: Comment[]): boolean => {
            for (let i = 0; i < comments.length; i++) {
              if (comments[i].id === replyTo.id) {
                // Found the parent, add the reply
                const replyWithValidDates = {
                  ...newComment,
                  createdAt: newComment.createdAt || new Date().toISOString(),
                  updatedAt: newComment.updatedAt || new Date().toISOString(),
                };
                comments[i].replies = [...(comments[i].replies || []), replyWithValidDates];
                return true;
              }

              // Check in replies if this comment has any
              if (comments[i].replies && comments[i].replies.length > 0) {
                if (addReplyToComment(comments[i].replies)) {
                  return true;
                }
              }
            }
            return false;
          };

          // Try to add the reply to the parent
          if (!addReplyToComment(updatedComments)) {
            // If parent not found (rare case), refresh comments from backend
            fetchComments(0);
          }

          return updatedComments;
        });
      } else {
        // Ensure the new comment has proper date fields before adding to state
        const commentWithValidDates = {
          ...newComment,
          createdAt: newComment.createdAt || new Date().toISOString(),
          updatedAt: newComment.updatedAt || new Date().toISOString(),
          replies: [] as Comment[],
        };

        // Add new top-level comment at the beginning of the list
        setComments((prevComments) => [commentWithValidDates, ...prevComments]);

        // Scroll to top after adding a new comment
        if (scrollContainerRef.current) {
          scrollContainerRef.current.scrollTop = 0;
        }
      }

      // Clear form
      setCommentText("");
      setReplyTo(null);

      toast({
        title: t("common.success"),
        description: t("document.comments.createSuccess"),
        variant: "success",
      });
    } catch (error: any) {
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

  // Recursively remove comment from nested structure
  const removeCommentFromState = useCallback((comments: Comment[], commentId: number): Comment[] => {
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
  }, []);

  // Recursively update comment in nested structure
  const updateCommentInState = useCallback(
    (comments: Comment[], commentId: number, updatedFields: Partial<Comment>): Comment[] => {
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
    },
    [],
  );

  // Handle opening the delete dialog
  const handleDeleteClick = useCallback((commentId: number) => {
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
  }, [commentToDelete, documentId, replyTo, toast, t, removeCommentFromState]);

  // Handle dialog close
  const handleDialogClose = useCallback((open: boolean) => {
    if (!open) {
      setDeleteDialogOpen(false);
      // Clear the comment ID after dialog closes
      setTimeout(() => {
        setCommentToDelete(null);
      }, 100);
    }
  }, []);

  const handleEditComment = async (commentId: number, { content }: CommentEditData) => {
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
  console.log("c", currentUser);

  return (
    <div className="space-y-6" key={documentId}>
      {/* Comment input section */}
      <div className="space-y-4">
        {!currentUser?.roles.includes("ROLE_ADMIN") && (
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
        )}

        {/* Comments list */}
        {loading && page === 0 ? (
          <>
            <CommentSkeleton />
            <CommentSkeleton />
            <CommentSkeleton />
          </>
        ) : (
          <div ref={scrollContainerRef} className="space-y-6 max-h-[700px] overflow-y-auto pr-2">
            {comments.length === 0 && !loading ? (
              <p className="flex justify-center py-4">{t("document.comments.empty")}</p>
            ) : (
              <div className="space-y-6">
                {comments.map((comment) => (
                  <CommentItem
                    key={comment.id}
                    comment={comment}
                    currentUser={currentUser}
                    onDelete={handleDeleteClick}
                    onReply={setReplyTo}
                    onEdit={handleEditComment}
                    documentId={documentId}
                  />
                ))}

                {/* Load more section */}
                <div ref={loadMoreRef}>
                  {hasMore && (
                    <div className="flex justify-center pt-4">
                      <Button variant="outline" onClick={loadMoreComments} disabled={loadingMore}>
                        {loadingMore ? t("document.comments.loading") : t("document.comments.loadMore")}
                      </Button>
                    </div>
                  )}

                  {/* Loading indicator */}
                  {loadingMore && (
                    <div className="pt-4">
                      <CommentSkeleton />
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Delete confirmation dialog */}
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
