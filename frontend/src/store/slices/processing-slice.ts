import { createSlice, PayloadAction } from "@reduxjs/toolkit";

import { RootState } from "@/store";
import { DocumentStatus, ProcessingItem } from "@/types/document";

interface ProcessingState {
  items: ProcessingItem[];
  visible: boolean;
}

const initialState: ProcessingState = {
  items: [],
  visible: true
};

const processingSlice = createSlice({
  name: "processing",
  initialState,
  reducers: {
    addProcessingItem: (state, action: PayloadAction<ProcessingItem>) => {
      // Check if item already exists
      const existingItem = state.items.find((item) => item.documentId === action.payload.documentId);
      if (existingItem) {
        // Update existing item instead of adding a duplicate
        existingItem.status = action.payload.status;
        existingItem.addedAt = action.payload.addedAt;
        if (action.payload.error) {
          existingItem.error = action.payload.error;
        }
      } else {
        state.items.push(action.payload);
      }
      // Ensure visibility is true when adding items
      state.visible = true;
    },
    removeProcessingItem: (state, action: PayloadAction<string>) => {
      state.items = state.items.filter((item) => item.id !== action.payload);
    },
    updateProcessingItem: (
      state,
      action: PayloadAction<{
        documentId: string;
        status: DocumentStatus;
        error?: string;
      }>,
    ) => {
      const item = state.items.find((i) => i.documentId === action.payload.documentId);
      if (item) {
        item.status = action.payload.status;
        if (action.payload.error) {
          item.error = action.payload.error;
        }
      }
    },
    // Toggle visibility
    setProcessingVisibility: (state, action: PayloadAction<boolean>) => {
      state.visible = action.payload;
    },
  },
});

export const { addProcessingItem, removeProcessingItem, updateProcessingItem, setProcessingVisibility } =
  processingSlice.actions;

export const selectProcessingItems = (state: RootState) => state.processing.items;
export const selectProcessingVisibility = (state: RootState) => state.processing.visible;
export const selectProcessingItemByDocumentId = (documentId: string) => (state: RootState) =>
  state.processing.items.find((item) => item.documentId === documentId);


export default processingSlice.reducer;
