import { createSlice, PayloadAction } from "@reduxjs/toolkit";

import { RootState } from "@/store";
import { DocumentStatus } from "@/types/document";

interface ProcessingItem {
  id: string;
  documentId: string;
  filename: string;
  status: DocumentStatus;
  error?: string;
  addedAt: number;
}

export interface ProcessingState {
  items: ProcessingItem[];
}

const initialState = {
  items: [] as ProcessingItem[]
};

const processingSlice = createSlice({
  name: "processing",
  initialState,
  reducers: {
    addProcessingItem: (state, action: PayloadAction<ProcessingItem>) => {
      state.items.push(action.payload);
    },
    removeProcessingItem: (state, action: PayloadAction<string>) => {
      state.items = state.items.filter(item => item.id !== action.payload);
    },
    updateProcessingItem: (
      state,
      action: PayloadAction<{
        documentId: string;
        status: DocumentStatus;
        error?: string;
      }>
    ) => {
      const item = state.items.find(i => i.documentId === action.payload.documentId);
      if (item) {
        item.status = action.payload.status;
        if (action.payload.error) {
          item.error = action.payload.error;
        }
      }
    }
  }
});

export const { addProcessingItem, removeProcessingItem, updateProcessingItem } = processingSlice.actions;

export const selectProcessingItems = (state: RootState) => state.processing.items;
export const selectProcessingItemByDocumentId = (documentId: string) =>
  (state: RootState) => state.processing.items.find(item => item.documentId === documentId);

export default processingSlice.reducer;