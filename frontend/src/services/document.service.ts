import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { DocumentInformation, DocumentMetadataUpdate } from "@/types/document";
import { UserSearchResponse } from "@/types/user";

class DocumentService extends BaseService {
  uploadDocument(formData: FormData) {
    return this.handleApiResponse<DocumentInformation>(
      axiosInstance.post("/document-interaction/api/v1/documents", formData, {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      })
    );
  }

  downloadDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/documents/${id}/downloads`, {
        responseType: "blob"
      })
    );
  }

  getDocumentThumbnail(id: string, versionInfo: string) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/documents/${id}/thumbnails`, {
        responseType: "blob",
        headers: {
          "Cache-Control": "no-cache, no-store, must-revalidate",
          "Pragma": "no-cache",
          "Expires": "0"
        },
        params: {
          [versionInfo]: ""  // Add version info as URL parameter
        }
      }));
  }

  getDocumentDetails(id: string) {
    return axiosInstance.get<DocumentInformation>(`/document-interaction/api/v1/documents/${id}`);
  }

  updateDocument(id: string, data: DocumentMetadataUpdate) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/documents/${id}`, data)
    );
  }

  deleteDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.delete(`/document-interaction/api/v1/documents/${id}`)
    );
  }

  updateDocumentWithFile(id: string, formData: FormData) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/documents/${id}/file`, formData, {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      })
    );
  }

  getTagSuggestions(prefix?: string) {
    return this.handleApiResponse<string[]>(
      axiosInstance.get(`/document-interaction/api/v1/documents/tags/suggestions`, {
        params: { prefix }
      })
    );
  }

  getShareSettings(documentId: string) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/shares/documents/${documentId}`)
    );
  }

  updateShareSettings(documentId: string, settings: {
    isPublic: boolean;
    sharedWith: string[];
  }) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/shares/documents/${documentId}`, settings)
    );
  }

  searchShareableUsers(query: string) {
    return this.handleApiResponse<UserSearchResponse[]>(
      axiosInstance.get(`/document-interaction/api/v1/shares/users`, {
        params: { query }
      })
    );
  }

  getShareableUsersByIds(userIds: string[]) {
    return this.handleApiResponse<UserSearchResponse[]>(
      axiosInstance.post(`/document-interaction/api/v1/shares/users/details`, userIds)
    );
  }

  bookmarkDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.post(`/document-interaction/api/v1/bookmarks/documents/${id}`)
    );
  }

  unbookmarkDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.delete(`/document-interaction/api/v1/bookmarks/documents/${id}`)
    );
  }

  isDocumentBookmarked(id: string) {
    return this.handleApiResponse<boolean>(
      axiosInstance.get(`/document-interaction/api/v1/bookmarks/documents/${id}/status`)
    );
  }

  downloadDocumentVersion(documentId: string, versionNumber: number) {
    return this.handleApiResponse(
      axiosInstance.get(
        `/document-interaction/api/v1/documents/${documentId}/versions/${versionNumber}/download`,
        { responseType: "blob" }
      )
    );
  }

  revertToVersion(documentId: string, versionNumber: number) {
    return this.handleApiResponse(
      axiosInstance.post<DocumentInformation>(`/document-interaction/api/v1/documents/${documentId}/versions/${versionNumber}/revert`)
    );
  }

  getRelatedDocuments(documentId: string, size: number = 6, page: number = 0) {
    return this.handleApiResponse(
      axiosInstance.get(
        `/document-search/api/v1/documents/${documentId}/related`,
        {
          params: { size, page }
        }
      )
    );
  }

  getDocumentComments(documentId, params = {}) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/comments/documents/${documentId}`, { params })
    );
  }

  createComment(documentId, data) {
    return this.handleApiResponse(
      axiosInstance.post(`/document-interaction/api/v1/comments/documents/${documentId}`, data)
    );
  }

  updateComment(commentId, data) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/comments/${commentId}`, data)
    );
  }

  deleteComment(commentId) {
    return this.handleApiResponse(
      axiosInstance.delete(`/document-interaction/api/v1/comments/${commentId}`)
    );
  }
}

export const documentService = new DocumentService();