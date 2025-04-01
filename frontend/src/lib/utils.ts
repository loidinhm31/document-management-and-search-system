import { type ClassValue, clsx } from "clsx";
import i18n from "i18next";
import { twMerge } from "tailwind-merge";

import { MasterData, MasterDataType } from "@/types/master-data";
import moment from "moment-timezone";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export interface MasterDataMap {
  majors?: MasterData[];
  courseCodes?: MasterData[];
  levels?: MasterData[];
  categories?: MasterData[];
}

export const getMasterDataTranslation = (code: string, type: MasterDataType, masterData: MasterDataMap): string => {
  let data;
  switch (type) {
    case MasterDataType.MAJOR:
      data = masterData.majors.find((item) => item.code === code);
      break;
    case MasterDataType.COURSE_CODE:
      data = masterData.courseCodes.find((item) => item.code === code);
      break;
    case MasterDataType.COURSE_LEVEL:
      data = masterData.levels.find((item) => item.code === code);
      break;
    case MasterDataType.DOCUMENT_CATEGORY:
      data = masterData.categories.find((item) => item.code === code);
      break;
  }
  return data ? data.translations[i18n.language] || data.translations.en : code;
};

export const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleString();
};

export const formatDateMoment = (dateString: string) => {
  return moment(dateString).format("DD/MM/YYYY, h:mm a");
};

export const getDescriptionType = (type: string) => {
  switch (type) {
    case "WORD":
    case "WORD_DOCX":
      return i18n["t"]("document.preferences.analytics.contentTypes.word");
    case "PDF":
      return i18n["t"]("document.preferences.analytics.contentTypes.pdf");
    case "EXCEL":
    case "EXCEL_XLSX":
      return i18n["t"]("document.preferences.analytics.contentTypes.excel");
    case "POWERPOINT":
    case "POWERPOINT_PPTX":
      return i18n["t"]("document.preferences.analytics.contentTypes.ppt");
    case "TEXT_PLAIN":
      return i18n["t"]("document.preferences.analytics.contentTypes.text");
    case "CSV":
      return i18n["t"]("document.preferences.analytics.contentTypes.csv");
    case "XML":
      return i18n["t"]("document.preferences.analytics.contentTypes.xml");
    case "JSON":
      return i18n["t"]("document.preferences.analytics.contentTypes.json");
    case "MARKDOWN":
      return i18n["t"]("document.preferences.analytics.contentTypes.markdown");
  }
};
