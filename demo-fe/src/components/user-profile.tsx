import { zodResolver } from "@hookform/resolvers/zod";
import { jwtDecode } from "jwt-decode";
import { Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import * as z from "zod";

import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { UserService } from "@/services/user.service";

const formSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(6, "Password must be at least 6 characters").optional(),
});

type FormValues = z.infer<typeof formSchema>;

const UserProfile = () => {
  const { currentUser, token } = useAuth();
  const { toast } = useToast();
  const [loginSession, setLoginSession] = useState<string | null>(null);
  const [credentialExpireDate, setCredentialExpireDate] = useState<string | null>(null);

  // 2FA states
  const [is2faEnabled, setIs2faEnabled] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [step, setStep] = useState(1);

  // Loading states
  const [loading, setLoading] = useState({
    page: true,
    form: false,
    twoFactor: false
  });

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: currentUser?.username || "",
      password: ""
    }
  });

  // Fetch 2FA status on mount
  useEffect(() => {
    const fetch2FAStatus = async () => {
      try {
        const response = await UserService.get2FAStatus();
        setIs2faEnabled(response.data.data);
      } catch (error) {
        toast({
          title: "Error",
          description: "Failed to fetch 2FA status",
          variant: "destructive"
        });
      } finally {
        setLoading(prev => ({ ...prev, page: false }));
      }
    };

    fetch2FAStatus();
  }, []);

  // Set login session from token
  useEffect(() => {
    if (token) {
      const decodedToken = jwtDecode(token);
      const lastLogin = new Date(decodedToken.iat * 1000).toLocaleString();
      setLoginSession(lastLogin);
    }
  }, [token]);

  // Set initial form values when user data is available
  useEffect(() => {
    if (currentUser) {
      form.reset({
        username: currentUser.username
      });

      if (currentUser.credentialsExpiryDate) {
        setCredentialExpireDate(new Date(currentUser.credentialsExpiryDate).toLocaleDateString());
      }
    }
  }, [currentUser, form]);

  // Enable 2FA
  const enable2FA = async () => {
    setLoading(prev => ({ ...prev, twoFactor: true }));
    try {
      const response = await UserService.enable2FA();
      setQrCodeUrl(response.data.data);
      setStep(2);
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to enable 2FA",
        variant: "destructive"
      });
    } finally {
      setLoading(prev => ({ ...prev, twoFactor: false }));
    }
  };

  // Disable 2FA
  const disable2FA = async () => {
    setLoading(prev => ({ ...prev, twoFactor: true }));
    try {
      await UserService.disable2FA();
      setIs2faEnabled(false);
      setQrCodeUrl("");
      toast({
        title: "Success",
        description: "2FA has been disabled"
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to disable 2FA",
        variant: "destructive"
      });
    } finally {
      setLoading(prev => ({ ...prev, twoFactor: false }));
    }
  };

  // Verify 2FA
  const verify2FA = async () => {
    if (!verificationCode) {
      toast({
        title: "Error",
        description: "Please enter verification code",
        variant: "destructive"
      });
      return;
    }

    setLoading(prev => ({ ...prev, twoFactor: true }));
    try {
      await UserService.verify2FA(verificationCode);
      setIs2faEnabled(true);
      setStep(1);
      toast({
        title: "Success",
        description: "2FA has been enabled"
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Invalid verification code",
        variant: "destructive"
      });
    } finally {
      setLoading(prev => ({ ...prev, twoFactor: false }));
    }
  };

  // Update user credentials
  const onSubmit = async (data: FormValues) => {
    setLoading(prev => ({ ...prev, form: true }));
    try {
      const updateData: any = { username: data.username };
      if (data.password) {
        updateData.password = data.password;
      }

      await UserService.updateCredentials(updateData);
      form.reset({ ...data, password: "" });
      toast({
        title: "Success",
        description: "Profile updated successfully"
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to update profile",
        variant: "destructive"
      });
    } finally {
      setLoading(prev => ({ ...prev, form: false }));
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
              <CardTitle>{currentUser?.username}</CardTitle>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <Accordion type="single" collapsible>
              <AccordionItem value="credentials">
                <AccordionTrigger>Update Profile</AccordionTrigger>
                <AccordionContent>
                  <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="username">Username</Label>
                      <Input
                        id="username"
                        {...form.register("username")}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="email">Email</Label>
                      <Input
                        id="email"
                        value={currentUser?.email}
                        disabled
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="password">New Password</Label>
                      <Input
                        id="password"
                        type="password"
                        {...form.register("password")}
                        placeholder="Enter new password"
                      />
                    </div>
                    <Button type="submit" disabled={loading.form}>
                      {loading.form && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                      Update Profile
                    </Button>
                  </form>
                </AccordionContent>
              </AccordionItem>
            </Accordion>

            {loginSession && (
              <div className="rounded-lg border p-4">
                <h3 className="font-semibold">Last Login Session</h3>
                <p className="text-sm text-muted-foreground">{loginSession}</p>
              </div>
            )}
          </CardContent>
        </Card>

        {/* 2FA Section */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Two-Factor Authentication</CardTitle>
              <div className={`rounded-full px-2 py-1 text-xs ${
                is2faEnabled ? "bg-green-100 text-green-800" : "bg-red-100 text-red-800"
              }`}>
                {is2faEnabled ? "Enabled" : "Disabled"}
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <p className="text-sm text-muted-foreground">
              Two-factor authentication adds an extra layer of security to your account
            </p>

            <Button
              onClick={is2faEnabled ? disable2FA : enable2FA}
              variant={is2faEnabled ? "destructive" : "default"}
              disabled={loading.twoFactor}
            >
              {loading.twoFactor && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {is2faEnabled ? "Disable 2FA" : "Enable 2FA"}
            </Button>

            {step === 2 && qrCodeUrl && (
              <div className="space-y-4">
                <div className="rounded-lg border p-4">
                  <img src={qrCodeUrl} alt="2FA QR Code" className="mx-auto" />
                </div>

                <div className="flex gap-2">
                  <Input
                    placeholder="Enter verification code"
                    value={verificationCode}
                    onChange={(e) => setVerificationCode(e.target.value)}
                  />
                  <Button onClick={verify2FA} disabled={loading.twoFactor}>
                    {loading.twoFactor && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Verify
                  </Button>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default UserProfile;