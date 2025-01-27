import { Loader2 } from "lucide-react";
import React, { useEffect, useRef, useState } from "react";

import { documentService } from "@/services/document.service";
import { DocumentInformation, DocumentType } from "@/types/document";
import { cn } from "@/lib/utils";


interface LazyThumbnailProps {
  documentInformation: DocumentInformation;
}

export const LazyThumbnail = React.memo(({ documentInformation }: LazyThumbnailProps) => {
  const [isVisible, setIsVisible] = useState(false);
  const [thumbnailUrl, setThumbnailUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: "50px" }
    );

    if (ref.current) {
      observer.observe(ref.current);
    }

    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!isVisible) return;

    const loadThumbnail = async () => {
      try {
        const cacheKeyUrl = `document/api/v1/documents/thumbnails/${documentInformation.id}`;

        // Try to get from cache first
        const cache = await caches.open("document-thumbnails");
        const cachedResponse = await cache.match(cacheKeyUrl);

        if (cachedResponse) {
          const blob = await cachedResponse.blob();
          setThumbnailUrl(URL.createObjectURL(blob));
          setLoading(false);
          return;
        }

        // If not in cache, fetch from server
        const response = await documentService.getDocumentThumbnail(documentInformation.id);

        // Convert Axios headers to Headers object
        const headers = new Headers();
        Object.entries(response.headers).forEach(([key, value]) => {
          if (value) headers.append(key, value.toString());
        });

        const blob = new Blob([response.data]);

        // Create a new Response with the blob and headers
        const responseToCache = new Response(blob, {
          status: response.status,
          statusText: response.statusText,
          headers: headers
        });

        // Cache the response
        await cache.put(cacheKeyUrl, responseToCache);

        setThumbnailUrl(URL.createObjectURL(blob));
      } catch (error) {
        console.error("Error loading thumbnail:", error);
        setError(true);
      } finally {
        setLoading(false);
      }
    };

    loadThumbnail();
  }, [isVisible, documentInformation.id]);

  // Cleanup URLs on unmount
  useEffect(() => {
    return () => {
      if (thumbnailUrl) {
        URL.revokeObjectURL(thumbnailUrl);
      }
    };
  }, [thumbnailUrl]);

  return (
    <div ref={ref} className="w-full h-full">
      {isVisible ? (
        loading ? (
          <div className="flex items-center justify-center h-full bg-muted">
            <Loader2 className="h-8 w-8 animate-spin" />
          </div>
        ) : error ? (
          <div className="flex items-center justify-center h-full bg-muted">
            <span className="text-sm text-muted-foreground">Failed to load preview</span>
          </div>
        ) : (
          <img
            src={thumbnailUrl}
            alt={documentInformation.filename}
            className={cn("w-full h-full rounded-md",
              documentInformation.documentType === DocumentType.PDF ? "object-cover" : "object-contain",
            )}
            loading="lazy"
          />
        )
      ) : (
        <div className="w-full h-full bg-muted animate-pulse rounded-md" />
      )}
    </div>
  );
});