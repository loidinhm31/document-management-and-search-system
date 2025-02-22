import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { MasterData, MasterDataType } from "@/types/master-data";

class MasterDataService extends BaseService {
  getAllByType(type: MasterDataType, active?: boolean) {
    return this.handleApiResponse<MasterData[]>(
      axiosInstance.get(`/document-interaction/api/v1/master-data/${type}`, {
        params: { active },
      }),
    );
  }

  getByTypeAndCode(type: MasterDataType, code: string) {
    return this.handleApiResponse<MasterData>(
      axiosInstance.get(`/document-interaction/api/v1/master-data/${type}/${code}`),
    );
  }

  getAllByTypeAndParentId(type: MasterDataType, parentId: string, active  ?: boolean) {
    return this.handleApiResponse<MasterData[]>(
      axiosInstance.get(`/document-interaction/api/v1/master-data/${type}/parent/${parentId}`, {
        params: { active },
      }),
    );
  }

  searchByText(query: string) {
    return this.handleApiResponse<MasterData[]>(
      axiosInstance.get(`/document-interaction/api/v1/master-data/search`, {
        params: { query },
      }),
    );
  }

  create(data: MasterData) {
    return this.handleApiResponse<MasterData>(axiosInstance.post("/document-interaction/api/v1/master-data", data));
  }

  update(id: string, data: MasterData) {
    return this.handleApiResponse<MasterData>(
      axiosInstance.put(`/document-interaction/api/v1/master-data/${id}`, data),
    );
  }

  save(data: MasterData) {
    if (data.id) {
      return this.update(data.id, data);
    }
    return this.create(data);
  }

  deleteById(id: string) {
    return this.handleApiResponse(axiosInstance.delete(`/document-interaction/api/v1/master-data/${id}`));
  }
}

export const masterDataService = new MasterDataService();
