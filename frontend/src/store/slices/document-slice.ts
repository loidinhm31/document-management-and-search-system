import { createSlice, PayloadAction } from "@reduxjs/toolkit";

import { RootState } from "@/store";
import { DocumentInformation } from "@/types/document";

interface DocumentState {
  currentDocument: DocumentInformation | null;
}

const initialState: DocumentState = {
  currentDocument: null
};

const documentSlice = createSlice({
  name: "document",
  initialState,
  reducers: {
    setCurrentDocument: (state, action: PayloadAction<DocumentInformation | null>) => {
      state.currentDocument = action.payload;
    }
  }
});

export const { setCurrentDocument } = documentSlice.actions;
export const selectCurrentDocument = (state: RootState) => state.document.currentDocument;
export default documentSlice.reducer;