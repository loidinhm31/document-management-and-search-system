import { Label } from "@radix-ui/react-menu";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import TagInput from "@/components/tag-input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { MasterData } from "@/types/document";
import { masterDataService, MasterDataType } from "@/services/master-data.service";
import i18n from "i18next";

export const courseTypes = [
  { label: "All", value: "all" },
  { label: "Fundamental", value: "FUNDAMENTAL" },
  { label: "Intermediate", value: "INTERMEDIATE" },
  { label: "Advanced", value: "ADVANCED" },
  { label: "Specialized", value: "SPECIALIZED" }
];

export const majors = [
  { label: "All", value: "all" },
  { label: "Software Engineering", value: "SOFTWARE_ENGINEERING" },
  { label: "Artificial Intelligence", value: "ARTIFICIAL_INTELLIGENCE" },
  { label: "Information Security", value: "INFORMATION_SECURITY" },
  { label: "Internet of Things", value: "IOT" }
];

export const categories = [
  { label: "All", value: "all" },
  { label: "Lecture", value: "LECTURE" },
  { label: "Exercise", value: "EXERCISE" },
  { label: "Exam", value: "EXAM" },
  { label: "Reference", value: "REFERENCE" },
  { label: "Lab", value: "LAB" },
  { label: "Project", value: "PROJECT" }
];

export interface DocumentFilterProps {
  majorValue: string;
  onMajorChange: (value: string) => void;
  levelValue: string;
  onLevelChange: (value: string) => void;
  categoryValue: string;
  onCategoryChange: (value: string) => void;
  tagsValue: string[];
  onTagsChange: (tags: string[]) => void;
  className?: string;
}

export const DocumentFilter = ({
                                 majorValue,
                                 onMajorChange,
                                 levelValue,
                                 onLevelChange,
                                 categoryValue,
                                 onCategoryChange,
                                 tagsValue,
                                 onTagsChange,
                                 className
                               }: DocumentFilterProps) => {
  const { t } = useTranslation();

  const [courseLevels, setCourseLevels] = useState<MasterData[]>([]);
  const [majors, setMajors] = useState<MasterData[]>([]);
  const [categories, setCategories] = useState<MasterData[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchMasterData = async () => {
      try {
        const [levelsResponse, majorsResponse, categoriesResponse] = await Promise.all([
          masterDataService.getByType(MasterDataType.COURSE_LEVEL),
          masterDataService.getByType(MasterDataType.MAJOR),
          masterDataService.getByType(MasterDataType.DOCUMENT_CATEGORY)
        ]);

        setCourseLevels(levelsResponse.data);
        setMajors(majorsResponse.data);
        setCategories(categoriesResponse.data);
      } catch (error) {
        console.error('Error fetching master data:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchMasterData();
  }, []);

  if (loading) {
    return <div>Loading...</div>;
  }

  return (
    <div className={`grid gap-4 ${className}`}>
      {/* Major Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.majorLabel")}</Label>
        <Select value={majorValue} onValueChange={onMajorChange}>
          <SelectTrigger>
            <SelectValue placeholder={t("document.commonSearch.majorPlaceholder")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("common.all")}</SelectItem>
            {majors.map((major) => (
              <SelectItem key={major.code} value={major.code}>
                {major.translations[i18n.language] || major.translations.en}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Level Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.levelLabel")}</Label>
        <Select value={levelValue} onValueChange={onLevelChange}>
          <SelectTrigger>
            <SelectValue placeholder={t("document.commonSearch.levelPlaceholder")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("common.all")}</SelectItem>
            {courseLevels.map((level) => (
              <SelectItem key={level.code} value={level.code}>
                {level.translations[i18n.language] || level.translations.en}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Category Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.categoryLabel")}</Label>
        <Select value={categoryValue} onValueChange={onCategoryChange}>
          <SelectTrigger>
            <SelectValue placeholder={t("document.commonSearch.categoryPlaceholder")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("common.all")}</SelectItem>
            {categories.map((category) => (
              <SelectItem key={category.code} value={category.code}>
                {category.translations[i18n.language] || category.translations.en}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Tags Input */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.tagLabel")}</Label>
        <TagInput
          value={tagsValue}
          onChange={onTagsChange}
          placeholder={t("document.commonSearch.tagsPlaceholder")}
        />
      </div>
    </div>
  );
};

export default DocumentFilter;