import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { DocumentInformation, DocumentSearchResponse } from "@/types/document";
import { PageResponse } from "@/types/user";

export interface SearchFilters {
  search?: string;
  majors?: string[];
  courseCodes?: string[];
  level?: string;
  categories?: string[];
  tags?: string[];
  sort?: string;
}

class SearchService extends BaseService {
  searchDocuments(filters: SearchFilters, page = 0, size = 10) {
    return this.handleApiResponse<DocumentSearchResponse>(
      axiosInstance.post(`/document-search/api/v1/search`, {
        ...filters,
        page,
        size,
      }),
    );
  }

  suggestions(query: string, filters?: Omit<SearchFilters, "search" | "sort">) {
    return this.handleApiResponse<string[]>(
      axiosInstance.post(`/document-search/api/v1/search/suggestions`, {
        query,
        ...filters,
      }),
    );
  }

  getUserDocuments(page: number = 0, size: number = 12, filters: SearchFilters = {}) {
    const searchRequest = {
      search: filters.search,
      majors: filters.majors,
      courseCodes: filters.courseCodes,
      level: filters.level,
      categories: filters.categories,
      tags: filters.tags,
      page,
      size,
      sortField: filters.sort?.split(",")[0] || "createdAt",
      sortDirection: filters.sort?.split(",")[1] || "desc",
    };

    return this.handleApiResponse<PageResponse<DocumentInformation>>(
      axiosInstance.post("/document-search/api/v1/documents/me/search", searchRequest),
    );
  }
}

export const searchService = new SearchService();
