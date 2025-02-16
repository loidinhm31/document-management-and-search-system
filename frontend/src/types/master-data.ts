export interface Translation {
  en: string;
  vi: string;
}

export enum MasterDataType {
  COURSE_LEVEL = "COURSE_LEVEL",
  MAJOR = "MAJOR",
  DOCUMENT_CATEGORY = "DOCUMENT_CATEGORY"
}

export interface MasterData {
  id?: string;
  type: MasterDataType;
  code: string;
  translations: Translation;
  description?: string;
  createdAt?: Date;
  updatedAt?: Date;
  active: boolean;
}