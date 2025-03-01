import { Loader2 } from "lucide-react";
import React, { useEffect, useRef, useState } from "react";

import { cn } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { DocumentInformation, DocumentStatus, DocumentType } from "@/types/document";

// IndexedDB configuration
const DB_NAME = "thumbnailCache";
const STORE_NAME = "thumbnails";
const METADATA_STORE = "metadata";
const DB_VERSION = 1;
const MAX_CACHE_AGE = 7 * 24 * 60 * 60 * 1000; // 7 days in milliseconds
const MAX_CACHE_SIZE = 50 * 1024 * 1024; // 50MB in bytes

interface CacheMetadata {
  key: string;
  timestamp: number;
  size: number;
  documentId: string;
  version: number;
}

// Initialize IndexedDB with metadata store
const initDB = (): Promise<IDBDatabase> => {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;

      // Create or ensure thumbnail store exists
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }

      // Create or ensure metadata store exists
      if (!db.objectStoreNames.contains(METADATA_STORE)) {
        const metadataStore = db.createObjectStore(METADATA_STORE, { keyPath: "key" });
        metadataStore.createIndex("timestamp", "timestamp");
        metadataStore.createIndex("documentId", "documentId");
      }
    };
  });
};

// Clean up old cache entries
const cleanupCache = async () => {
  try {
    const db = await initDB();
    const transaction = db.transaction([STORE_NAME, METADATA_STORE], "readwrite");
    const store = transaction.objectStore(STORE_NAME);
    const metadataStore = transaction.objectStore(METADATA_STORE);
    const timestampIndex = metadataStore.index("timestamp");

    return new Promise<void>((resolve, reject) => {
      // Get all metadata entries sorted by timestamp
      const request = timestampIndex.openCursor();
      let totalSize = 0;
      const deletionQueue: string[] = [];

      request.onsuccess = (event) => {
        const cursor = (event.target as IDBRequest).result;
        if (cursor) {
          const metadata = cursor.value as CacheMetadata;
          const age = Date.now() - metadata.timestamp;

          // Mark for deletion if too old or if we're over size limit
          if (age > MAX_CACHE_AGE || totalSize > MAX_CACHE_SIZE) {
            deletionQueue.push(metadata.key);
          } else {
            totalSize += metadata.size;
          }
          cursor.continue();
        } else {
          // Delete all marked entries
          Promise.all(
            deletionQueue.map((key) => {
              store.delete(key);
              metadataStore.delete(key);
            }),
          )
            .then(() => resolve())
            .catch(reject);
        }
      };
      request.onerror = () => reject(request.error);
    });
  } catch (error) {
    console.info("Error cleaning up cache:", error);
  }
};

// Clean up old versions of a document's thumbnails
const cleanupOldVersions = async (documentId: string, currentVersion: number) => {
  try {
    const db = await initDB();
    const transaction = db.transaction([STORE_NAME, METADATA_STORE], "readwrite");
    const store = transaction.objectStore(STORE_NAME);
    const metadataStore = transaction.objectStore(METADATA_STORE);
    const documentIndex = metadataStore.index("documentId");

    return new Promise<void>((resolve, reject) => {
      const request = documentIndex.openCursor(IDBKeyRange.only(documentId));

      request.onsuccess = (event) => {
        const cursor = (event.target as IDBRequest).result;
        if (cursor) {
          const metadata = cursor.value as CacheMetadata;
          if (metadata.version < currentVersion) {
            // Delete old version
            store.delete(metadata.key);
            metadataStore.delete(metadata.key);
          }
          cursor.continue();
        } else {
          resolve();
        }
      };
      request.onerror = () => reject(request.error);
    });
  } catch (error) {
    console.info("Error cleaning up old versions:", error);
  }
};

// Get thumbnail from cache
const getCachedThumbnail = async (cacheKey: string): Promise<Blob | null> => {
  try {
    const db = await initDB();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, "readonly");
      const store = transaction.objectStore(STORE_NAME);
      const request = store.get(cacheKey);

      request.onsuccess = () => resolve(request.result || null);
      request.onerror = () => reject(request.error);
    });
  } catch (error) {
    console.info("Error accessing cache:", error);
    return null;
  }
};

// Save thumbnail to cache with metadata
const cacheThumbnail = async (cacheKey: string, blob: Blob, documentId: string, version: number): Promise<void> => {
  try {
    const db = await initDB();
    const transaction = db.transaction([STORE_NAME, METADATA_STORE], "readwrite");
    const store = transaction.objectStore(STORE_NAME);
    const metadataStore = transaction.objectStore(METADATA_STORE);

    return new Promise((resolve, reject) => {
      // Save the thumbnail
      store.put(blob, cacheKey);

      // Save the metadata
      const metadata: CacheMetadata = {
        key: cacheKey,
        timestamp: Date.now(),
        size: blob.size,
        documentId,
        version,
      };
      metadataStore.put(metadata);

      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
  } catch (error) {
    console.info("Error caching thumbnail:", error);
  }
};

interface LazyThumbnailProps {
  documentInformation: DocumentInformation;
}

export const LazyThumbnail = React.memo(({ documentInformation }: LazyThumbnailProps) => {
  const [isVisible, setIsVisible] = useState(false);
  const [thumbnailUrl, setThumbnailUrl] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  // Create a unique cache key that includes both document ID and update timestamp
  const cacheKey = `${documentInformation.id}_${documentInformation.currentVersion}_${new Date(documentInformation.updatedAt).getTime()}`;

  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: "50px" },
    );

    if (ref.current) {
      observer.observe(ref.current);
    }

    return () => observer.disconnect();
  }, []);

  // Cleanup old thumbnail URL when document information changes
  useEffect(() => {
    if (thumbnailUrl) {
      URL.revokeObjectURL(thumbnailUrl);
      setThumbnailUrl("");
    }
  }, [cacheKey]);

  useEffect(() => {
    if (!isVisible) return;

    const loadThumbnail = async () => {
      try {
        setLoading(true);
        setError(false);

        // Clean up old versions of this document's thumbnails
        await cleanupOldVersions(documentInformation.id, documentInformation.currentVersion);

        // Run general cache cleanup
        await cleanupCache();

        // Try to get thumbnail from cache
        const cachedThumbnail = await getCachedThumbnail(cacheKey);

        if (cachedThumbnail) {
          // If found in cache, use it
          setThumbnailUrl(URL.createObjectURL(cachedThumbnail));
        } else {
          // If not in cache, fetch from server
          const response = await documentService.getDocumentThumbnail(
            documentInformation.id,
            `v=${documentInformation.currentVersion}_${new Date(documentInformation.updatedAt).getTime()}`,
          );

          const blob = new Blob([response.data]);

          // Cache the new thumbnail with metadata
          await cacheThumbnail(cacheKey, blob, documentInformation.id, documentInformation.currentVersion);

          // Create and set URL for display
          setThumbnailUrl(URL.createObjectURL(blob));
        }
      } catch (error) {
        console.info("Error loading thumbnail:", error);
        setError(true);
      } finally {
        setLoading(false);
      }
    };

    loadThumbnail();
  }, [isVisible, cacheKey]);

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
            className={cn(
              "w-full h-full rounded-md",
              documentInformation.documentType === DocumentType.PDF &&
                documentInformation.status === DocumentStatus.COMPLETED
                ? "object-cover"
                : "object-contain",
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
