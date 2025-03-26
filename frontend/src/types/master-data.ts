export interface Translation {
  [key: string]: string;
}

export enum MasterDataType {
  MAJOR = "MAJOR",
  COURSE_CODE = "COURSE_CODE",
  COURSE_LEVEL = "COURSE_LEVEL",
  DOCUMENT_CATEGORY = "DOCUMENT_CATEGORY",
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
  parentId?: string;
  fullUpdate?: boolean;
}

// Define the type for the hierarchy
type MasterDataHierarchy = {
  [key in MasterDataType]: MasterDataType[];
};

export const MASTER_DATA_HIERARCHY: MasterDataHierarchy = {
  [MasterDataType.MAJOR]: [],
  [MasterDataType.COURSE_CODE]: [MasterDataType.MAJOR],
  [MasterDataType.COURSE_LEVEL]: [],
  [MasterDataType.DOCUMENT_CATEGORY]: [],
};