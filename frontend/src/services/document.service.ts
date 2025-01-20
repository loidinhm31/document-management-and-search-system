import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { DocumentUploadResponse } from "@/types/document";

class DocumentService extends BaseService {
  uploadDocument(formData: FormData) {
    return this.handleApiResponse<DocumentUploadResponse>(
      axiosInstance.post("/document/api/v1/documents", formData, {
        headers: {
          "Content-Type": "multipart/form-data"
        }
      })
    );
  }

  downloadDocument(id: string) {
    return axiosInstance.get(`/document/api/v1/documents/${id}`, {
      responseType: "blob"
    });
  }

  updateDocument(id: string, metadata: Record<string, string>) {
    return this.handleApiResponse(
      axiosInstance.put(`/document/api/v1/documents/${id}/metadata`, metadata)
    );
  }

  deleteDocument(id: string) {
    return this.handleApiResponse(
      axiosInstance.delete(`/document/api/v1/documents/${id}`)
    );
  }

  updateTags(id: string, tags: string[]) {
    return this.handleApiResponse<Document>(
      axiosInstance.put(`/document/api/v1/documents/${id}/tags`, tags)
    );
  }

  getTagSuggestions(prefix?: string) {
    return this.handleApiResponse<string[]>(
      axiosInstance.get(`/document/api/v1/documents/tags/suggestions`, {
        params: { prefix }
      })
    );
  }
}

export const documentService = new DocumentService();