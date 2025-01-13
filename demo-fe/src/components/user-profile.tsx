import { zodResolver } from "@hookform/resolvers/zod";
import { jwtDecode } from "jwt-decode";
import { Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import * as z from "zod";

import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { userService } from "@/services/user.service";

const formSchema = z.object({
  password: z
    .string()
    .min(6, "Password must be at least 6 characters")
    .regex(
      /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=]).*$/,
      "Password must contain at least one digit, lowercase, uppercase, and special character",
    ),
});

type FormValues = z.infer<typeof formSchema>;

export default function UserProfile() {
  const { t } = useTranslation();
  const { currentUser, token } = useAuth();
  const { toast } = useToast();
  const [loginSession, setLoginSession] = useState<string | null>(null);

  // 2FA states
  const [is2faEnabled, setIs2faEnabled] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [step, setStep] = useState(1);

  // Loading states
  const [loading, setLoading] = useState({
    page: true,
    form: false,
    twoFactor: false,
  });

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      password: "",
    },
  });

  // Fetch 2FA status on mount
  useEffect(() => {
    const fetch2FAStatus = async () => {
      try {
        console.log("currej", currentUser);
        const response = await userService.get2FAStatus(currentUser?.userId);
        if (response.data && typeof response.data.data === "boolean") {
          setIs2faEnabled(response.data.data);
        }
      } catch (_error) {
        toast({
          title: t("common.error"),
          description: t("profile.twoFactor.messages.statusError"),
          variant: "destructive",
        });
      } finally {
        setLoading((prev) => ({ ...prev, page: false }));
      }
    };

    if (currentUser) {
      fetch2FAStatus();
    }
  }, [t, currentUser]);

  // Set login session from token
  useEffect(() => {
    if (token) {
      const decodedToken = jwtDecode(token);
      if ("iat" in decodedToken) {
        const lastLogin = new Date(decodedToken.iat * 1000).toLocaleString();
        setLoginSession(lastLogin);
      }
    }
  }, [token]);

  // Enable 2FA
  const enable2FA = async () => {
    setLoading((prev) => ({ ...prev, twoFactor: true }));
    try {
      const response = await userService.enable2FA();
      const qrCode = response.data.data;
      if (typeof qrCode === "string") {
        setQrCodeUrl(qrCode);
        setStep(2);
      }
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("profile.twoFactor.messages.enableError"),
        variant: "destructive",
      });
    } finally {
      setLoading((prev) => ({ ...prev, twoFactor: false }));
    }
  };

  // Disable 2FA
  const disable2FA = async () => {
    setLoading((prev) => ({ ...prev, twoFactor: true }));
    try {
      await userService.disable2FA();
      setIs2faEnabled(false);
      setQrCodeUrl("");
      toast({
        title: t("common.success"),
        description: t("profile.twoFactor.messages.disableSuccess"),
      });
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("profile.twoFactor.messages.disableError"),
        variant: "destructive",
      });
    } finally {
      setLoading((prev) => ({ ...prev, twoFactor: false }));
    }
  };

  // Verify 2FA
  const verify2FA = async () => {
    if (!verificationCode) {
      toast({
        title: t("common.error"),
        description: t("profile.twoFactor.messages.verificationRequired"),
        variant: "destructive",
      });
      return;
    }

    setLoading((prev) => ({ ...prev, twoFactor: true }));
    try {
      await userService.verify2FA(verificationCode);
      setIs2faEnabled(true);
      setStep(1);
      toast({
        title: t("common.success"),
        description: t("profile.twoFactor.messages.enableSuccess"),
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("profile.twoFactor.messages.verifyError"),
        variant: "destructive",
      });
    } finally {
      setLoading((prev) => ({ ...prev, twoFactor: false }));
    }
  };

  // Update user credentials
  const onSubmit = async (data: FormValues) => {
    setLoading((prev) => ({ ...prev, form: true }));
    try {
      const updateData: any = { username: currentUser?.username };
      if (data.password) {
        updateData.password = data.password;
      }

      await userService.updateCredentials(updateData);
      form.reset({ ...data, password: "" });
      toast({
        title: t("common.success"),
        description: t("profile.updateProfile.messages.success"),
      });
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("profile.updateProfile.messages.error"),
        variant: "destructive",
      });
    } finally {
      setLoading((prev) => ({ ...prev, form: false }));
    }
  };

  if (loading.page) {
    return (
      <div className="flex h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="container mx-auto py-10">
      <div className="grid gap-6 md:grid-cols-2">
        {/* Profile Section */}
        <Card>
          <CardHeader className="space-y-1">
            <div className="flex items-center gap-4">
              <Avatar className="h-12 w-12">
                <AvatarFallback>{currentUser?.username?.[0]?.toUpperCase()}</AvatarFallback>
              </Avatar>
              <CardTitle>{t("profile.title")}</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <Accordion type="single" collapsible>
              <AccordionItem value="credentials">
                <AccordionTrigger>{t("profile.updateProfile.title")}</AccordionTrigger>
                <AccordionContent>
                  <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="username">{t("profile.updateProfile.fields.username")}</Label>
                      <Input value={currentUser?.email} />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="email">{t("profile.updateProfile.fields.email")}</Label>
                      <Input  disabled />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="password">{t("profile.updateProfile.fields.newPassword")}</Label>
                      <Input
                        type="password"
                        {...form.register("password")}
                        placeholder={t("profile.updateProfile.fields.passwordPlaceholder")}
                      />
                    </div>
                    <Button type="submit" disabled={loading.form}>
                      {loading.form && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                      {t("profile.updateProfile.actions.update")}
                    </Button>
                  </form>
                </AccordionContent>
              </AccordionItem>
            </Accordion>

            {loginSession && (
              <div className="rounded-lg border p-4">
                <h3 className="font-semibold">{t("profile.lastLogin.title")}</h3>
                <p className="text-sm text-muted-foreground">{loginSession}</p>
              </div>
            )}
          </CardContent>
        </Card>

        {/* 2FA Section */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>{t("profile.twoFactor.title")}</CardTitle>
              <div
                className={`rounded-full px-2 py-1 text-xs ${
                  is2faEnabled ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"
                }`}
              >
                {is2faEnabled ? t("profile.twoFactor.status.enabled") : t("profile.twoFactor.status.disabled")}
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">{t("profile.twoFactor.description")}</p>

            <Button
              onClick={is2faEnabled ? disable2FA : enable2FA}
              variant={is2faEnabled ? "destructive" : "default"}
              disabled={loading.twoFactor}
            >
              {loading.twoFactor && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {is2faEnabled ? t("profile.twoFactor.actions.disable") : t("profile.twoFactor.actions.enable")}
            </Button>

            {step === 2 && qrCodeUrl && (
              <div className="space-y-4">
                <div className="rounded-lg border p-4">
                  <img src={qrCodeUrl} alt="2FA QR Code" className="mx-auto" />
                </div>

                <div className="flex gap-2">
                  <Input
                    placeholder={t("profile.twoFactor.verificationCodePlaceholder")}
                    value={verificationCode}
                    onChange={(e) => setVerificationCode(e.target.value)}
                  />
                  <Button onClick={verify2FA} disabled={loading.twoFactor}>
                    {loading.twoFactor && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    {t("profile.twoFactor.actions.verify")}
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}