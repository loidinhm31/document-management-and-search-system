import { AxiosError, AxiosResponse } from "axios";
import i18next from "i18next";

import { toast } from "@/hooks/use-toast";

interface ResponseMessages {
  successMessage?: string;
  errorMessage?: string;
}

export class BaseService {
  protected async handleApiResponse<T>(promise: Promise<AxiosResponse<T>>, messages?: ResponseMessages) {
    try {
      const response = await promise;
      const message = messages?.successMessage;
      if (message) {
        this.showSuccess(message);
      }
      return response;
    } catch (error) {
      this.handleApiError(error, messages?.errorMessage);
      throw error;
    }
  }

  protected showSuccess(message: string) {
    toast({
      title: i18next.t("common.success"),
      description: message,
      variant: "success",
    });
  }

  protected handleApiError(error: AxiosError<any>, defaultMessage?: string) {
    const status = error.response?.status;
    let description = error.response?.data?.error?.message || defaultMessage;

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
      case 500:
        description = i18next.t("api.error.serverError");
        break;
      default:
        if (error.message === "Network Error") {
          description = i18next.t("api.error.networkError");
        } else {
          description = description || i18next.t("api.error.unknown");
        }
    }

    toast({
      title: i18next.t("common.error"),
      description,
      variant: "destructive",
    });
  }
}
