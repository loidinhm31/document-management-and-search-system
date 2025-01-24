import React, { useEffect, useState } from "react";
import { Loader2, Search, Share2, Users } from "lucide-react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/hooks/use-toast";
import { documentService } from "@/services/document.service";
import { userService } from "@/services/user.service";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Checkbox } from "@/components/ui/checkbox";

interface ShareDocumentDialogProps {
  documentId: string;
  documentName: string;
  isShared: boolean;
  onShareToggle: (isShared: boolean) => void;
}

export default function ShareDocumentDialog({
                                              documentId,
                                              documentName,
                                              isShared,
                                              onShareToggle,
                                            }: ShareDocumentDialogProps) {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [users, setUsers] = useState<any[]>([]);
  const [selectedUsers, setSelectedUsers] = useState<string[]>([]);
  const [isPublic, setIsPublic] = useState(isShared);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) {
      fetchShareSettings();
      if (searchQuery) {
        searchUsers();
      }
    }
  }, [open, searchQuery]);

  const fetchShareSettings = async () => {
    try {
      const response = await documentService.getShareSettings(documentId);
      const { isPublic, sharedWith = [] } = response.data;
      setIsPublic(isPublic);
      setSelectedUsers(sharedWith || []);
    } catch (error) {
      console.error("Error fetching share settings:", error);
    }
  };

  const searchUsers = async () => {
    setLoading(true);
    try {
      const response = await userService.searchUsers(searchQuery);
      setUsers(response.data);
    } catch (error) {
      console.error("Error searching users:", error);
    } finally {
      setLoading(false);
    }
  };

  const handleSharingToggle = async () => {
    setIsUpdating(true);
    try {
      await documentService.updateShareSettings(documentId, {
        isPublic: !isPublic,
        sharedWith: selectedUsers
      });
      setIsPublic(!isPublic);
      onShareToggle(!isPublic);
      toast({
        title: t("common.success"),
        description: !isPublic
          ? t("document.myDocuments.share.enableSuccess")
          : t("document.myDocuments.share.disableSuccess"),
        variant: "success"
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.share.error"),
        variant: "destructive"
      });
    } finally {
      setIsUpdating(false);
    }
  };

  const toggleUserSelection = (userId: string) => {
    setSelectedUsers(prev => {
      const currentSelected = prev || [];
      const isSelected = currentSelected.includes(userId);
      if (isSelected) {
        return currentSelected.filter(id => id !== userId);
      } else {
        return [...currentSelected, userId];
      }
    });
  };

  const handleUpdateSharing = async () => {
    setIsUpdating(true);
    try {
      await documentService.updateShareSettings(documentId, {
        isPublic,
        sharedWith: selectedUsers
      });
      toast({
        title: t("common.success"),
        description: t("document.myDocuments.share.updateSuccess"),
        variant: "success"
      });
      setOpen(false);
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("document.share.error"),
        variant: "destructive"
      });
    } finally {
      setIsUpdating(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="w-full"
          onClick={(e) => {
            e.stopPropagation();
          }}
        >
          <Share2 className="mr-2 h-4 w-4" />
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t("document.myDocuments.share.title")}</DialogTitle>
          <DialogDescription>
            {t("document.myDocuments.share.description", { name: documentName })}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {/* Public Sharing Toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <h4 className="text-sm font-medium">
                {t("document.myDocuments.share.toggle.title")}
              </h4>
              <p className="text-sm text-muted-foreground">
                {t("document.myDocuments.share.toggle.description")}
              </p>
            </div>
            <Switch
              checked={isPublic}
              disabled={isUpdating}
              onCheckedChange={handleSharingToggle}
            />
          </div>

          <Separator />

          {/* User Search and Selection */}
          <div className="space-y-4">
            <div className="space-y-2">
              <h4 className="text-sm font-medium flex items-center gap-2">
                <Users className="h-4 w-4" />
                {t("document.myDocuments.share.specific.title")}
              </h4>
              <div className="relative">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder={t("document.myDocuments.share.specific.searchPlaceholder")}
                  className="pl-8"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
              </div>
            </div>

            <ScrollArea className="h-[200px] rounded-md border">
              {loading ? (
                <div className="flex h-full items-center justify-center">
                  <Loader2 className="h-6 w-6 animate-spin" />
                </div>
              ) : (
                <div className="p-4 space-y-2">
                  {users.map((user) => (
                    <div
                      key={user.userId}
                      className="flex items-center space-x-2 rounded-lg p-2 hover:bg-accent"
                    >
                      <Checkbox
                        checked={selectedUsers.includes(user.userId)}
                        onCheckedChange={() => toggleUserSelection(user.userId)}
                      />
                      <Avatar className="h-8 w-8">
                        <AvatarFallback>
                          {user.username[0].toUpperCase()}
                        </AvatarFallback>
                      </Avatar>
                      <div className="space-y-1">
                        <p className="text-sm font-medium leading-none">
                          {user.username}
                        </p>
                        <p className="text-sm text-muted-foreground">
                          {user.email}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </ScrollArea>
          </div>

          <Button
            onClick={handleUpdateSharing}
            disabled={isUpdating}
            className="w-full"
          >
            {isUpdating && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("document.myDocuments.share.actions.update")}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}