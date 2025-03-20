import { ArrowLeft, Eye, EyeOff, KeyRound, Loader2, Lock, Save, Shield, User } from "lucide-react";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router-dom";

import RevokeTokensButton from "@/components/admin/reports/revoke-tokens-button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { useAuth } from "@/context/auth-context";
import { useToast } from "@/hooks/use-toast";
import { adminService } from "@/services/admin.service";
import { Role, UserData } from "@/types/user";

export default function UserDetail() {
  const { t } = useTranslation();
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { currentUser } = useAuth();

  const [userData, setUserData] = useState<UserData | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);
  const [loading, setLoading] = useState(true);
  const [roleLoading, setRoleLoading] = useState(false);
  const [statusLoading, setStatusLoading] = useState(false);

  const [currentRole, setCurrentRole] = useState<string>("");
  const [currentAccountLocked, setCurrentAccountLocked] = useState(false);

  const [selectedRole, setSelectedRole] = useState<string>("");
  const [accountLocked, setAccountLocked] = useState(false);

  const [roleChanged, setRoleChanged] = useState(false);
  const [accountStatusChanged, setAccountStatusChanged] = useState(false);

  // Check if current user is viewing their own profile
  const isOwnProfile = currentUser?.userId === userId;

  const fetchUserData = async () => {
    if (!userId) return;

    setLoading(true);
    try {
      const response = await adminService.getUser(userId);
      const user = response.data;
      setUserData(user);

      // Set both current DB values and form values
      const roleName = user.role.roleName;
      setCurrentRole(roleName);
      setSelectedRole(roleName);

      const isLocked = !user.accountNonLocked;
      setCurrentAccountLocked(isLocked);
      setAccountLocked(isLocked);

      // Reset change trackers
      setRoleChanged(false);
      setAccountStatusChanged(false);
    } catch (error) {
      console.error("Error fetching user data:", error);
      toast({
        title: t("common.error"),
        description: t("admin.users.messages.fetchError"),
        variant: "destructive",
      });
    } finally {
      setLoading(false);
    }
  };

  const fetchRoles = async () => {
    try {
      const response = await adminService.getAllRoles();
      setRoles(response.data);
    } catch (error) {
      console.error("Error fetching roles:", error);
    }
  };

  useEffect(() => {
    fetchUserData();
    fetchRoles();
  }, [userId]);

  // Track role changes
  useEffect(() => {
    if (currentRole && selectedRole) {
      setRoleChanged(currentRole !== selectedRole);
    }
  }, [currentRole, selectedRole]);

  // Track account status changes
  useEffect(() => {
    setAccountStatusChanged(currentAccountLocked !== accountLocked);
  }, [currentAccountLocked, accountLocked]);

  const handleUpdateRole = async () => {
    if (!userId || !selectedRole || !roleChanged) return;

    // Prevent admins from changing their own role
    if (isOwnProfile) {
      toast({
        title: t("common.error"),
        description: t("admin.users.actions.updateRole.cannotChangeOwnRole", "You cannot change your own role"),
        variant: "destructive",
      });

      // Reset to original role
      setSelectedRole(currentRole);
      setRoleChanged(false);
      return;
    }

    setRoleLoading(true);
    try {
      await adminService.updateUserRole(userId, {
        userId,
        roleName: selectedRole,
      });

      toast({
        title: t("common.success"),
        description: t("admin.users.actions.updateRole.success"),
        variant: "success",
      });

      // Refresh user data
      fetchUserData();
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("admin.users.actions.updateRole.error"),
        variant: "destructive",
      });
    } finally {
      setRoleLoading(false);
    }
  };

  const handleUpdateAccountStatus = async () => {
    if (!userId || !accountStatusChanged) return;

    setStatusLoading(true);
    try {
      await adminService.updateStatus(userId, {
        accountLocked: accountLocked,
      });

      toast({
        title: t("common.success"),
        description: t("admin.users.actions.updateStatus.success"),
        variant: "success",
      });

      // Refresh user data
      fetchUserData();
    } catch (_error) {
      toast({
        title: t("common.error"),
        description: t("admin.users.actions.updateAccount.error"),
        variant: "destructive",
      });
    } finally {
      setStatusLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin" />
      </div>
    );
  }

  if (!userData) {
    return (
      <div className="container mx-auto py-10">
        <Card>
          <CardContent className="p-6">
            <p className="text-center">User not found</p>
            <div className="mt-4 flex justify-center">
              <Button variant="ghost" onClick={() => navigate("/admin/users")} className="mb-4">
                <ArrowLeft className="mr-2 h-4 w-4" />
                {t("admin.users.navigation.backToUsers")}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    );
  }

  return (
    <div className="container mx-auto py-10">
      <Button variant="ghost" onClick={() => navigate("/admin/users")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        {t("admin.users.navigation.backToUsers")}
      </Button>

      {/* User Information Card */}
      <Card className="mb-6">
        <CardHeader>
          <CardTitle>{t("admin.users.details.title")}</CardTitle>
          <CardDescription>{t("admin.users.details.description")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center gap-4">
            <Avatar className="h-16 w-16">
              <AvatarFallback className="text-lg">{userData.username[0]?.toUpperCase()}</AvatarFallback>
            </Avatar>
            <div>
              <h3 className="text-xl font-semibold">{userData.username}</h3>
              <p className="text-muted-foreground">{userData.email}</p>
              <div className="mt-1 flex items-center">
                <div
                  className={`mr-2 h-2.5 w-2.5 rounded-full ${userData.enabled ? "bg-green-500" : "bg-red-500"}`}
                ></div>
                <span className="text-sm text-muted-foreground">
                  {userData.enabled ? t("admin.users.status.active") : t("admin.users.status.inactive")}
                </span>
              </div>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <Label htmlFor="username">{t("admin.users.details.username")}</Label>
              <Input id="username" value={userData.username} disabled className="bg-muted" />
            </div>
            <div>
              <Label htmlFor="email">{t("admin.users.details.email")}</Label>
              <Input id="email" value={userData.email} disabled className="bg-muted" />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Role & Permissions Card */}
      <Card>
        <CardHeader>
          <CardTitle>{t("admin.users.details.roleAndPermissions.title")}</CardTitle>
          <CardDescription>{t("admin.users.details.roleAndPermissions.description")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Role Selection with Update Button */}
          <div className="space-y-2">
            <Label htmlFor="role">{t("admin.users.details.roleAndPermissions.userRole")}</Label>
            <div className="flex gap-2">
              <Select
                disabled={roleLoading || isOwnProfile}
                value={selectedRole}
                onValueChange={(value) => setSelectedRole(value)}
              >
                <SelectTrigger id="role" className="w-full md:w-72">
                  <SelectValue placeholder={t("admin.users.details.roleAndPermissions.selectRole")} />
                </SelectTrigger>
                <SelectContent>
                  {roles.map((role) => (
                    <SelectItem key={role.roleId} value={role.roleName}>
                      <div className="flex items-center">
                        <Shield className="mr-2 h-4 w-4 text-primary" />
                        {role.roleName}
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button
                onClick={handleUpdateRole}
                disabled={roleLoading || !roleChanged || isOwnProfile}
                size="sm"
                title={
                  isOwnProfile
                    ? t("admin.users.actions.updateRole.cannotChangeOwnRole", "You cannot change your own role")
                    : ""
                }
              >
                {roleLoading ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <Save className="h-4 w-4 mr-2" />}
                {t("common.update")}
              </Button>
            </div>
            {isOwnProfile && (
              <p className="text-sm text-amber-500 mt-1">
                {t("admin.users.actions.updateRole.cannotChangeOwnRole", "You cannot change your own role")}
              </p>
            )}
          </div>

          <div className="rounded-md border p-4">
            <h3 className="mb-4 text-lg font-medium">{t("admin.users.details.accountStatus.title")}</h3>

            {/* Account Lock Status */}
            <div className="flex items-center justify-between py-2">
              <div className="space-y-0.5">
                <div className="flex items-center">
                  <Lock className="mr-2 h-4 w-4 text-muted-foreground" />
                  <h4 className="font-medium">{t("admin.users.details.accountStatus.locked")}</h4>
                </div>
                <p className="text-sm text-muted-foreground">
                  {t("admin.users.details.accountStatus.lockedDescription")}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Switch checked={accountLocked} onCheckedChange={setAccountLocked} disabled={statusLoading} />
                <Button onClick={handleUpdateAccountStatus} disabled={statusLoading || !accountStatusChanged} size="sm">
                  {statusLoading ? (
                    <Loader2 className="h-4 w-4 animate-spin mr-2" />
                  ) : (
                    <Save className="h-4 w-4 mr-2" />
                  )}
                  {t("common.update")}
                </Button>
              </div>
            </div>

            {/* Token Expiration */}
            <div className="flex items-center justify-between border-t py-4">
              <div className="space-y-0.5">
                <div className="flex items-center">
                  <KeyRound className="mr-2 h-4 w-4 text-muted-foreground" />
                  <h4 className="font-medium">{t("admin.users.details.accountStatus.tokenExpired")}</h4>
                </div>
                <p className="text-sm text-muted-foreground">
                  {t("admin.users.details.accountStatus.tokenExpiredDescription")}
                </p>
              </div>
              <RevokeTokensButton userId={userId} onSuccess={fetchUserData} />
            </div>

            {/* 2FA Status */}
            <div className="flex items-center justify-between border-t py-4">
              <div className="space-y-0.5">
                <div className="flex items-center">
                  {userData.twoFactorEnabled ? (
                    <Eye className="mr-2 h-4 w-4 text-green-500" />
                  ) : (
                    <EyeOff className="mr-2 h-4 w-4 text-muted-foreground" />
                  )}
                  <h4 className="font-medium">Two-Factor Authentication</h4>
                </div>
                <p className="text-sm text-muted-foreground">
                  {userData.twoFactorEnabled
                    ? "User has enabled two-factor authentication"
                    : "User has not enabled two-factor authentication"}
                </p>
              </div>
              <div
                className={`rounded-full px-2 py-1 text-xs ${
                  userData.twoFactorEnabled ? "bg-green-100 text-green-800" : "bg-amber-100 text-amber-800"
                }`}
              >
                {userData.twoFactorEnabled ? "Enabled" : "Disabled"}
              </div>
            </div>

            {/* Sign Up Method */}
            <div className="flex items-center justify-between border-t py-4">
              <div className="space-y-0.5">
                <div className="flex items-center">
                  <User className="mr-2 h-4 w-4 text-muted-foreground" />
                  <h4 className="font-medium">Sign Up Method</h4>
                </div>
              </div>
              <div
                className={`rounded-full px-2 py-1 text-xs ${
                  userData.signUpMethod === "google" ? "bg-blue-100 text-blue-800" : "bg-purple-100 text-purple-800"
                }`}
              >
                {userData.signUpMethod === "google" ? "Google" : "Email"}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
