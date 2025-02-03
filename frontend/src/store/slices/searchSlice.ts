import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";

import { searchService } from "@/services/search.service";
import { RootState } from "@/store";
import { DocumentInformation } from "@/types/document";

interface SearchState {
  // Search inputs
  searchTerm: string;
  selectedSort: string;
  selectedMajor: string;
  selectedLevel: string;
  selectedCategory: string;
  selectedTags: string[];

  // Pagination
  currentPage: number;
  pageSize: number;
  totalPages: number;
  totalElements: number;

  // Search suggestions
  suggestions: string[];
  loadingSuggestions: boolean;

  // Search results
  documents: DocumentInformation[];
  loading: boolean;
  error: string | null;

  // Advanced filters
  advancedFilters: {
    dateRange?: { start: string; end: string };
    fileTypes?: string[];
    confidence?: number;
  };
}

const initialState: SearchState = {
  searchTerm: "",
  selectedSort: "created_at,desc",
  selectedMajor: "all",
  selectedLevel: "all",
  selectedCategory: "all",
  selectedTags: [],
  currentPage: 0,
  pageSize: 10,
  totalPages: 0,
  totalElements: 0,
  suggestions: [],
  loadingSuggestions: false,
  documents: [],
  loading: false,
  error: null,
  advancedFilters: {}
};

// Async thunk for search suggestions
export const fetchSuggestions = createAsyncThunk(
  "search/fetchSuggestions",
  async (query: string, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const { selectedMajor, selectedLevel, selectedCategory, selectedTags } = state.search;

      const filters = {
        major: selectedMajor === "all" ? undefined : selectedMajor,
        level: selectedLevel === "all" ? undefined : selectedLevel,
        category: selectedCategory === "all" ? undefined : selectedCategory,
        tags: selectedTags.length > 0 ? selectedTags : undefined
      };

      const response = await searchService.suggestions(query, filters);
      return response.data;
    } catch (error) {
      return rejectWithValue(error.message);
    }
  }
);

// Async thunk for fetching documents
export const fetchDocuments = createAsyncThunk(
  "search/fetchDocuments",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const {
        searchTerm,
        selectedMajor,
        selectedLevel,
        selectedCategory,
        selectedTags,
        selectedSort,
        currentPage,
        pageSize,
        advancedFilters
      } = state.search;

      const selectSortParts = selectedSort.split(",");

      const filters = {
        search: searchTerm,
        major: selectedMajor === "all" ? undefined : selectedMajor,
        level: selectedLevel === "all" ? undefined : selectedLevel,
        category: selectedCategory === "all" ? undefined : selectedCategory,
        tags: selectedTags.length > 0 ? selectedTags : undefined,
        sortField: selectSortParts.length > 0 ? selectSortParts[0] : undefined,
        sortDirection: selectSortParts.length > 1 ? selectSortParts[1] : undefined,
        ...advancedFilters
      };

      const response = await searchService.searchDocuments(filters, currentPage, pageSize);
      return response.data;
    } catch (error) {
      return rejectWithValue(error.message);
    }
  }
);

const searchSlice = createSlice({
  name: "search",
  initialState,
  reducers: {
    setSearchTerm: (state, action: PayloadAction<string>) => {
      state.searchTerm = action.payload;
      state.currentPage = 0; // Reset to first page when search term changes
    },
    setSort: (state, action: PayloadAction<string>) => {
      state.selectedSort = action.payload;
      state.currentPage = 0;
    },
    setMajor: (state, action: PayloadAction<string>) => {
      state.selectedMajor = action.payload;
      state.currentPage = 0;
    },
    setLevel: (state, action: PayloadAction<string>) => {
      state.selectedLevel = action.payload;
      state.currentPage = 0;
    },
    setCategory: (state, action: PayloadAction<string>) => {
      state.selectedCategory = action.payload;
      state.currentPage = 0;
    },
    setTags: (state, action: PayloadAction<string[]>) => {
      state.selectedTags = action.payload;
      state.currentPage = 0;
    },
    setPage: (state, action: PayloadAction<number>) => {
      state.currentPage = action.payload;
    },
    setPageSize: (state, action: PayloadAction<number>) => {
      state.pageSize = action.payload;
      state.currentPage = 0; // Reset to first page when page size changes
    },
    setAdvancedFilters: (state, action: PayloadAction<Partial<SearchState["advancedFilters"]>>) => {
      state.advancedFilters = {
        ...state.advancedFilters,
        ...action.payload
      };
      state.currentPage = 0;
    },
    resetFilters: (state) => {
      return {
        ...initialState,
        documents: state.documents, // Preserve current documents until new search
        suggestions: state.suggestions // Preserve suggestions
      };
    }
  },
  extraReducers: (builder) => {
    builder
      // Handle fetchSuggestions
      .addCase(fetchSuggestions.pending, (state) => {
        state.loadingSuggestions = true;
      })
      .addCase(fetchSuggestions.fulfilled, (state, action) => {
        state.loadingSuggestions = false;
        state.suggestions = action.payload;
      })
      .addCase(fetchSuggestions.rejected, (state) => {
        state.loadingSuggestions = false;
        state.suggestions = [];
      })
      // Handle fetchDocuments
      .addCase(fetchDocuments.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchDocuments.fulfilled, (state, action) => {
        state.loading = false;
        state.documents = action.payload.content;
        state.totalPages = action.payload.totalPages;
        state.totalElements = action.payload.totalElements;
      })
      .addCase(fetchDocuments.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
        state.documents = [];
        state.totalPages = 0;
        state.totalElements = 0;
      });
  }
});

export const {
  setSearchTerm,
  setSort,
  setMajor,
  setLevel,
  setCategory,
  setTags,
  setPage,
  setPageSize,
  setAdvancedFilters,
  resetFilters
} = searchSlice.actions;

// Selectors
export const selectSearchState = (state: RootState) => state.search;
export const selectSearchResults = (state: RootState) => state.search.documents;
export const selectSearchLoading = (state: RootState) => state.search.loading;
export const selectSuggestions = (state: RootState) => state.search.suggestions;
export const selectPagination = (state: RootState) => ({
  currentPage: state.search.currentPage,
  pageSize: state.search.pageSize,
  totalPages: state.search.totalPages,
  totalElements: state.search.totalElements
});

export default searchSlice.reducer;