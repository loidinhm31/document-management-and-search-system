import { Separator } from "@radix-ui/react-dropdown-menu";
import { jwtDecode } from "jwt-decode";
import { Loader2 } from "lucide-react";
import moment from "moment-timezone";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import Disable2FADialog from "@/components/auth/disable-2fa-dialog";
import PasswordUpdateForm from "@/components/auth/password-update-form";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { userService } from "@/services/user.service";

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
  const [showDisableDialog, setShowDisableDialog] = useState(false);

  // Loading states
  const [loading, setLoading] = useState({
    page: true,
    form: false,
    twoFactor: false,
  });

  // Fetch 2FA status on mount
  useEffect(() => {
    const fetch2FAStatus = async () => {
      try {
        const response = await userService.get2FAStatus(currentUser?.userId);
        if (response.data && typeof response.data === "boolean") {
          setIs2faEnabled(response.data);
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
      const response = await userService.enable2FA(currentUser?.userId);
      const qrCode = response.data;
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

  // Open disable confirmation dialog
  const handleDisable2FA = () => {
    setShowDisableDialog(true);
  };

  // Disable 2FA
  const confirmDisable2FA = async () => {
    setLoading((prev) => ({ ...prev, twoFactor: true }));
    try {
      await userService.disable2FA(currentUser?.userId);
      setIs2faEnabled(false);
      setQrCodeUrl("");
      toast({
        title: t("common.success"),
        description: t("profile.twoFactor.messages.disableSuccess"),
        variant: "success",
      });
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("profile.twoFactor.messages.disableError"),
        variant: "destructive",
      });
    } finally {
      setLoading((prev) => ({ ...prev, twoFactor: false }));
      setShowDisableDialog(false);
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
      await userService.verify2FA(currentUser?.userId, verificationCode);
      setIs2faEnabled(true);
      setStep(1);
      toast({
        title: t("common.success"),
        description: t("profile.twoFactor.messages.enableSuccess"),
        variant: "success",
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
                  <div className="space-y-6">
                    {/* Basic Profile Form */}
                    <div className="space-y-2">
                      <Label htmlFor="username">{t("profile.updateProfile.fields.username")}</Label>
                      <Input disabled value={currentUser?.username} />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="email">{t("profile.updateProfile.fields.email")}</Label>
                      <Input disabled value={currentUser?.email} />
                    </div>

                    {/* Separator */}
                    <Separator />

                    {/* Password Update Section */}
                    <div className="space-y-4">
                      <div>
                        <h3 className="text-lg font-medium">{t("profile.password.title")}</h3>
                      </div>
                      <PasswordUpdateForm />
                    </div>
                  </div>
                </AccordionContent>
              </AccordionItem>
            </Accordion>

            <div className="rounded-lg border p-4">
              <h3 className="font-semibold">{t("profile.createdDate.title")}</h3>
              <p className="text-sm text-muted-foreground"><p>{moment(currentUser.createdDate).format('DD/MM/YYYY, h:mm:ss a')}</p></p>
            </div>
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
              onClick={is2faEnabled ? handleDisable2FA : enable2FA}
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

                  {/* Extract and display the secret key for manual entry */}
                  {(() => {
                    try {
                      // Parse the secret from the QR URL
                      const url = new URL(qrCodeUrl);
                      const data = url.searchParams.get("data");
                      if (data) {
                        const decodedData = decodeURIComponent(data);
                        const secretMatch = decodedData.match(/secret=([A-Z0-9]+)/);
                        if (secretMatch && secretMatch[1]) {
                          const secret = secretMatch[1];
                          return (
                            <div className="mt-4 text-center">
                              <p className="text-sm text-muted-foreground mb-2">{t("profile.twoFactor.manualCode")}</p>
                              <div className="flex items-center justify-center gap-2">
                                <code className="bg-muted px-2 py-1 rounded text-base font-mono tracking-wider">
                                  {secret}
                                </code>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => {
                                    navigator.clipboard.writeText(secret);
                                    toast({
                                      title: t("common.success"),
                                      description: t("profile.twoFactor.codeCopied"),
                                      variant: "success",
                                    });
                                  }}
                                >
                                  {t("profile.twoFactor.copy")}
                                </Button>
                              </div>
                            </div>
                          );
                        }
                      }
                    } catch (e) {
                      console.info("Error parsing 2FA QR code URL:", e);
                    }
                    return null;
                  })()}
                </div>

                <div className="flex gap-2">
                  <Input
                    placeholder={t("profile.twoFactor.verificationCodePlaceholder")}
                    value={verificationCode}
                    maxLength={6}
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

      {/* 2FA Disable Confirmation Dialog */}
      <Disable2FADialog
        open={showDisableDialog}
        onOpenChange={setShowDisableDialog}
        onConfirm={confirmDisable2FA}
        loading={loading.twoFactor}
      />
    </div>
  );
}