import { configureStore } from "@reduxjs/toolkit";
import { createLogger } from "redux-logger";

import documentReducer from "@/store/slices/document-slice";
import masterDataReducer from "@/store/slices/master-data-slice";
import processingReducer from "@/store/slices/processing-slice";
import searchReducer from "@/store/slices/search-slice";

const logger = createLogger({
  collapsed: true, // Collapse logs by default
  duration: true, // Print the duration of each action
  timestamp: true,
  predicate: (_getState, _action) => process.env.NODE_ENV === "development"
});

export const store = configureStore({
  reducer: {
    search: searchReducer,
    masterData: masterDataReducer,
    processing: processingReducer,
    document: documentReducer
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: {
        // Ignore these action types
        ignoredActions: ["your-non-serializable-action-type"]
      }
    }).concat(logger),
  devTools: process.env.NODE_ENV !== "production"
});

// Infer the `RootState` and `AppDispatch` types from the store itself
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;