import i18next from "i18next";

import { toast } from "@/hooks/use-toast";

import { BaseService } from "./base.service";

class ToastService extends BaseService {
  protected showSuccess(message?: string) {
    toast({
      title: i18next.t("common.success"),
      description: message,
      variant: "success",
    });
  }

  protected handleApiError(error: any) {
    const status = error.response?.status;
    let description = error.response?.data?.message;

    switch (status) {
      case 400:
        description = description || i18next.t("api.error.badRequest");
        break;
      case 401:
        description = i18next.t("api.error.unauthorized");
        break;
      case 403:
        description = i18next.t("api.error.forbidden");
        break;
      case 404:
        description = i18next.t("api.error.notFound");
        break;
      default:
        if (error.message === "Network Error") {
          description = i18next.t("api.error.networkError");
        } else {
          description = i18next.t("api.error.unknown");
        }
    }

    toast({
      title: i18next.t("common.error"),
      description,
      variant: "destructive",
    });
  }
}

export const toastService = new ToastService();
