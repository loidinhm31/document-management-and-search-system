import { Label } from "@radix-ui/react-menu";
import React from "react";
import { useTranslation } from "react-i18next";

import TagInput from "@/components/tag-input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

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
            {majors.map((major) => (
              <SelectItem key={major.value} value={major.value}>
                {major.label}
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
            {courseTypes.map((level) => (
              <SelectItem key={level.value} value={level.value}>
                {level.label}
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
            {categories.map((category) => (
              <SelectItem key={category.value} value={category.value}>
                {category.label}
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