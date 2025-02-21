import { AlertTriangle } from "lucide-react";
import React from "react";
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

interface Disable2FADialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  loading?: boolean;
}

const Disable2FADialog: React.FC<Disable2FADialogProps> = ({
                                                             open,
                                                             onOpenChange,
                                                             onConfirm,
                                                             loading = false,
                                                           }) => {
  const { t } = useTranslation();

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-destructive" />
            {t("profile.twoFactor.disable.title")}
          </DialogTitle>
          <DialogDescription>
            {t("profile.twoFactor.disable.description")}
          </DialogDescription>
        </DialogHeader>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={loading}
          >
            {t("common.cancel")}
          </Button>
          <Button
            variant="destructive"
            onClick={onConfirm}
            disabled={loading}
          >
            {t("profile.twoFactor.actions.disable")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default Disable2FADialog;