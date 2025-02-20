import { Filter, Search } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import DocumentFilter from "@/components/document/document-filter";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";


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

  // Filter states
  const [selectedMajor, setSelectedMajor] = useState("all");
  const [selectedCourseCode, setSelectedCourseCode] = useState("all");
  const [selectedLevel, setSelectedLevel] = useState("all");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  const handleSearch = () => {
    onSearch({
      search: searchTerm,
      major: selectedMajor === "all" ? undefined : selectedMajor,
      level: selectedLevel === "all" ? undefined : selectedLevel,
      category: selectedCategory === "all" ? undefined : selectedCategory,
      tags: selectedTags.length > 0 ? selectedTags : undefined
    });
  };

  const handleReset = () => {
    setSearchTerm("");
    setSelectedMajor("all");
    setSelectedLevel("all");
    setSelectedCategory("all");
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

          {/* Advanced Filter Toggle */}
          <Button
            variant="outline"
            onClick={() => setShowAdvanced(!showAdvanced)}
            className="relative gap-2"
          >
            <Filter className="h-4 w-4" />
            {t("document.commonSearch.filters")}
            {getActiveFilterCount() > 0 && (
              <span
                className="absolute -top-2 -right-2 h-5 w-5 rounded-full bg-primary text-xs flex items-center justify-center text-primary-foreground">
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
              courseCodeValue={selectedCourseCode}
              onCourseCodeChange={setSelectedCourseCode}
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