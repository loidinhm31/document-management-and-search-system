import { Label } from "@radix-ui/react-menu";
import i18n from "i18next";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";

import TagInputDebounce from "@/components/common/tag-input-debounce";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/master-data-slice";


export interface DocumentFilterProps {
  majorValue: string;
  onMajorChange: (value: string) => void;
  courseCodeValue: string;
  onCourseCodeChange: (value: string) => void;
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
                                 courseCodeValue,
                                 onCourseCodeChange,
                                 levelValue,
                                 onLevelChange,
                                 categoryValue,
                                 onCategoryChange,
                                 tagsValue,
                                 onTagsChange,
                                 className
                               }: DocumentFilterProps) => {
  const { t } = useTranslation();

  const dispatch = useAppDispatch();
  const { majors, courseCodes, levels, categories, loading } = useAppSelector(selectMasterData);

  useEffect(() => {
    if (majors?.length === 0 || courseCodes?.length === 0 || levels?.length === 0 || categories?.length === 0) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, majors?.length, courseCodes?.length, levels?.length, categories?.length]);

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
            <SelectItem value="all">{t("document.commonSearch.all")}</SelectItem>
            {majors.map((major) => (
              <SelectItem key={major.code} value={major.code}>
                {major.translations[i18n.language] || major.translations.en}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Course code Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.courseCodeLabel")}</Label>
        <Select value={courseCodeValue} onValueChange={onCourseCodeChange}>
          <SelectTrigger>
            <SelectValue placeholder={t("document.commonSearch.courseCodePlaceholder")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t("document.commonSearch.all")}</SelectItem>
            {courseCodes?.map((course) => (
              <SelectItem key={course.code} value={course.code}>
                {course.translations[i18n.language] || course.translations.en}
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
            <SelectItem value="all">{t("document.commonSearch.all")}</SelectItem>
            {levels?.map((level) => (
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
            <SelectItem value="all">{t("document.commonSearch.all")}</SelectItem>
            {categories?.map((category) => (
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
        <TagInputDebounce
          value={tagsValue}
          onChange={onTagsChange}
          placeholder={t("document.commonSearch.tagsPlaceholder")}
        />
      </div>
    </div>
  );
};

export default DocumentFilter;