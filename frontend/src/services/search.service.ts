import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { DocumentSearchResponse } from "@/types/document";

class SearchService extends BaseService {

  searchDocuments(query: string, page = 0, size = 10) {
    return axiosInstance.get<DocumentSearchResponse>(`/document/api/v1/search`, {
      params: {
        query,
        page,
        size
      }
    });
  }

  suggestions(query: string, page = 0, size = 10) {
    return axiosInstance.get(`/document/api/v1/search/suggestions`, {
      params: {
        query
      }
    });
  }
}

export const searchService = new SearchService();
