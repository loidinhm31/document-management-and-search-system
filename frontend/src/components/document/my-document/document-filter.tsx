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
      <Select value={majorValue} onValueChange={onMajorChange}>
        <SelectTrigger>
          <SelectValue placeholder={t("document.myDocuments.search.advancedSearch.majorPlaceholder")} />
        </SelectTrigger>
        <SelectContent>
          {majors.map((major) => (
            <SelectItem key={major.value} value={major.value}>
              {major.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* Level Filter */}
      <Select value={levelValue} onValueChange={onLevelChange}>
        <SelectTrigger>
          <SelectValue placeholder={t("document.myDocuments.search.advancedSearch.levelPlaceholder")} />
        </SelectTrigger>
        <SelectContent>
          {courseTypes.map((level) => (
            <SelectItem key={level.value} value={level.value}>
              {level.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* Category Filter */}
      <Select value={categoryValue} onValueChange={onCategoryChange}>
        <SelectTrigger>
          <SelectValue placeholder={t("document.myDocuments.search.advancedSearch.categoryPlaceholder")} />
        </SelectTrigger>
        <SelectContent>
          {categories.map((category) => (
            <SelectItem key={category.value} value={category.value}>
              {category.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      {/* Tags Input */}
      <TagInput
        value={tagsValue}
        onChange={onTagsChange}
        placeholder={t("document.myDocuments.search.advancedSearch.tagsPlaceholder")}
      />
    </div>
  );
};

export default DocumentFilter;