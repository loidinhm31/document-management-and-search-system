import { type ClassValue, clsx } from "clsx";
import i18n from "i18next";
import { twMerge } from "tailwind-merge";

import { MasterData } from "@/types/document";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export type MasterDataType = "major" | "level" | "category";

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
    case "major":
      data = masterData.majors.find(item => item.code === code);
      break;
    case "level":
      data = masterData.levels.find(item => item.code === code);
      break;
    case "category":
      data = masterData.categories.find(item => item.code === code);
      break;
  }
  return data ? (data.translations[i18n.language] || data.translations.en) : code;
};