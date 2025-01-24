import { SearchFilters } from "@/components/document/my-document/advanced-search";
import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { DocumentInformation, DocumentMetadataUpdate } from "@/types/document";
import { PageResponse } from "@/types/user";

class DocumentService extends BaseService {
  uploadDocument(formData: FormData) {
    return this.handleApiResponse<DocumentInformation>(
      axiosInstance.post("/document/api/v1/documents", formData, {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      })
    );
  }

  downloadDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.get(`/document/api/v1/documents/downloads/${id}`, {
        responseType: "blob"
      })
    );
  }

  getUserDocuments(page: number = 0, size: number = 12, filters: SearchFilters = {}) {
    const searchRequest = {
      search: filters.search,
      major: filters.major,
      level: filters.level,
      category: filters.category,
      tags: filters.tags,
      page,
      size,
      sortField: filters.sort?.split(',')[0] || 'createdAt',
      sortDirection: filters.sort?.split(',')[1] || 'desc'
    };

    return this.handleApiResponse<PageResponse<DocumentInformation>>(
      axiosInstance.post("/document/api/v1/documents/user/search", searchRequest)
    );
  }

  getDocumentThumbnail(id: string) {
    return this.handleApiResponse(
      axiosInstance.get(`/document/api/v1/documents/thumbnails/${id}`, {
        responseType: "blob"
      }));
  }

  getDocumentDetails(id: string) {
    return axiosInstance.get(`/document/api/v1/documents/${id}`);
  }

  updateDocument(id: string, data: DocumentMetadataUpdate) {
    return this.handleApiResponse(
      axiosInstance.put(`/document/api/v1/documents/${id}`, data)
    );
  }

  deleteDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.delete(`/document/api/v1/documents/${id}`)
    );
  }

  updateFile(id: string, formData: FormData) {
    return this.handleApiResponse(
      axiosInstance.put(`/document/api/v1/documents/${id}/file`, formData, {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      })
    );
  }

  getTagSuggestions(prefix?: string) {
    return this.handleApiResponse<string[]>(
      axiosInstance.get(`/document/api/v1/documents/tags/suggestions`, {
        params: { prefix }
      })
    );
  }

  getShareSettings(documentId: string) {
    return this.handleApiResponse(
      axiosInstance.get(`/document/api/v1/documents/${documentId}/share`)
    );
  }

  updateShareSettings(documentId: string, settings: {
    isPublic: boolean;
    sharedWith: string[];
  }) {
    return this.handleApiResponse(
      axiosInstance.put(`/document/api/v1/documents/${documentId}/share`, settings)
    );
  }
}

export const documentService = new DocumentService();