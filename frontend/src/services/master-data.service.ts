import axiosInstance from "@/services/axios.config";
import { BaseService } from "@/services/base.service";
import { MasterData } from "@/types/document";

export enum MasterDataType {
  COURSE_LEVEL = "COURSE_LEVEL",
  MAJOR = "MAJOR",
  DOCUMENT_CATEGORY = "DOCUMENT_CATEGORY"
}

class MasterDataService extends BaseService {
  getByType(type: MasterDataType) {
    return this.handleApiResponse<MasterData[]>(
      axiosInstance.get(`/document/api/v1/master-data/${type}/active`)
    );
  }
}

export const masterDataService = new MasterDataService();