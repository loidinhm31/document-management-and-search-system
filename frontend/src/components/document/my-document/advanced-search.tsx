import { Filter, Search, SortAsc, SortDesc } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import DocumentFilter from "@/components/document/document-filter";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

export interface SearchFilters {
  search?: string;
  majors?: string[];
  courseCodes?: string[];
  level?: string;
  categories?: string[];
  sort?: string;
  tags?: string[];
  favoriteOnly?: boolean;
}

interface AdvancedSearchProps {
  onSearch: (filters: SearchFilters) => void;
}

export const AdvancedSearch = ({ onSearch }: AdvancedSearchProps) => {
  const { t } = useTranslation();

  const sortOptions = [
    { label: t("document.myDocuments.search.sortOptions.createdDateNewest"), value: "createdAt,desc" },
    { label: t("document.myDocuments.search.sortOptions.createdDateOldest"), value: "createdAt,asc" },
    { label: t("document.myDocuments.search.sortOptions.nameAz"), value: "filename,asc" },
    { label: t("document.myDocuments.search.sortOptions.nameZa"), value: "filename,desc" },
    { label: t("document.myDocuments.search.sortOptions.sizeLargest"), value: "fileSize,desc" },
    { label: t("document.myDocuments.search.sortOptions.sizeSmallest"), value: "fileSize,asc" },
  ];

  const [showAdvanced, setShowAdvanced] = useState(false);

  // Search states
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedSort, setSelectedSort] = useState(sortOptions[0].value);

  // Filter states
  const [selectedMajors, setSelectedMajors] = useState<string[]>([]);
  const [selectedCourseCodes, setSelectedCourseCodes] = useState<string[]>([]);
  const [selectedLevel, setSelectedLevel] = useState(undefined);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  const handleSearch = () => {
    onSearch({
      search: searchTerm,
      majors: selectedMajors.length <= 0 ? undefined : selectedMajors,
      courseCodes: selectedCourseCodes.length <= 0 ? undefined : selectedCourseCodes,
      level: selectedLevel ? selectedLevel : undefined,
      categories: selectedCategories.length <= 0 ? undefined : selectedCategories,
      sort: selectedSort,
      tags: selectedTags.length > 0 ? selectedTags : undefined,
    });
  };

  const handleReset = () => {
    setSearchTerm("");
    setSelectedMajors([]);
    setSelectedCourseCodes([]);
    setSelectedLevel(undefined);
    setSelectedCategories([]);
    setSelectedTags([]);
    setSelectedSort(sortOptions[0].value);
    onSearch({});
  };

  const getActiveFilterCount = () => {
    let count = 0;
    if (selectedMajors.length > 0) count++;
    if (selectedCourseCodes.length > 0) count++;
    if (selectedLevel) count++;
    if (selectedCategories.length > 0) count++;
    if (selectedTags.length > 0) count++;
    return count;
  };

  return (
    <Card className="mb-6">
      <CardContent className="pt-6">
        {/* Primary Search Bar */}
        <div className="flex flex-wrap gap-4 mb-4">
          <div className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t("document.myDocuments.search.advancedSearch.searchPlaceholder")}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-9"
              />
            </div>
          </div>

          <Button onClick={handleSearch} className="gap-2">
            {t("document.commonSearch.apply")}
          </Button>

          {/* Sort Dropdown */}
          <Select value={selectedSort} onValueChange={setSelectedSort}>
            <SelectTrigger className="w-[200px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {sortOptions.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  <span className="flex items-center gap-2">
                    {option.value.endsWith("desc") ? <SortDesc className="h-4 w-4" /> : <SortAsc className="h-4 w-4" />}
                    {option.label}
                  </span>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* Advanced Filter Toggle */}
          <Button variant="outline" onClick={() => setShowAdvanced(!showAdvanced)} className="relative gap-2">
            <Filter className="h-4 w-4" />
            {t("document.commonSearch.filters")}
            {getActiveFilterCount() > 0 && (
              <span className="absolute -top-2 -right-2 h-5 w-5 rounded-full bg-primary text-xs flex items-center justify-center text-primary-foreground">
                {getActiveFilterCount()}
              </span>
            )}
          </Button>

          <Button variant="outline" onClick={handleReset}>
            {t("document.commonSearch.reset")}
          </Button>
        </div>

        {/* Advanced Search Section using DocumentFilter */}
        {showAdvanced && (
          <div className="space-y-4">
            <DocumentFilter
              majors={selectedMajors}
              onMajorsChange={setSelectedMajors}
              courseCodes={selectedCourseCodes}
              onCourseCodesChange={setSelectedCourseCodes}
              level={selectedLevel}
              onLevelChange={setSelectedLevel}
              categories={selectedCategories}
              onCategoriesChange={setSelectedCategories}
              tags={selectedTags}
              onTagsChange={setSelectedTags}
              className="md:grid-cols-3 lg:grid-cols-4"
            />
          </div>
        )}
      </CardContent>
    </Card>
  );
};

export default AdvancedSearch;
