import React, { useState } from 'react';
import { Filter, Search, SortAsc, SortDesc } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useTranslation } from "react-i18next";
import DocumentFilter from "@/components/document/my-document/document-filter";

const sortOptions = [
  { label: "Created Date (Newest)", value: "createdAt,desc" },
  { label: "Created Date (Oldest)", value: "createdAt,asc" },
  { label: "Name (A-Z)", value: "filename,asc" },
  { label: "Name (Z-A)", value: "filename,desc" },
  { label: "Size (Largest)", value: "fileSize,desc" },
  { label: "Size (Smallest)", value: "fileSize,asc" }
];

export interface SearchFilters {
  search?: string;
  major?: string;
  level?: string;
  category?: string;
  sort?: string;
  tags?: string[];
}

interface AdvancedSearchProps {
  onSearch: (filters: SearchFilters) => void;
}

export const AdvancedSearch = ({ onSearch }: AdvancedSearchProps) => {
  const { t } = useTranslation();
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Search states
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedSort, setSelectedSort] = useState(sortOptions[0].value);

  // Filter states
  const [selectedMajor, setSelectedMajor] = useState("all");
  const [selectedLevel, setSelectedLevel] = useState("all");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  const handleSearch = () => {
    onSearch({
      search: searchTerm,
      major: selectedMajor === "all" ? undefined : selectedMajor,
      level: selectedLevel === "all" ? undefined : selectedLevel,
      category: selectedCategory === "all" ? undefined : selectedCategory,
      sort: selectedSort,
      tags: selectedTags.length > 0 ? selectedTags : undefined
    });
  };

  const handleReset = () => {
    setSearchTerm("");
    setSelectedMajor("all");
    setSelectedLevel("all");
    setSelectedCategory("all");
    setSelectedSort(sortOptions[0].value);
    setSelectedTags([]);
    onSearch({});
  };

  const getActiveFilterCount = () => {
    let count = 0;
    if (selectedMajor !== "all") count++;
    if (selectedLevel !== "all") count++;
    if (selectedCategory !== "all") count++;
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
                    {option.value.endsWith('desc') ?
                      <SortDesc className="h-4 w-4" /> :
                      <SortAsc className="h-4 w-4" />
                    }
                    {option.label}
                  </span>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          {/* Advanced Filter Toggle */}
          <Button
            variant="outline"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="relative gap-2"
          >
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
              majorValue={selectedMajor}
              onMajorChange={setSelectedMajor}
              levelValue={selectedLevel}
              onLevelChange={setSelectedLevel}
              categoryValue={selectedCategory}
              onCategoryChange={setSelectedCategory}
              tagsValue={selectedTags}
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