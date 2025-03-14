import { AlertCircle, KeyRound, Loader2 } from "lucide-react";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { adminService } from "@/services/admin.service";

interface RevokeTokensButtonProps {
  userId: string;
  onSuccess?: () => void;
}

export default function RevokeTokensButton({ userId, onSuccess }: RevokeTokensButtonProps) {
  const { t } = useTranslation();
  const { toast } = useToast();
  const [showDialog, setShowDialog] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleRevokeTokens = async () => {
    setLoading(true);
    try {
      await adminService.updateStatus(userId, {
        credentialsExpired: true,
      });

      toast({
        title: t("common.success"),
        description: t("admin.users.actions.revokeTokens.success", "Tokens successfully revoked"),
        variant: "success",
      });

      setShowDialog(false);
      if (onSuccess) onSuccess();
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.users.actions.revokeTokens.error", "Failed to revoke tokens"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        className="text-red-500 border-red-200 hover:bg-red-50 hover:text-red-600"
        onClick={() => setShowDialog(true)}
      >
        <KeyRound className="h-4 w-4 mr-2" />
        {t("admin.users.actions.revokeTokens.button", "Revoke Tokens")}
      </Button>

      <Dialog open={showDialog} onOpenChange={setShowDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <AlertCircle className="h-5 w-5 text-destructive" />
              {t("admin.users.actions.revokeTokens.title", "Revoke Refresh Tokens")}
            </DialogTitle>
            <DialogDescription>
              {t(
                "admin.users.actions.revokeTokens.description",
                "This action will invalidate all refresh tokens for this user. The user will need to log in again on all devices. This cannot be undone."
              )}
            </DialogDescription>
          </DialogHeader>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setShowDialog(false)}
              disabled={loading}
            >
              {t("common.cancel")}
            </Button>
            <Button
              variant="destructive"
              onClick={handleRevokeTokens}
              disabled={loading}
            >
              {loading ? (
                <span className="flex items-center gap-2">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  {t("admin.users.actions.revokeTokens.processing", "Revoking...")}
                </span>
              ) : (
                t("admin.users.actions.revokeTokens.confirm", "Revoke Tokens")
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}