import { Label } from "@radix-ui/react-menu";
import i18n from "i18next";
import React, { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";

import TagInput from "@/components/common/tag-input";
import TagInputDebounce from "@/components/common/tag-input-debounce";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/master-data-slice";

export interface DocumentFilterProps {
  majors: string[];
  onMajorsChange: (value: string[]) => void;
  courseCodes: string[];
  onCourseCodesChange: (value: string[]) => void;
  level: string;
  onLevelChange: (value: string) => void;
  categories: string[];
  onCategoriesChange: (value: string[]) => void;
  tags: string[];
  onTagsChange: (tags: string[]) => void;
  className?: string;
}

export const DocumentFilter = ({
  majors,
  onMajorsChange,
  courseCodes,
  onCourseCodesChange,
  level,
  onLevelChange,
  categories,
  onCategoriesChange,
  tags,
  onTagsChange,
  className,
}: DocumentFilterProps) => {
  const { t } = useTranslation();

  const dispatch = useAppDispatch();
  const {
    majors: availableMajors,
    courseCodes: availableCourseCodes,
    levels,
    categories: availableCategories,
    loading,
  } = useAppSelector(selectMasterData);

  const filteredCourseCodes = useMemo(() => {
    if (majors.length === 0) {
      return availableCourseCodes;
    }

    // Find parent IDs for all selected majors
    const majorParentIds = availableMajors.filter((m) => majors.includes(m.code)).map((m) => m.id);

    // Return course codes for any of the selected majors
    return availableCourseCodes.filter((course) => majorParentIds.includes(course.parentId));
  }, [majors, availableCourseCodes, availableMajors]);

  useEffect(() => {
    // If majors change, validate course codes
    if (courseCodes.length > 0) {
      // Get all valid course code options
      const validCourseCodeOptions = filteredCourseCodes.map((course) => course.code);

      // Filter out any course codes that are no longer valid based on selected majors
      const validCourseCodes = courseCodes.filter((code) => validCourseCodeOptions.includes(code));

      // If we've filtered out any course codes, update the selection
      if (validCourseCodes.length !== courseCodes.length) {
        onCourseCodesChange(validCourseCodes.length > 0 ? validCourseCodes : []);
      }
    }
  }, [majors, filteredCourseCodes, courseCodes, onCourseCodesChange]);

  useEffect(() => {
    if (
      availableMajors?.length === 0 ||
      availableCourseCodes?.length === 0 ||
      levels?.length === 0 ||
      availableCategories?.length === 0
    ) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, availableMajors?.length, availableCourseCodes?.length, levels?.length, availableCategories?.length]);

  if (loading) {
    return <div>Loading...</div>;
  }

  // Helper function for tag display
  const getTagDisplay = (tag: string) => {
    const majorItem = availableMajors.find((m) => m.code === tag);
    if (majorItem) return majorItem.translations[i18n.language] || majorItem.translations.en;

    const courseCodeItem = availableCourseCodes.find((m) => m.code === tag);
    if (courseCodeItem) return courseCodeItem.translations[i18n.language] || courseCodeItem.translations.en;

    const levelItem = levels.find((l) => l.code === tag);
    if (levelItem) return levelItem.translations[i18n.language] || levelItem.translations.en;

    const categoryItem = availableCategories.find((c) => c.code === tag);
    if (categoryItem) return categoryItem.translations[i18n.language] || categoryItem.translations.en;

    return tag;
  };

  return (
    <div className={`grid gap-4 ${className}`}>
      {/* Majors Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.majorLabel")}</Label>
        <TagInput
          value={majors}
          onChange={(values) => onMajorsChange(values.length > 0 ? values : [])}
          recommendedTags={availableMajors?.map((major) => major.code) || []}
          getTagDisplay={getTagDisplay}
          placeholder={t("document.commonSearch.majorPlaceholder")}
          disabled={!availableMajors}
        />
      </div>

      {/* Course Codes Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.courseCodeLabel")}</Label>
        <TagInput
          value={courseCodes}
          onChange={(values) => onCourseCodesChange(values.length > 0 ? values : [])}
          recommendedTags={filteredCourseCodes?.map((course) => course.code)}
          getTagDisplay={getTagDisplay}
          placeholder={t("document.commonSearch.courseCodePlaceholder")}
          disabled={!filteredCourseCodes || filteredCourseCodes.length === 0 || majors.length === 0}
        />
      </div>

      {/* Level Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.levelLabel")}</Label>
        <Select value={level} onValueChange={onLevelChange}>
          <SelectTrigger disabled={!levels}>
            <SelectValue placeholder={t("document.commonSearch.levelPlaceholder")} />
          </SelectTrigger>
          <SelectContent>
            {levels?.map((level) => (
              <SelectItem key={level.code} value={level.code}>
                {level.translations[i18n.language] || level.translations.en}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Categories Filter */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.categoryLabel")}</Label>
        <TagInput
          value={categories}
          onChange={(values) => onCategoriesChange(values.length > 0 ? values : [])}
          recommendedTags={availableCategories?.map((category) => category.code) || []}
          getTagDisplay={getTagDisplay}
          placeholder={t("document.commonSearch.categoryPlaceholder")}
          disabled={!availableCategories}
        />
      </div>

      {/* Tags Input */}
      <div className="space-y-2">
        <Label>{t("document.commonSearch.tagLabel")}</Label>
        <TagInputDebounce
          value={tags}
          onChange={onTagsChange}
          placeholder={t("document.commonSearch.tagsPlaceholder")}
        />
      </div>
    </div>
  );
};

export default DocumentFilter;
