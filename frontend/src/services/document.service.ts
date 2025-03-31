import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { CommentCreateData } from "@/types/comment";
import { DocumentInformation, DocumentMetadataUpdate } from "@/types/document";
import { UpdatePreferencesRequest } from "@/types/document-preference";
import { UserDocumentActionType, UserHistoryFilter, UserHistoryPage } from "@/types/document-user-history";
import { UserSearchResponse } from "@/types/user";

class DocumentService extends BaseService {
  uploadDocument(formData: FormData) {
    return this.handleApiResponse<DocumentInformation>(
      axiosInstance.post("/document-interaction/api/v1/documents", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      }),
    );
  }

  downloadDocument(payload: { id: string; action?: string; history?: boolean }) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/documents/${payload?.id}/file`, {
        responseType: "blob",
        params: {
          action: payload?.action,
          history: payload?.history,
        },
      }),
    );
  }

  getDocumentThumbnail(id: string, versionInfo: string) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/documents/${id}/thumbnail`, {
        responseType: "blob",
        headers: {
          "Cache-Control": "no-cache, no-store, must-revalidate",
          Pragma: "no-cache",
          Expires: "0",
        },
        params: {
          [versionInfo]: "",
        },
      }),
    );
  }

  async getDocumentDetails(id: string, history?: boolean) {
    return axiosInstance.get<DocumentInformation>(`/document-interaction/api/v1/documents/${id}`, {
      params: {
        history,
      },
    });
  }

  updateDocument(id: string, data: DocumentMetadataUpdate) {
    return this.handleApiResponse(axiosInstance.put(`/document-interaction/api/v1/documents/${id}`, data));
  }

  deleteDocument(id: string) {
    return this.handleApiResponse(axiosInstance.delete(`/document-interaction/api/v1/documents/${id}`));
  }

  updateDocumentWithFile(id: string, formData: FormData) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/documents/${id}/file`, formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      }),
    );
  }

  getTagSuggestions(prefix?: string) {
    return this.handleApiResponse<string[]>(
      axiosInstance.get(`/document-interaction/api/v1/documents/tags/suggestions`, {
        params: { prefix },
      }),
    );
  }

  getShareSettings(documentId: string) {
    return this.handleApiResponse(axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/sharing`));
  }

  updateShareSettings(
    documentId: string,
    settings: {
      isPublic: boolean;
      sharedWith: string[];
    },
  ) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/documents/${documentId}/sharing`, settings),
    );
  }

  searchShareableUsers(query: string) {
    return this.handleApiResponse<UserSearchResponse[]>(
      axiosInstance.get(`/document-interaction/api/v1/documents/sharing/users`, {
        params: { query },
      }),
    );
  }

  getShareableUsersByIds(userIds: string[]) {
    return this.handleApiResponse<UserSearchResponse[]>(
      axiosInstance.post(`/document-interaction/api/v1/documents/sharing/users/details`, userIds),
    );
  }

  favoriteDocument(id: string) {
    return this.handleApiResponse(axiosInstance.post(`/document-interaction/api/v1/documents/${id}/favorites`));
  }

  unfavoriteDocument(id: string) {
    return this.handleApiResponse(axiosInstance.delete(`/document-interaction/api/v1/documents/${id}/favorites`));
  }

  async isDocumentFavorited(id: string) {
    return axiosInstance.get(`/document-interaction/api/v1/documents/${id}/favorites/status`);
  }

  downloadDocumentVersion(payload: { documentId: string; versionNumber: number; action?: string; history?: boolean }) {
    return this.handleApiResponse(
      axiosInstance.get(
        `/document-interaction/api/v1/documents/${payload.documentId}/versions/${payload.versionNumber}/file`,
        {
          responseType: "blob",
          params: {
            action: payload?.action,
            history: payload?.history,
          },
        },
      ),
    );
  }

  revertToVersion(documentId: string, versionNumber: number) {
    return this.handleApiResponse(
      axiosInstance.put<DocumentInformation>(
        `/document-interaction/api/v1/documents/${documentId}/versions/${versionNumber}`,
      ),
    );
  }

  getRecommendationDocuments(documentId: string, size: number = 6, page: number = 0, favoriteOnly: boolean = false) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-search/api/v1/documents/recommendation`, {
        params: { documentId, size, page, favoriteOnly },
      }),
    );
  }

  getDocumentComments(documentId: string, params = {}) {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/comments`, { params }),
    );
  }

  createComment(documentId: string, data: CommentCreateData) {
    return this.handleApiResponse(
      axiosInstance.post(`/document-interaction/api/v1/documents/${documentId}/comments`, data),
    );
  }

  updateComment(documentId: string, commentId: number, data: { content: string }) {
    return this.handleApiResponse(
      axiosInstance.put(`/document-interaction/api/v1/documents/${documentId}/comments/${commentId}`, data),
    );
  }

  deleteComment(documentId: string, commentId: number) {
    return this.handleApiResponse(
      axiosInstance.delete(`/document-interaction/api/v1/documents/${documentId}/comments/${commentId}`),
    );
  }

  getDocumentPreferences() {
    return this.handleApiResponse(axiosInstance.get(`/document-interaction/api/v1/documents/preferences`));
  }

  getDocumentContentWeights() {
    return this.handleApiResponse(
      axiosInstance.get(`/document-interaction/api/v1/documents/preferences/content-weights`),
    );
  }

  updateDocumentPreferences(preferences: UpdatePreferencesRequest) {
    return this.handleApiResponse(axiosInstance.put(`/document-interaction/api/v1/documents/preferences`, preferences));
  }

  getInteractionStatistics() {
    return this.handleApiResponse(axiosInstance.get(`/document-interaction/api/v1/documents/preferences/statistics`));
  }

  getRecommendedTags() {
    return this.handleApiResponse(axiosInstance.get(`/document-interaction/api/v1/documents/preferences/tags`));
  }

  async getDocumentStatistics(documentId: string) {
    return axiosInstance.get(`/document-interaction/api/v1/documents/${documentId}/statistics`);
  }

  async getUserHistory(filters: UserHistoryFilter = {}): Promise<UserHistoryPage> {
    const params = new URLSearchParams();

    if (filters.actionType && filters.actionType !== UserDocumentActionType.ALL) params.append("actionType", filters.actionType);
    if (filters.fromDate) params.append("fromDate", filters.fromDate);
    if (filters.toDate) params.append("toDate", filters.toDate);
    if (filters.documentName) params.append("documentName", filters.documentName);

    params.append("page", String(filters.page || 0));
    params.append("size", String(filters.size || 20));

    const response = await axiosInstance.get(`/document-interaction/api/v1/documents?${params.toString()}`);
    return response.data;
  }
}

export const documentService = new DocumentService();
