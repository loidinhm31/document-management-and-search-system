import React, { createContext, useCallback, useContext, useRef, useState } from "react";
import { v4 as uuidv4 } from "uuid";

interface ProcessingItem {
  id: string;
  documentId: string;
  filename: string;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  error?: string;
  addedAt: Date;
}

interface ProcessingContextType {
  items: ProcessingItem[];
  addProcessingItem: (documentId: string, filename: string) => void;
  removeProcessingItem: (id: string) => void;
  updateProcessingItem: (id: string, status: ProcessingItem["status"], error?: string) => void;
}

const ProcessingContext = createContext<ProcessingContextType | undefined>(undefined);

export function ProcessingProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ProcessingItem[]>([]);
  const itemsRef = useRef<ProcessingItem[]>([]);

  // Keep ref in sync with state
  itemsRef.current = items;

  const addProcessingItem = useCallback((documentId: string, filename: string) => {
    const item: ProcessingItem = {
      id: uuidv4(),
      documentId,
      filename,
      status: "PENDING",
      addedAt: new Date()
    };

    setItems(current => [...current, item]);
    return item.id;
  }, []);

  const removeProcessingItem = useCallback((id: string) => {
    setItems(current => current.filter(item => item.id !== id));
  }, []);

  const updateProcessingItem = useCallback((documentId: string, status: ProcessingItem["status"], error?: string) => {
    setItems(current =>
      current.map(item =>
        item.documentId === documentId
          ? { ...item, status, error, updatedAt: new Date() }
          : item
      )
    );
  }, []);

  return (
    <ProcessingContext.Provider
      value={{
        items,
        addProcessingItem,
        removeProcessingItem,
        updateProcessingItem
      }}
    >
      {children}
    </ProcessingContext.Provider>
  );
}

export const useProcessing = () => {
  const context = useContext(ProcessingContext);
  if (!context) {
    throw new Error("useProcessing must be used within a ProcessingProvider");
  }
  return context;
};

// Optional: Hook to monitor a specific document
export const useDocumentProcessing = (documentId: string) => {
  const { items } = useProcessing();
  return items.find(item => item.documentId === documentId);
};