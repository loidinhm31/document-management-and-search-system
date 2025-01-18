import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { Document, DocumentUploadResponse } from "@/types/document";

class DocumentService extends BaseService {

  uploadDocument(file: File, metadata?: Record<string, string>) {
    const formData = new FormData();
    formData.append("file", file);
    if (metadata) {
      formData.append("metadata", JSON.stringify(metadata));
    }

    return this.handleApiResponse<DocumentUploadResponse>(
      axiosInstance.post("/document/api/v1/documents", formData, {
        headers: {
          "Content-Type": "multipart/form-data",
        },
      })
    );
  }

  getDocument(id: string) {
    return this.handleApiResponse<Document>(
      axiosInstance.get(`/document/api/v1/documents/${id}`)
    );
  }

  downloadDocument(id: string) {
    return axiosInstance.get(`/document/api/v1/documents/${id}`, {
      responseType: "blob",
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

}

export const documentService = new DocumentService();
