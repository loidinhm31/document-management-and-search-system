export interface UpdatePreferencesRequest {
  preferredMajors?: string[];
  preferredLevels?: string[];
  preferredCategories?: string[];
  preferredTags?: string[];
  languagePreferences?: string[];
}

export interface PreferenceCategory {
  type: string;
  weight: number;
}

export interface DocumentPreferences {
  preferredMajors: Set<string>;
  preferredCourseCodes: Set<string>;
  preferredLevels: Set<string>;
  preferredCategories: Set<string>;
  preferredTags: Set<string>;
  languagePreferences: Set<string>;
  contentTypeWeights: Record<string, number>;
}

export interface InteractionStats {
  interactionCounts: {
    VIEW: number;
    DOWNLOAD: number;
    FAVORITE: number;
    COMMENT: number;
    SHARE: number;
  };
  uniqueDocumentsAccessed: number;
}

export enum InteractionType {
  VIEW = "VIEW",
  DOWNLOAD = "DOWNLOAD",
  FAVORITE = "FAVORITE",
  COMMENT = "COMMENT",
  SHARE = "SHARE",
}

export interface DocumentFavoriteCheck {
  isFavorited: boolean;
  favoriteCount: number;
}
