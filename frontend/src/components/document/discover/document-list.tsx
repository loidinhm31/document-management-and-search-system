import { ArrowDown, ArrowUp, BookOpen, Calendar, Download, Eye, Filter, Heart, Loader2, Search } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import MultiValueDisplay from "@/components/common/multi-value-display";
import { DocumentListActions } from "@/components/document/discover/document-list-actions";
import { HighlightCell } from "@/components/document/discover/highlight-cell";
import SearchSuggestions from "@/components/document/discover/search-suggestions";
import DocumentFilter from "@/components/document/document-filter";
import DocumentViewerDialog from "@/components/document/viewers/viewer-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { cn, getMasterDataTranslation } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/master-data-slice";
import {
  fetchRecommendedDocuments,
  fetchSearchDocuments,
  resetFilters,
  selectSearchLoading,
  selectSearchResults,
  selectSearchState,
  setCategories,
  setCourseCodes,
  setLevel,
  setMajors,
  setPage,
  setPageSize,
  setSearchTerm,
  setSort,
  setTags,
  toggleFavoriteOnly,
} from "@/store/slices/search-slice";
import { DocumentInformation } from "@/types/document";
import { MasterDataType } from "@/types/master-data";

interface SortableColumn {
  field: string;
  label: string;
  sortable: boolean;
}

