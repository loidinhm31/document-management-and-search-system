import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import * as z from "zod";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useToast } from "@/hooks/use-toast";
import { authService } from "@/services/auth.service";

const MAX_RETRY_ATTEMPTS = 5;

const twoFactorSchema = z.object({
  code: z.string().min(6, "Code must be 6 digits").max(6),
});

type TwoFactorFormValues = z.infer<typeof twoFactorSchema>;

interface TwoFactorFormProps {
  username: string;
  onSuccess: () => void;
}

export const TwoFactorForm = ({ username, onSuccess }: TwoFactorFormProps) => {
  const { t } = useTranslation();
  const { toast } = useToast();
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const [remainingAttempts, setRemainingAttempts] = useState(MAX_RETRY_ATTEMPTS);

  const form = useForm<TwoFactorFormValues>({
    resolver: zodResolver(twoFactorSchema),
    defaultValues: {
      code: "",
    },
  });

  const onSubmit = async (data: TwoFactorFormValues) => {
    setIsLoading(true);
    try {
      const resposne = await authService.verify2FA(username, data.code);
      if (resposne.data?.success) {
        onSuccess();
      } else {
        const newRetryCount = retryCount + 1;
        const remaining = MAX_RETRY_ATTEMPTS - newRetryCount;

        setRetryCount(newRetryCount);
        setRemainingAttempts(remaining);

        if (newRetryCount >= MAX_RETRY_ATTEMPTS) {
          toast({
            title: t("common.error"),
            description: t("auth.login.2fa.maxAttemptsReached"),
            variant: "destructive",
          });
          navigate("/login");
          window.location.reload();
          return;
        }

        toast({
          title: t("common.error"),
          description: t("auth.login.2fa.remainingAttempts", { count: remaining }),
          variant: "warning",
        });

        // Clear the form input
        form.reset();
      }
    } catch (error) {
      console.log("Error:", error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Card className="w-full max-w-md">
      <CardHeader className="text-center">
        <CardTitle>{t("auth.login.2fa.title")}</CardTitle>
        <CardDescription>
          {t("auth.login.2fa.description")}
          {retryCount > 0 && (
            <div className="mt-2 text-sm text-muted-foreground">
              {t("auth.login.2fa.attemptsRemaining", { count: remainingAttempts })}
            </div>
          )}
        </CardDescription>
      </CardHeader>

      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
            <FormField
              control={form.control}
              name="code"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("auth.login.form.verificationCode.label")}</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      type="text"
                      placeholder={t("auth.login.form.verificationCode.placeholder")}
                      maxLength={6}
                      disabled={isLoading}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {t("auth.login.buttons.verify")}
            </Button>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
};