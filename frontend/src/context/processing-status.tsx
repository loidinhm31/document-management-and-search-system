import React, { useEffect } from "react";
import { AlertCircle, CheckCircle2, Loader2, Timer, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { documentService } from "@/services/document.service";
import { useProcessing } from "@/context/processing-provider";

export function ProcessingStatus() {
  const { t } = useTranslation();
  const { items, updateProcessingItem, removeProcessingItem } = useProcessing();

  // Poll for status updates
  useEffect(() => {
    const checkStatuses = async () => {
      const pendingItems = items.filter(
        item => item.status === "PENDING" || item.status === "PROCESSING"
      );

      for (const item of pendingItems) {
        try {
          const response = await documentService.getDocumentDetails(item.documentId);
          updateProcessingItem(item.documentId, response.data.status, response.data.processingError);
        } catch (error) {
          console.error("Failed to check status:", error);
        }
      }
    };

    const interval = setInterval(checkStatuses, 5000);
    return () => clearInterval(interval);
  }, [items, updateProcessingItem]);

  // Auto-remove completed items after 5 seconds
  useEffect(() => {
    const completedItems = items.filter(
      item => item.status === "COMPLETED" || item.status === "FAILED"
    );

    completedItems.forEach(item => {
      const timer = setTimeout(() => {
        removeProcessingItem(item.id);
      }, 5000);

      return () => clearTimeout(timer);
    });
  }, [items, removeProcessingItem]);

  if (items.length === 0) return null;

  return (
    <div className="fixed right-4 top-4 z-50 flex flex-col gap-2 w-80">
      {items.map((item) => (
        <div
          key={item.id}
          className={`
            rounded-lg border bg-card p-4 text-card-foreground shadow-lg
            transform transition-all duration-300
            ${item.status === "COMPLETED" ? "border-green-500" : ""}
            ${item.status === "FAILED" ? "border-red-500" : ""}
          `}
        >
          <div className="flex items-start gap-3">
            {/* Status Icon */}
            <div className="mt-1">
              {item.status === "PENDING" && <Timer className="h-5 w-5 text-muted-foreground" />}
              {item.status === "PROCESSING" && <Loader2 className="h-5 w-5 animate-spin text-primary" />}
              {item.status === "COMPLETED" && <CheckCircle2 className="h-5 w-5 text-green-500" />}
              {item.status === "FAILED" && <AlertCircle className="h-5 w-5 text-destructive" />}
            </div>

            {/* Content */}
            <div className="flex-1 space-y-1">
              <p className="text-sm font-medium leading-none">
                {item.filename}
              </p>
              <p className="text-sm text-muted-foreground">
                {t(`document.upload.processing.status.${item.status.toLowerCase()}`)}
              </p>
              {item.error && (
                <p className="text-sm text-destructive">{item.error}</p>
              )}
            </div>

            {/* Close button */}
            <button
              onClick={() => removeProcessingItem(item.id)}
              className="rounded-md p-1 hover:bg-muted"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}