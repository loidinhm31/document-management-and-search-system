import React, { useState } from 'react';
import { useTranslation } from "react-i18next";
import { Filter, Search, SortAsc, SortDesc } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import TagInput from "@/components/tag-input";

const courseTypes = [
  { label: "All", value: "all" },
  { label: "Fundamental", value: "FUNDAMENTAL" },
  { label: "Intermediate", value: "INTERMEDIATE" },
  { label: "Advanced", value: "ADVANCED" },
  { label: "Specialized", value: "SPECIALIZED" }
];

const majors = [
  { label: "All", value: "all" },
  { label: "Software Engineering", value: "SOFTWARE_ENGINEERING" },
  { label: "Artificial Intelligence", value: "ARTIFICIAL_INTELLIGENCE" },
  { label: "Information Security", value: "INFORMATION_SECURITY" },
  { label: "Internet of Things", value: "IOT" }
];

const categories = [
  { label: "All", value: "all" },
  { label: "Lecture", value: "LECTURE" },
  { label: "Exercise", value: "EXERCISE" },
  { label: "Exam", value: "EXAM" },
  { label: "Reference", value: "REFERENCE" },
  { label: "Lab", value: "LAB" },
  { label: "Project", value: "PROJECT" }
];

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

export default function AdvancedSearch({ onSearch }: AdvancedSearchProps) {
  const { t } = useTranslation();
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedMajor, setSelectedMajor] = useState("all");
  const [selectedLevel, setSelectedLevel] = useState("all");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [selectedSort, setSelectedSort] = useState(sortOptions[0].value);
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

  return (
    <Card className="mb-4">
      <CardContent className="pt-6">
        <div className="flex flex-col gap-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {/* Search Input */}
            <Input
              placeholder={t("document.myDocuments.search.advancedSearch.searchPlaceholder")}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />

            {/* Major Filter */}
            <Select value={selectedMajor} onValueChange={setSelectedMajor}>
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
            <Select value={selectedLevel} onValueChange={setSelectedLevel}>
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
            <Select value={selectedCategory} onValueChange={setSelectedCategory}>
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

            {/* Sort Dropdown */}
            <Select value={selectedSort} onValueChange={setSelectedSort}>
              <SelectTrigger>
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

            {/* Tag Input */}
            <div className="md:col-span-2">
              <TagInput
                value={selectedTags}
                onChange={setSelectedTags}
                placeholder={t("document.myDocuments.search.advancedSearch.tagsPlaceholder")}
              />
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex justify-start gap-2">
            <Button variant="outline" onClick={handleReset}>
              {t("document.myDocuments.search.advancedSearch.reset")}
            </Button>
            <Button onClick={handleSearch} className="gap-2">
              <Search className="h-4 w-4" />
              {t("document.myDocuments.search.advancedSearch.apply")}
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}