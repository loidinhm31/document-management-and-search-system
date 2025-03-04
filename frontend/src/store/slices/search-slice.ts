import { createAsyncThunk, createSlice, PayloadAction } from "@reduxjs/toolkit";

import { documentService } from "@/services/document.service";
import { searchService } from "@/services/search.service";
import { RootState } from "@/store";
import { DocumentInformation } from "@/types/document";

interface SearchState {
  // Search inputs
  searchTerm: string;
  selectedSort: string;
  selectedMajors: string[];
  selectedCourseCodes: string[];
  selectedLevel: string;
  selectedCategories: string[];
  selectedTags: string[];
  favoriteOnly: boolean;

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

  isSearchMode: boolean;
}

const initialState: SearchState = {
  isSearchMode: false,
  searchTerm: "",
  selectedSort: "created_at,desc",
  selectedMajors: [],
  selectedCourseCodes: [],
  selectedLevel: "",
  selectedCategories: [],
  selectedTags: [],
  favoriteOnly: false,
  currentPage: 0,
  pageSize: 10,
  totalPages: 0,
  totalElements: 0,
  suggestions: [],
  loadingSuggestions: false,
  documents: [],
  loading: false,
  error: null,
  advancedFilters: {},
};

// Helper function to check if a filter array
const isFilterEmpty = (values: string[]): boolean => {
  return values.length === 0;
};

export const fetchRecommendedDocuments = createAsyncThunk(
  "search/fetchRecommendedDocuments",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const { currentPage, pageSize, favoriteOnly } = state.search;

      // Add favoriteOnly parameter to the request
      const response = await documentService.getRecommendationDocuments(undefined, pageSize, currentPage, favoriteOnly);
      return response.data;
    } catch (error) {
      return rejectWithValue(error.message);
    }
  },
);

// Async thunk for search suggestions
export const fetchSuggestions = createAsyncThunk(
  "search/fetchSuggestions",
  async (query: string, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const { selectedMajors, selectedCourseCodes, selectedLevel, selectedCategories, selectedTags } = state.search;

      const filters = {
        majors: isFilterEmpty(selectedMajors) ? undefined : selectedMajors,
        courseCodes: isFilterEmpty(selectedCourseCodes) ? undefined : selectedCourseCodes,
        level: selectedLevel === "all" ? undefined : selectedLevel,
        categories: isFilterEmpty(selectedCategories) ? undefined : selectedCategories,
        tags: selectedTags.length > 0 ? selectedTags : undefined,
      };

      const response = await searchService.suggestions(query, filters);
      return response.data;
    } catch (error) {
      return rejectWithValue(error.message);
    }
  },
);

// Async thunk for fetching documents
export const fetchSearchDocuments = createAsyncThunk(
  "search/fetchSearchDocuments",
  async (_, { getState, rejectWithValue }) => {
    try {
      const state = getState() as RootState;
      const {
        searchTerm,
        selectedMajors,
        selectedCourseCodes,
        selectedLevel,
        selectedCategories,
        selectedTags,
        selectedSort,
        currentPage,
        pageSize,
        advancedFilters,
        favoriteOnly,
      } = state.search;

      const selectSortParts = selectedSort.split(",");

      const filters = {
        search: searchTerm,
        majors: isFilterEmpty(selectedMajors) ? undefined : selectedMajors,
        courseCodes: isFilterEmpty(selectedCourseCodes) ? undefined : selectedCourseCodes,
        level: selectedLevel === "all" ? undefined : selectedLevel,
        categories: isFilterEmpty(selectedCategories) ? undefined : selectedCategories,
        tags: selectedTags.length > 0 ? selectedTags : undefined,
        sortField: selectSortParts.length > 0 ? selectSortParts[0] : undefined,
        sortDirection: selectSortParts.length > 1 ? selectSortParts[1] : undefined,
        favoriteOnly,
        ...advancedFilters,
      };

      const response = await searchService.searchDocuments(filters, currentPage, pageSize);
      return response.data;
    } catch (error) {
      return rejectWithValue(error.message);
    }
  },
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
    setMajors: (state, action: PayloadAction<string[]>) => {
      state.selectedMajors = action.payload;
      state.currentPage = 0;
    },
    setCourseCodes: (state, action: PayloadAction<string[]>) => {
      state.selectedCourseCodes = action.payload;
      state.currentPage = 0;
    },
    setLevel: (state, action: PayloadAction<string>) => {
      state.selectedLevel = action.payload;
      state.currentPage = 0;
    },
    setCategories: (state, action: PayloadAction<string[]>) => {
      state.selectedCategories = action.payload;
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
    toggleFavoriteOnly: (state) => {
      state.favoriteOnly = !state.favoriteOnly;
      state.currentPage = 0; // Reset to first page when toggling favorites
    },
    setAdvancedFilters: (state, action: PayloadAction<Partial<SearchState["advancedFilters"]>>) => {
      state.advancedFilters = {
        ...state.advancedFilters,
        ...action.payload,
      };
      state.currentPage = 0;
    },
    resetFilters: (state) => {
      return {
        ...initialState,
        documents: state.documents, // Preserve current documents until new search
        suggestions: state.suggestions, // Preserve suggestions
        isSearchMode: false,
      };
    },
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
      .addCase(fetchSearchDocuments.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchSearchDocuments.fulfilled, (state, action) => {
        state.loading = false;
        state.documents = action.payload.content;
        state.totalPages = action.payload.totalPages;
        state.currentPage = action.payload.number;
        state.totalElements = action.payload.totalElements;
        state.pageSize = action.payload.size;
        state.isSearchMode = true;
      })
      .addCase(fetchSearchDocuments.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
        state.documents = [];
        state.totalPages = 0;
        state.totalElements = 0;
      })
      .addCase(fetchRecommendedDocuments.pending, (state) => {
        state.loading = true;
      })
      .addCase(fetchRecommendedDocuments.fulfilled, (state, action) => {
        state.loading = false;
        state.documents = action.payload.content;
        state.totalPages = action.payload.totalPages;
        state.currentPage = action.payload.number;
        state.totalElements = action.payload.totalElements;
        state.pageSize = action.payload.size;
        state.isSearchMode = false;
      })
      .addCase(fetchRecommendedDocuments.rejected, (state) => {
        state.loading = false;
        state.documents = [];
      });
  },
});

export const {
  setSearchTerm,
  setSort,
  setMajors,
  setCourseCodes,
  setLevel,
  setCategories,
  setTags,
  setPage,
  setPageSize,
  setAdvancedFilters,
  resetFilters,
  toggleFavoriteOnly,
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
  totalElements: state.search.totalElements,
});

export default searchSlice.reducer;