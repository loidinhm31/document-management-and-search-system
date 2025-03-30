import React, { useCallback } from "react";
import { v4 as uuidv4 } from "uuid";

import { useAppDispatch, useAppSelector } from "@/store/hook";
import {
  addProcessingItem,
  removeProcessingItem,
  selectProcessingItems,
  updateProcessingItem,
} from "@/store/slices/processing-slice";
import { DocumentStatus, ProcessingItem } from "@/types/document";

interface ProcessingContextType {
  items: ProcessingItem[];
  addProcessingItem: (documentId: string, filename: string) => void;
  removeProcessingItem: (id: string) => void;
  updateProcessingItem: (id: string, status: ProcessingItem["status"], error?: string) => void;
}

const ProcessingContext = React.createContext<ProcessingContextType | undefined>(undefined);

export function ProcessingProvider({ children }: { children: React.ReactNode }) {
  const dispatch = useAppDispatch();
  const items = useAppSelector(selectProcessingItems);

  const handleAddProcessingItem = useCallback(
    (documentId: string, filename: string) => {
      const item = {
        id: uuidv4(),
        documentId,
        filename,
        status: DocumentStatus.PENDING,
        addedAt: new Date().getTime(),
      };
      dispatch(addProcessingItem(item));
    },
    [dispatch],
  );

  const handleRemoveProcessingItem = useCallback(
    (id: string) => {
      dispatch(removeProcessingItem(id));
    },
    [dispatch],
  );

  const handleUpdateProcessingItem = useCallback(
    async (documentId: string, status: DocumentStatus, error?: string) => {
      dispatch(updateProcessingItem({ documentId, status, error }));
    },
    [dispatch],
  );

  const contextValue = React.useMemo<ProcessingContextType>(
    () => ({
      items,
      addProcessingItem: handleAddProcessingItem,
      removeProcessingItem: handleRemoveProcessingItem,
      updateProcessingItem: handleUpdateProcessingItem,
    }),
    [items, handleAddProcessingItem, handleRemoveProcessingItem, handleUpdateProcessingItem],
  );

  return <ProcessingContext.Provider value={contextValue}>{children}</ProcessingContext.Provider>;
}

export const useProcessing = () => {
  const context = React.useContext(ProcessingContext);
  if (!context) {
    throw new Error("useProcessing must be used within a ProcessingProvider");
  }
  return context;
};
