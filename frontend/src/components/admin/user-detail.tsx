import { ArrowLeft, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useToast } from "@/hooks/use-toast";
import { adminService } from "@/services/admin.service";
import { Role, UserData } from "@/types/user";

export default function UserDetail() {
  const { t } = useTranslation();
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [roles, setRoles] = useState<Role[]>([]);
  const [selectedRole, setSelectedRole] = useState("");
  const [userData, setUserData] = useState<UserData | null>(null);
  const [accountStates, setAccountStates] = useState({
    accountNonLocked: true,
    accountNonExpired: true,
    credentialsNonExpired: true,
    enabled: true,
  });
  const { toast } = useToast();

  useEffect(() => {
    const fetchUserDetails = async () => {
      if (!userId) return;

      try {
        // Get user details - Updated endpoint
        const userResponse = await adminService.getUser(userId);
        // Get roles - This endpoint remains the same
        const rolesResponse = await adminService.getAllRoles();

        const userData = userResponse.data;
        const roles = rolesResponse.data;

        setUserData(userData);
        setSelectedRole(userData.role?.roleName || "");
        setAccountStates({
          accountNonLocked: userData.accountNonLocked,
          accountNonExpired: userData.accountNonExpired,
          credentialsNonExpired: userData.credentialsNonExpired,
          enabled: userData.enabled,
        });
        setRoles(roles);
      } catch (error) {
        console.log("Error:", error);
        toast({
          title: t("common.error"),
          description: t("admin.users.messages.fetchError"),
          variant: "destructive",
        });
      } finally {
        setLoading(false);
      }
    };

    fetchUserDetails();
  }, [userId, toast, t]);

  const handleRoleChange = async (value: string) => {
    if (!userId) return;

    try {
      // Updated to use new endpoint
      await adminService.updateUserRole(userId, {
        userId: userId,
        roleName: value,
      });
      setSelectedRole(value);
    } catch (error) {
      console.log("Error:", error);
      toast({
        title: t("common.error"),
        description: t("admin.users.actions.updateRole.error"),
        variant: "destructive",
      });
    }
  };

  const handleAccountStateChange = async (key: keyof typeof accountStates, value: boolean) => {
    if (!userId) return;

    try {
      const updateData = {
        accountLocked: key === "accountNonLocked" ? !value : !accountStates.accountNonLocked,
        accountExpired: key === "accountNonExpired" ? !value : !accountStates.accountNonExpired,
        credentialsExpired: key === "credentialsNonExpired" ? !value : !accountStates.credentialsNonExpired,
        enabled: key === "enabled" ? value : accountStates.enabled,
      };

      await adminService.updateStatus(userId, updateData);
      setAccountStates((prev) => ({ ...prev, [key]: value }));
    } catch (error) {
      toast({
        title: t("common.error"),
        description: t("admin.users.actions.updateAccount.error"),
        variant: "destructive",
      });
    }
  };

  if (loading) {
    return (
      <div className="flex h-[400px] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <Button variant="ghost" onClick={() => navigate("/admin/users")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        {t("admin.users.navigation.backToUsers")}
      </Button>

      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("admin.users.details.title")}</CardTitle>
            <CardDescription>{t("admin.users.details.description")}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t("admin.users.details.username")}</Label>
                <Input value={userData?.username || ""} disabled />
              </div>

              <div className="space-y-2">
                <Label>{t("admin.users.details.email")}</Label>
                <Input value={userData?.email || ""} disabled />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t("admin.users.details.roleAndPermissions.title")}</CardTitle>
            <CardDescription>{t("admin.users.details.roleAndPermissions.description")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-2">
              <Label>{t("admin.users.details.roleAndPermissions.userRole")}</Label>
              <Select value={selectedRole} onValueChange={handleRoleChange}>
                <SelectTrigger>
                  <SelectValue placeholder={t("admin.users.details.roleAndPermissions.selectRole")} />
                </SelectTrigger>
                <SelectContent>
                  {roles.map((role) => (
                    <SelectItem key={role.roleId} value={role.roleName}>
                      {role.roleName.replace("ROLE_", "")}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>{t("admin.users.details.accountStatus.enabled")}</Label>
                  <p className="text-sm text-muted-foreground">
                    {t("admin.users.details.accountStatus.enabledDescription")}
                  </p>
                </div>
                <Switch
                  checked={accountStates.enabled}
                  onCheckedChange={(checked) => handleAccountStateChange("enabled", checked)}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>{t("admin.users.details.accountStatus.locked")}</Label>
                  <p className="text-sm text-muted-foreground">
                    {t("admin.users.details.accountStatus.lockedDescription")}
                  </p>
                </div>
                <Switch
                  checked={!accountStates.accountNonLocked}
                  onCheckedChange={(checked) => handleAccountStateChange("accountNonLocked", !checked)}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>{t("admin.users.details.accountStatus.expired")}</Label>
                  <p className="text-sm text-muted-foreground">
                    {t("admin.users.details.accountStatus.expiredDescription")}
                  </p>
                </div>
                <Switch
                  checked={!accountStates.accountNonExpired}
                  onCheckedChange={(checked) => handleAccountStateChange("accountNonExpired", !checked)}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>{t("admin.users.details.accountStatus.credentialsExpired")}</Label>
                  <p className="text-sm text-muted-foreground">
                    {t("admin.users.details.accountStatus.credentialsExpiredDescription")}
                  </p>
                </div>
                <Switch
                  checked={!accountStates.credentialsNonExpired}
                  onCheckedChange={(checked) => handleAccountStateChange("credentialsNonExpired", !checked)}
                />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
