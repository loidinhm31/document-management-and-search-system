import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { DocumentSearchResponse } from "@/types/document";

export interface SearchFilters {
  search?: string;
  major?: string;
  level?: string;
  category?: string;
  tags?: string[];
  sort?: string;
}

class SearchService extends BaseService {
  searchDocuments(filters: SearchFilters, page = 0, size = 10) {
    return this.handleApiResponse<DocumentSearchResponse>(
      axiosInstance.post(`/document/api/v1/search`, {
        ...filters,
        page,
        size
      })
    );
  }

  suggestions(query: string, filters?: Omit<SearchFilters, 'search' | 'sort'>) {
    return this.handleApiResponse<string[]>(
      axiosInstance.post(`/document/api/v1/search/suggestions`, {
        query,
        ...filters
      })
    );
  }
}

export const searchService = new SearchService();