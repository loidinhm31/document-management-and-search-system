import { AxiosError } from "axios";
import { useTranslation } from "react-i18next";

import { useToast } from "@/hooks/use-toast";

interface ApiError {
  status: number;
  message?: string;
}

export const useApiResponseHandler = () => {
  const { t } = useTranslation();
  const { toast } = useToast();

  const handleSuccess = (message?: string) => {
    toast({
      title: t("common.success"),
      description: message,
      variant: "success",
    });
  };

  const handleError = (error: AxiosError<ApiError>) => {
    const status = error.response?.status;
    const customMessage = error.response?.data?.message;

    let title = t("common.error");
    let description = customMessage;

    switch (status) {
      case 400:
        description = description || t("api.error.badRequest");
        break;
      case 401:
        description = t("api.error.unauthorized");
        // Optionally handle token expiration and logout
        break;
      case 403:
        description = t("api.error.forbidden");
        break;
      case 404:
        description = t("api.error.notFound");
        break;
      case 500:
        description = t("api.error.serverError");
        break;
      default:
        if (error.message === "Network Error") {
          description = t("api.error.networkError");
        } else {
          description = t("api.error.unknown");
        }
    }

    toast({
      title,
      description,
      variant: "destructive",
    });
  };

  return {
    handleSuccess,
    handleError,
  };
};