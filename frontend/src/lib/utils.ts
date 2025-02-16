import { type ClassValue, clsx } from "clsx";
import i18n from "i18next";
import { twMerge } from "tailwind-merge";

import { MasterData, MasterDataType } from "@/types/master-data";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export interface MasterDataMap {
  majors?: MasterData[];
  levels?: MasterData[];
  categories?: MasterData[];
}

export const getMasterDataTranslation = (
  code: string,
  type: MasterDataType,
  masterData: MasterDataMap
): string => {
  let data;
  switch (type) {
    case MasterDataType.MAJOR:
      data = masterData.majors.find(item => item.code === code);
      break;
    case MasterDataType.COURSE_LEVEL:
      data = masterData.levels.find(item => item.code === code);
      break;
    case MasterDataType.DOCUMENT_CATEGORY:
      data = masterData.categories.find(item => item.code === code);
      break;
  }
  return data ? (data.translations[i18n.language] || data.translations.en) : code;
};