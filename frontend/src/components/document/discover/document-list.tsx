import { ArrowDown, ArrowUp, Calendar, Download, Eye, Filter, Loader2, MoreHorizontal } from "lucide-react";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { HighlightCell } from "@/components/document/discover/highlight-cell";
import SearchSuggestions from "@/components/document/discover/search-suggestions";
import DocumentFilter from "@/components/document/my-document/document-filter";
import { DocumentViewer } from "@/components/document/viewers/document-viewer";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { cn, getMasterDataTranslation } from "@/lib/utils";
import { documentService } from "@/services/document.service";
import { useAppDispatch, useAppSelector } from "@/store/hook";
import { fetchMasterData, selectMasterData } from "@/store/slices/masterDataSlice";
import {
  fetchRecommendedDocuments,
  fetchSearchDocuments,
  resetFilters,
  selectSearchLoading,
  selectSearchResults,
  selectSearchState,
  setCategory,
  setLevel,
  setMajor,
  setPage,
  setSort,
  setTags
} from "@/store/slices/searchSlice";

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

  const loading = useAppSelector(selectSearchLoading);
  const documents = useAppSelector(selectSearchResults);

  const {
    selectedSort,
    selectedMajor,
    selectedLevel,
    selectedCategory,
    selectedTags,
    currentPage,
    totalPages
  } = useAppSelector(selectSearchState);

  const { majors, levels, categories } = useAppSelector(selectMasterData);
  const { searchTerm } = useAppSelector(selectSearchState);
  const { isSearchMode } = useAppSelector(selectSearchState);

  const [selectedDoc, setSelectedDoc] = React.useState(null);
  const [showAdvanced, setShowAdvanced] = React.useState(false);

  const columns: SortableColumn[] = [
    { field: "filename", label: t("document.discover.headers.name"), sortable: true },
    { field: "courseCode", label: t("document.discover.headers.course"), sortable: true },
    { field: "major", label: t("document.discover.headers.major"), sortable: true },
    { field: "courseLevel", label: t("document.discover.headers.level"), sortable: true },
    { field: "category", label: t("document.discover.headers.category"), sortable: true },
    { field: "tags", label: t("document.discover.headers.tags"), sortable: false },
    // Only show matches column in search mode
    ...(isSearchMode ? [{ field: "highlights", label: t("document.discover.headers.matches"), sortable: false }] : []),
    { field: "createdAt", label: t("document.discover.headers.created"), sortable: true },
    { field: "actions", label: t("document.discover.headers.actions"), sortable: false }
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

    return direction === "asc" ? (
      <ArrowUp className="ml-1 h-4 w-4" />
    ) : (
      <ArrowDown className="ml-1 h-4 w-4" />
    );
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
    if (searchTerm ||
      selectedMajor !== "all" ||
      selectedLevel !== "all" ||
      selectedCategory !== "all" ||
      selectedTags.length > 0) {
      dispatch(fetchSearchDocuments());
    } else {
      dispatch(fetchRecommendedDocuments());
    }
  }, []);

  const handleSearch = () => {
    if (!searchTerm.trim() &&
      selectedMajor === "all" &&
      selectedLevel === "all" &&
      selectedCategory === "all" &&
      selectedTags.length === 0) {
      dispatch(fetchRecommendedDocuments());
    } else {
      dispatch(setPage(0));
      dispatch(fetchSearchDocuments());
    }
  };

  const handleDownload = async (id: string, filename: string) => {
    try {
      const response = await documentService.downloadDocument(id);
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.viewer.error.download"),
        variant: "destructive"
      });
    }
  };

  const handleReset = () => {
    dispatch(resetFilters());
    dispatch(fetchRecommendedDocuments());
  };

  const handlePageChange = (newPage: number) => {
    dispatch(setPage(newPage));
    dispatch(fetchSearchDocuments());
  };

  const formatDate = (dateString: string | Date) => {
    const date = typeof dateString === "string" ? new Date(dateString) : dateString;
    return date.toLocaleString();
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
    <Card>
      <CardHeader>
        <CardTitle>{t("document.discover.title")}</CardTitle>
        <CardDescription>{t("document.discover.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          {/* Search Section */}
          <div className="flex flex-col gap-4 lg:flex-row">
            {/* Search Input and Button */}
            <div className="flex flex-1 flex-col gap-2 sm:flex-row">
              <div className="flex-1">
                <SearchSuggestions
                  onSearch={handleSearch}
                  className="max-w-none"
                  placeholder={t("document.search.placeholder")}
                />
              </div>
              <Button onClick={handleSearch} className="shrink-0">
                {t("document.commonSearch.apply")}
              </Button>
            </div>

            {/* Filter and Reset Buttons */}
            <div className="flex gap-2">
              <Button
                variant="outline"
                onClick={() => setShowAdvanced(!showAdvanced)}
                className="relative gap-2"
              >
                <Filter className="h-4 w-4" />
                <span className="hidden sm:inline">{t("document.commonSearch.filters")}</span>
                {getActiveFilterCount() > 0 && (
                  <span
                    className="absolute -right-2 -top-2 flex h-5 w-5 items-center justify-center rounded-full bg-primary text-xs text-primary-foreground">
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
                majorValue={selectedMajor}
                onMajorChange={(value) => dispatch(setMajor(value))}
                levelValue={selectedLevel}
                onLevelChange={(value) => dispatch(setLevel(value))}
                categoryValue={selectedCategory}
                onCategoryChange={(value) => dispatch(setCategory(value))}
                tagsValue={selectedTags}
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
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    {columns.map((column) => (
                      <TableHead
                        key={column.field}
                        className={cn(
                          column.sortable && "cursor-pointer select-none",
                          column.field === "actions" && "text-right"
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
                          className="font-medium truncate p-0 h-auto"
                          onClick={() => navigate(`/documents/${doc.id}`)}
                        >
                          {doc.filename}
                        </Button>
                      </TableCell>
                      <TableCell className="truncate">{doc.courseCode}</TableCell>
                      <TableCell className="truncate">
                        {getMasterDataTranslation(doc.major, "major", { majors })}
                      </TableCell>
                      <TableCell className="hidden md:table-cell">
                        {getMasterDataTranslation(doc.courseLevel, "level", { levels })}
                      </TableCell>
                      <TableCell className="hidden lg:table-cell">
                        {getMasterDataTranslation(doc.category, "category", { categories })}
                      </TableCell>
                      <TableCell className="hidden xl:table-cell">
                        <div className="flex flex-wrap gap-1">
                          {doc.tags?.map((tag, index) => (
                            <span
                              key={index}
                              className="inline-flex items-center rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20"
                            >
                              {tag}
                            </span>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell>
                        <HighlightCell highlights={doc.highlights} />
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <Calendar className="h-4 w-4 text-muted-foreground" />
                          {formatDate(doc.createdAt)}
                        </div>
                      </TableCell>
                      <TableCell className="text-right">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon">
                              <MoreHorizontal className="h-4 w-4" />
                              <span className="sr-only">Actions</span>
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => setSelectedDoc(doc)}>
                              <Eye className="mr-2 h-4 w-4" />
                              {t("document.actions.view")}
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => handleDownload(doc.id, doc.filename)}>
                              <Download className="mr-2 h-4 w-4" />
                              {t("document.actions.download")}
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-4 flex justify-center gap-2">
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage - 1)}
                disabled={currentPage === 0}
              >
                {t("document.discover.pagination.previous")}
              </Button>
              <span className="flex items-center px-4">
                {t("document.discover.pagination.pageInfo", {
                  current: currentPage + 1,
                  total: totalPages
                })}
              </span>
              <Button
                variant="outline"
                onClick={() => handlePageChange(currentPage + 1)}
                disabled={currentPage === totalPages - 1}
              >
                {t("document.discover.pagination.next")}
              </Button>
            </div>
          )}

          {/* Document Preview Dialog */}
          {selectedDoc && (
            <Dialog open={!!selectedDoc} onOpenChange={() => setSelectedDoc(null)}>
              <DialogContent className="max-w-4xl h-[80vh]">
                <DialogHeader>
                  <DialogTitle>{selectedDoc?.filename}</DialogTitle>
                </DialogHeader>
                <div className="flex-1 overflow-auto">
                  <DocumentViewer
                    documentId={selectedDoc?.id}
                    documentType={selectedDoc?.documentType}
                    mimeType={selectedDoc?.mimeType}
                    fileName={selectedDoc?.filename}
                  />
                </div>
              </DialogContent>
            </Dialog>
          )}
        </div>
      </CardContent>
    </Card>
  );
};

export default DocumentList;