export const DocumentList = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { toast } = useToast();
  const { currentUser } = useAuth();

  const loading = useAppSelector(selectSearchLoading);
  const documents = useAppSelector(selectSearchResults);

  const {
    selectedSort,
    selectedMajors,
    selectedCourseCodes,
    selectedLevel,
    selectedCategories,
    selectedTags,
    totalPages,
    currentPage,
    pageSize,
    favoriteOnly,
    isSearchMode,
    searchTerm,
  } = useAppSelector(selectSearchState);

  const { majors, levels, categories } = useAppSelector(selectMasterData);

  const pageSizeOptions = [10, 20, 50, 100];

  const [showPreview, setShowPreview] = useState(false);
  const [selectedDoc, setSelectedDoc] = React.useState(null);
  const [showAdvanced, setShowAdvanced] = React.useState(false);

  const columns: SortableColumn[] = [
    { field: "filename", label: t("document.discover.headers.name"), sortable: !!isSearchMode },
    { field: "majors", label: t("document.discover.headers.majors"), sortable: false },
    { field: "courseCodes", label: t("document.discover.headers.courses"), sortable: false },
    { field: "courseLevel", label: t("document.discover.headers.level"), sortable: !!isSearchMode },
    { field: "categories", label: t("document.discover.headers.categories"), sortable: false },
    { field: "tags", label: t("document.discover.headers.tags"), sortable: false },
    // Only show matches column in search mode
    ...(isSearchMode ? [{ field: "highlights", label: t("document.discover.headers.matches"), sortable: false }] : []),
    { field: "createdAt", label: t("document.discover.headers.created"), sortable: !!isSearchMode },
    { field: "actions", label: t("document.discover.headers.actions"), sortable: false },
  ];

  // Extract current sort field and direction from selectedSort
  const getCurrentSort = () => {
    if (!selectedSort) return { field: "", direction: "" };
    const [field, direction] = selectedSort.split(",");
    return { field, direction };
  };

  const handleSort = (field: string) => {
    const currentSort = getCurrentSort();
    let newDirection = "asc";

    if (currentSort.field === field) {
      newDirection = currentSort.direction === "asc" ? "desc" : "asc";
    }

    dispatch(setSort(`${field},${newDirection}`));
    dispatch(fetchSearchDocuments());
  };

  const renderSortIcon = (field: string) => {
    const { field: currentField, direction } = getCurrentSort();
    if (field !== currentField) return null;

    return direction === "asc" ? <ArrowUp className="ml-1 h-4 w-4" /> : <ArrowDown className="ml-1 h-4 w-4" />;
  };

  // Initial fetch master data
  useEffect(() => {
    // Only fetch master data if not already loaded
    if (majors.length === 0 || levels.length === 0 || categories.length === 0) {
      dispatch(fetchMasterData());
    }
  }, [dispatch, majors.length, levels.length, categories.length]);

  // Initial fetch
  useEffect(() => {
    if (
      searchTerm !== "" ||
      selectedMajors.length > 0 ||
      selectedCourseCodes.length > 0 ||
      selectedCategories.length > 0 ||
      selectedLevel !== "" ||
      selectedTags.length > 0 ||
      favoriteOnly
    ) {
      dispatch(fetchSearchDocuments());
    } else {
      dispatch(fetchRecommendedDocuments());
    }
  }, []);

  const handleSearch = () => {
    if (
      searchTerm !== "" ||
      selectedMajors.length > 0 ||
      selectedCourseCodes.length > 0 ||
      selectedCategories.length > 0 ||
      selectedLevel !== "" ||
      selectedTags.length > 0 ||
      favoriteOnly
    ) {
      dispatch(setPage(0));
      dispatch(fetchSearchDocuments());
    } else {
      dispatch(fetchRecommendedDocuments());
    }
  };

  const handlePageSizeChange = (newSize: string) => {
    dispatch(setPageSize(parseInt(newSize)));
    dispatch(setPage(0)); // Reset to first page when changing page size
    if (isSearchMode) {
      // dispatch(fetchSearchDocuments());
    } else {
      dispatch(fetchRecommendedDocuments());
    }
  };

  const handleDownload = async (id: string, filename: string) => {
    try {
      const response = await documentService.downloadDocument({ id, action: "download", history: true });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.info("err:", error);
      toast({
        title: t("common.error"),
        description: t("document.viewer.error.download"),
        variant: "destructive",
      });
    }
  };

  const handleReset = () => {
    dispatch(resetFilters());
    dispatch(fetchRecommendedDocuments());
  };

  const handlePageChange = (newPage: number) => {
    dispatch(setPage(newPage));
    if (isSearchMode) {
      dispatch(fetchSearchDocuments());
    } else {
      dispatch(fetchRecommendedDocuments());
    }
  };

  const formatDate = (dateString: string | Date) => {
    const date = typeof dateString === "string" ? new Date(dateString) : dateString;
    return date.toLocaleString();
  };

  const getActiveFilterCount = () => {
    let count = 0;
    if (selectedMajors.length > 0) count++;
    if (selectedCourseCodes.length > 0) count++;
    if (selectedLevel) count++;
    if (selectedCategories.length > 0) count++;
    if (selectedTags.length > 0) count++;
    if (favoriteOnly) count++;
    return count;
  };

  const handleToggleFavorites = () => {
    dispatch(toggleFavoriteOnly());
    dispatch(setPage(0));
    if (favoriteOnly) {
      // Turning it off, go back to normal behavior
      if (
        !searchTerm.trim() &&
        selectedMajors.includes("all") &&
        selectedLevel === "all" &&
        selectedCategories.includes("all") &&
        selectedTags.length === 0
      ) {
        dispatch(fetchRecommendedDocuments());
      } else {
        dispatch(fetchSearchDocuments());
      }
    } else {
      // Turning it on, fetch favorites
      dispatch(fetchSearchDocuments());
    }
  };

  const handlePreview = (doc: DocumentInformation) => {
    setSelectedDoc(doc);
    setShowPreview(!showPreview);
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <BookOpen className="h-5 w-5" />
          <div>
            <CardTitle className="flex items-center gap-2">{t("document.discover.title")}</CardTitle>
            <CardDescription>{t("document.discover.description")}</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {/* Search Section */}
          <div className="flex flex-col gap-4 lg:flex-row">
            {/* Search Input and Button */}
            <div className="flex flex-1 flex-col gap-2 sm:flex-row">
              <div className="flex-1">
                {!favoriteOnly && (
                  <SearchSuggestions
                    onSearch={handleSearch}
                    className="max-w-none"
                    placeholder={t("document.search.placeholder")}
                  />
                )}
                {favoriteOnly && (
                  <div className="relative rounded-lg border shadow-sm">
                    <div className="flex items-center px-3 gap-2">
                      <Search className="h-4 w-4 shrink-0 opacity-50" />
                      <input
                        value={searchTerm}
                        onChange={(e) => dispatch(setSearchTerm(e.target.value))}
                        className="flex h-9 w-full rounded-md bg-transparent py-3 text-sm outline-none"
                        placeholder={t("document.search.favorites.placeholder")}
                      />
                    </div>
                  </div>
                )}
              </div>
              <Button onClick={handleSearch} className="shrink-0">
                {t("document.commonSearch.apply")}
              </Button>
            </div>

            {/* Filter and Reset Buttons */}
            <div className="flex gap-2">
              {/* Favorite Button */}
              {!currentUser?.roles.includes("ROLE_ADMIN") && (
                <Button
                  variant={favoriteOnly ? "default" : "outline"}
                  onClick={handleToggleFavorites}
                  className="relative gap-2"
                >
                  <Heart className={`h-4 w-4 ${favoriteOnly ? "fill-current" : ""}`} />
                  <span className="hidden sm:inline">{t("document.commonSearch.favoritesOnly")}</span>
                </Button>
              )}

              <Button variant="outline" onClick={() => setShowAdvanced(!showAdvanced)} className="relative gap-2">
                <Filter className="h-4 w-4" />
                <span className="hidden sm:inline">{t("document.commonSearch.filters")}</span>
                {getActiveFilterCount() > 0 && (
                  <span className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full bg-primary text-xs text-primary-foreground">
                    {getActiveFilterCount()}
                  </span>
                )}
              </Button>

              <Button variant="outline" onClick={handleReset} className="sm:w-auto">
                {t("document.commonSearch.reset")}
              </Button>
            </div>
          </div>

          {/* Advanced Filters */}
          {showAdvanced && (
            <div className="space-y-4">
              <DocumentFilter
                majors={selectedMajors}
                onMajorsChange={(values) => dispatch(setMajors(values))}
                courseCodes={selectedCourseCodes}
                onCourseCodesChange={(values) => dispatch(setCourseCodes(values))}
                level={selectedLevel}
                onLevelChange={(value) => dispatch(setLevel(value))}
                categories={selectedCategories}
                onCategoriesChange={(values) => dispatch(setCategories(values))}
                tags={selectedTags}
                onTagsChange={(tags) => dispatch(setTags(tags))}
                className="grid-cols-1 sm:grid-cols-2 lg:grid-cols-4"
              />
            </div>
          )}

          {/* Results Section */}
          {loading ? (
            <div className="flex justify-center p-4">
              <Loader2 className="h-8 w-8 animate-spin" />
              <span className="ml-2">{t("document.search.loading")}</span>
            </div>
          ) : documents?.length > 0 ? (
            <>
              <div className="space-y-4 lg:hidden">
                {documents.map((doc) => (
                  <Card key={doc.id} className="overflow-hidden">
                    <CardHeader className="p-4">
                      <CardTitle className="text-base">
                        <Button
                          variant="link"
                          className="h-auto p-0 text-left"
                          onClick={() => navigate(`/discover/${doc.id}`)}
                        >
                          {doc.filename}
                        </Button>
                      </CardTitle>
                      <CardDescription>
                        <div className="flex items-center gap-2">
                          <Calendar className="h-4 w-4" />
                          {formatDate(doc.createdAt)}
                        </div>
                      </CardDescription>
                    </CardHeader>
                    <CardContent className="p-4 pt-0">
                      <div className="space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.majors")}:</span>
                          <span className="text-sm">
                            <MultiValueDisplay value={doc.majors} className="line-clamp-1" />
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.courses")}:</span>
                          <span className="text-sm">
                            <MultiValueDisplay value={doc.courseCodes} className="line-clamp-1" />
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.level")}:</span>
                          <span className="text-sm">
                            {getMasterDataTranslation(doc.courseLevel, MasterDataType.COURSE_LEVEL, { levels })}
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span className="text-sm font-medium">{t("document.discover.headers.categories")}:</span>
                          <span className="text-sm">
                            <MultiValueDisplay value={doc.categories} className="line-clamp-1" />
                          </span>
                        </div>
                        {doc.highlights && doc.highlights.length > 0 && (
                          <div className="mt-2">
                            <HighlightCell highlights={doc.highlights} />
                          </div>
                        )}
                        <div className="flex justify-end gap-2 pt-2">
                          <Button variant="outline" size="sm" onClick={() => handlePreview(doc)}>
                            <Eye className="h-4 w-4" />
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => handleDownload(doc.id, doc.filename)}>
                            <Download className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
              {/* Desktop Table View */}
              <div className="hidden lg:block">
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        {columns.map((column) => (
                          <TableHead
                            key={column.field}
                            className={cn(
                              column.sortable && "cursor-pointer select-none",
                              column.field === "actions" && "text-right",
                            )}
                            onClick={() => column.sortable && handleSort(column.field)}
                          >
                            <div className="flex items-center">
                              {column.label}
                              {column.sortable && renderSortIcon(column.field)}
                            </div>
                          </TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {documents.map((doc) => (
                        <TableRow key={doc.id}>
                          <TableCell className="font-medium truncate">
                            <Button
                              variant="link"
                              className="font-medium truncate p-0 h-auto text-left text-wrap"
                              onClick={() => navigate(`/discover/${doc.id}`)}
                            >
                              {doc.filename}
                            </Button>
                          </TableCell>
                          <TableCell className="truncate">
                            <MultiValueDisplay
                              value={doc.majors}
                              type={MasterDataType.MAJOR}
                              masterData={{ majors }}
                              className="line-clamp-1"
                            />
                          </TableCell>
                          <TableCell className="truncate">
                            <MultiValueDisplay value={doc.courseCodes} className="line-clamp-1" />
                          </TableCell>
                          <TableCell className="hidden md:table-cell">
                            {getMasterDataTranslation(doc.courseLevel, MasterDataType.COURSE_LEVEL, { levels })}
                          </TableCell>
                          <TableCell className="hidden lg:table-cell">
                            <MultiValueDisplay
                              value={doc.categories}
                              type={MasterDataType.DOCUMENT_CATEGORY}
                              masterData={{ categories }}
                              className="line-clamp-1"
                            />
                          </TableCell>
                          <TableCell className="hidden xl:table-cell">
                            <MultiValueDisplay value={doc.tags} />
                          </TableCell>
                          {isSearchMode && (
                            <TableCell>
                              <HighlightCell highlights={doc.highlights} />
                            </TableCell>
                          )}
                          <TableCell>
                            <div className="flex items-center gap-2">
                              <Calendar className="h-4 w-4 text-muted-foreground" />
                              {formatDate(doc.createdAt)}
                            </div>
                          </TableCell>
                          <TableCell className="text-right">
                            <DocumentListActions
                              onDownload={() => handleDownload(doc.id, doc.filename)}
                              onShowPreview={() => handlePreview(doc)}
                            />
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </div>
            </>
          ) : (
            <p className="flex justify-center">
              {favoriteOnly ? t("document.discover.emptyFavorites") : t("document.discover.empty")}
            </p>
          )}

          {/* Pagination */}
          <div className="mt-4 flex flex-col sm:flex-row justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-sm">{t("document.discover.pagination.pageSize")}</span>
              <Select
                value={pageSize.toString()}
                onValueChange={handlePageSizeChange}
                disabled={documents.length === 0}
              >
                <SelectTrigger className="w-[80px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {pageSizeOptions.map((size) => (
                    <SelectItem key={size} value={size.toString()}>
                      {size}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex justify-center gap-2">
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage - 1)}
                disabled={currentPage === 0 || documents.length === 0}
              >
                {t("document.discover.pagination.previous")}
              </Button>
              <span className={cn("flex items-center px-4", documents.length === 0 && "text-muted-foreground")}>
                {t("document.discover.pagination.pageInfo", {
                  current: documents.length > 0 ? currentPage + 1 : 0,
                  total: totalPages,
                })}
              </span>
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage + 1)}
                disabled={currentPage === totalPages - 1 || documents.length === 0}
              >
                {t("document.discover.pagination.next")}
              </Button>
            </div>
          </div>

          {/* Document Preview Dialog */}
          {showPreview && selectedDoc && (
            <DocumentViewerDialog
              open={showPreview}
              onOpenChange={setShowPreview}
              documentData={selectedDoc}
              documentId={selectedDoc.id}
              isVersion={false}
            />
          )}
        </div>
      </CardContent>
    </Card>
  );
};

export default DocumentList;
