import { Loader2, Plus, Search } from "lucide-react";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import MasterDataDialog from "@/components/admin/master-data/master-data-dialog";
import { DeleteDialog } from "@/components/common/delete-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/hooks/use-toast";
import { masterDataService } from "@/services/master-data.service";
import { MasterData, MasterDataType } from "@/types/master-data";

export default function MasterDataManagement() {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedType, setSelectedType] = useState<MasterDataType>(MasterDataType.MAJOR);
  const [masterData, setMasterData] = useState<MasterData[]>([]);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [selectedItem, setSelectedItem] = useState<MasterData | null>(null);
  const [showEditDialog, setShowEditDialog] = useState(false);

  const [selectedParent, setSelectedParent] = useState<string>("all");
  const [parentOptions, setParentOptions] = useState<MasterData[]>([]);
  const [loadingParents, setLoadingParents] = useState(false);

  // Map for looking up parent names
  const [parentMap, setParentMap] = useState<Record<string, MasterData>>({});

  useEffect(() => {
    if (selectedType === MasterDataType.COURSE_CODE) {
      loadParentOptions();
    } else {
      setSelectedParent("all");
      setParentOptions([]);
    }
  }, [selectedType]);

  const loadParentOptions = async () => {
    setLoadingParents(true);
    try {
      const response = await masterDataService.getAllByType(MasterDataType.MAJOR);
      setParentOptions(response.data);

      // Build a lookup map for parent names
      const map: Record<string, MasterData> = {};
      response.data.forEach(item => {
        if (item.id) {
          map[item.id] = item;
        }
      });
      setParentMap(map);
    } catch (error) {
      console.info("Error loading parent options:", error);
      toast({
        title: t("common.error"),
        description: t("masterData.fetchError"),
        variant: "destructive"
      });
    } finally {
      setLoadingParents(false);
    }
  };

  const fetchMasterData = useCallback(async () => {
    setLoading(true);
    try {
      let response;
      if (selectedType === MasterDataType.COURSE_CODE && selectedParent && selectedParent !== "all") {
        // Fetch course codes for a specific major
        response = await masterDataService.getAllByTypeAndParentId(selectedType, selectedParent);
      } else {
        // Fetch all items of the selected type
        response = await masterDataService.getAllByType(selectedType);
      }
      setMasterData(response.data);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("masterData.fetchError"),
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  }, [selectedType, selectedParent, toast, t]);

  useEffect(() => {
    fetchMasterData();
  }, [fetchMasterData]);

  const handleTypeChange = (value: MasterDataType) => {
    setSelectedType(value);
    setSelectedParent("all");  // Reset parent filter when type changes
  };

  const handleParentChange = (value: string) => {
    setSelectedParent(value);
  };

  const handleSearch = async () => {
    setLoading(true);
    try {
      if (searchQuery.trim()) {
        const response = await masterDataService.searchByText(searchQuery);
        // Filter by type if needed
        const filtered = selectedType
          ? response.data.filter(item => item.type === selectedType)
          : response.data;
        setMasterData(filtered);
      } else {
        fetchMasterData();
      }
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.masterData.searchError"),
        variant: "destructive"
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedItem) return;

    try {
      await masterDataService.deleteById(selectedItem.id);
      toast({
        title: t("common.success"),
        description: t("admin.masterData.deleteSuccess"),
        variant: "success"
      });
      fetchMasterData();
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.masterData.deleteError"),
        variant: "destructive"
      });
    } finally {
      setShowDeleteDialog(false);
      setSelectedItem(null);
    }
  };

  const handleStatusChange = async (item: MasterData) => {
    try {
      const updatedItem = { ...item, active: !item.active };
      await masterDataService.save(updatedItem);
      fetchMasterData();
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.masterData.updateError"),
        variant: "destructive"
      });
    }
  };

  // Get parent display text
  const getParentDisplay = (parentId?: string): string => {
    if (!parentId) return "-";
    const parent = parentMap[parentId];
    return parent ? `${parent.translations.en}` : parentId;
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("admin.masterData.title")}</CardTitle>
        <CardDescription>{t("admin.masterData.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col gap-4">
          {/* Controls */}
          <div className="flex flex-wrap gap-4">
            <Select value={selectedType} onValueChange={handleTypeChange}>
              <SelectTrigger className="w-[200px]">
                <SelectValue placeholder={t("admin.masterData.selectType")} />
              </SelectTrigger>
              <SelectContent>
                {Object.values(MasterDataType).map((type) => (
                  <SelectItem key={type} value={type}>
                    {t(`admin.masterData.types.${type.toLowerCase()}`)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            {/* Parent filter - only show for COURSE_CODE */}
            {selectedType === MasterDataType.COURSE_CODE && (
              <Select
                value={selectedParent}
                onValueChange={handleParentChange}
                disabled={loadingParents}
              >
                <SelectTrigger className="w-[200px]">
                  <SelectValue placeholder={t("admin.masterData.selectParent")} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">
                    {t("admin.masterData.allParents")}
                  </SelectItem>
                  {parentOptions.map((parent) => (
                    <SelectItem key={parent.id} value={parent.id}>
                      {parent.translations.en})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            <div className="flex flex-1 gap-2">
              <Input
                placeholder={t("admin.masterData.searchPlaceholder")}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="max-w-xs"
              />
              <Button onClick={handleSearch}>
                <Search className="mr-2 h-4 w-4" />
                {t("admin.masterData.search")}
              </Button>
            </div>

            <Button onClick={() => setShowEditDialog(true)}>
              <Plus className="mr-2 h-4 w-4" />
              {t("admin.masterData.add")}
            </Button>
          </div>

          {/* Table */}
          {loading ? (
            <div className="flex h-[400px] items-center justify-center">
              <Loader2 className="h-8 w-8 animate-spin" />
            </div>
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("admin.masterData.headers.code")}</TableHead>
                    <TableHead>{t("admin.masterData.headers.nameEn")}</TableHead>
                    <TableHead>{t("admin.masterData.headers.nameVi")}</TableHead>
                    <TableHead>{t("admin.masterData.headers.type")}</TableHead>
                    {/* Add parent column */}
                    {selectedType === MasterDataType.COURSE_CODE && (
                      <TableHead>{t("admin.masterData.headers.parent")}</TableHead>
                    )}
                    <TableHead>{t("admin.masterData.headers.status")}</TableHead>
                    <TableHead className="text-right">
                      {t("admin.masterData.headers.actions")}
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {masterData.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.code}</TableCell>
                      <TableCell>{item.translations.en}</TableCell>
                      <TableCell>{item.translations.vi}</TableCell>
                      <TableCell>
                        {t(`admin.masterData.types.${item.type.toLowerCase()}`)}
                      </TableCell>
                      {/* Display parent for COURSE_CODE */}
                      {selectedType === MasterDataType.COURSE_CODE && (
                        <TableCell>{getParentDisplay(item.parentId)}</TableCell>
                      )}
                      <TableCell>
                        <Switch
                          checked={item.active}
                          onCheckedChange={() => handleStatusChange(item)}
                        />
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              setSelectedItem(item);
                              setShowEditDialog(true);
                            }}
                          >
                            {t("admin.masterData.actions.edit")}
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => {
                              setSelectedItem(item);
                              setShowDeleteDialog(true);
                            }}
                          >
                            {t("admin.masterData.actions.delete")}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </div>

        <MasterDataDialog
          open={showEditDialog}
          onOpenChange={setShowEditDialog}
          type={selectedType}
          data={selectedItem}
          onSuccess={() => {
            setShowEditDialog(false);
            setSelectedItem(null);
            fetchMasterData();
          }}
          onClose={() => {
            setSelectedItem(null);
          }}
        />

        <DeleteDialog
          open={showDeleteDialog}
          onOpenChange={setShowDeleteDialog}
          onConfirm={handleDelete}
          title={t("admin.masterData.delete.title")}
          description={t("admin.masterData.delete.description", {
            name: selectedItem?.translations.en
          })}
        />
      </CardContent>
    </Card>
  );
}