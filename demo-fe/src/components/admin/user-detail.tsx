// src/components/admin/user-detail.tsx

import { ArrowLeft, Loader2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useForm } from "react-hook-form";
import * as z from "zod";
import { zodResolver } from "@hookform/resolvers/zod";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { AdminService } from "@/services/admin.service";
import { Form, FormControl, FormDescription, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";

const formSchema = z.object({
  username: z.string().min(1, "Username is required"),
  email: z.string().email("Invalid email address"),
  password: z.string().optional(),
  role: z.string().optional(),
});

type FormValues = z.infer<typeof formSchema>;

interface Role {
  roleId: number;
  roleName: string;
}

export default function UserDetail() {
  // Get route params using useParams hook
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [roles, setRoles] = useState<Role[]>([]);
  const [selectedRole, setSelectedRole] = useState("");
  const [accountStates, setAccountStates] = useState({
    accountNonLocked: true,
    accountNonExpired: true,
    credentialsNonExpired: true,
    enabled: true,
  });
  const { toast } = useToast();

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      username: "",
      email: "",
      password: "",
      role: "",
    },
  });

  useEffect(() => {
    const fetchUserDetails = async () => {
      if (!userId) return;

      try {
        const [userResponse, rolesResponse] = await Promise.all([
          AdminService.getUser(parseInt(userId)),
          AdminService.getAllRoles(),
        ]);
        console.log("userResponse", userResponse);

        const userData = userResponse.data.data;
        form.reset({
          username: userData.userName,
          email: userData.email,
          role: userData.role?.roleName,
        });

        setSelectedRole(userData.role?.roleName || "");
        setAccountStates({
          accountNonLocked: userData.accountNonLocked,
          accountNonExpired: userData.accountNonExpired,
          credentialsNonExpired: userData.credentialsNonExpired,
          enabled: userData.enabled,
        });
        setRoles(rolesResponse.data.data);
      } catch (_error) {
        toast({
          title: "Error",
          description: "Failed to fetch user details",
          variant: "destructive",
        });
      } finally {
        setLoading(false);
      }
    };

    fetchUserDetails();
  }, [userId, form, toast]);

  const onSubmit = async (data: FormValues) => {
    if (!userId) return;

    try {
      if (data.password) {
        await AdminService.updatePassword(parseInt(userId), data.password);

        form.reset({ ...data, password: "" });

        toast({
          title: "Success",
          description: "Password updated successfully",
          variant: "success",
        });
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to update password",
        variant: "destructive",
      });
    }
  };

  const handleRoleChange = async (value: string) => {
    if (!userId) return;

    try {
      await AdminService.updateUserRole(parseInt(userId), {
        userId: parseInt(userId),
        roleName: value,
      });
      setSelectedRole(value);
      toast({
        title: "Success",
        description: "User role updated successfully",
        variant: "success",
      });
    } catch (error) {
      console.error("Error updating role:", error);
      toast({
        title: "Error",
        description: "Failed to update user role",
        variant: "destructive",
      });
    }
  };

  const handleAccountStateChange = async (key: keyof typeof accountStates, value: boolean) => {
    if (!userId) return;

    try {
      const userIdNum = parseInt(userId);
      switch (key) {
        case "accountNonLocked":
          await AdminService.updateAccountLockStatus(userIdNum, !value);
          break;
        case "accountNonExpired":
          await AdminService.updateAccountExpiryStatus(userIdNum, !value);
          break;
        case "enabled":
          await AdminService.updateAccountEnabledStatus(userIdNum, value);
          break;
        case "credentialsNonExpired":
          await AdminService.updateCredentialsExpiryStatus(userIdNum, !value);
          break;
      }

      setAccountStates((prev) => ({ ...prev, [key]: value }));

      toast({
        title: "Success",
        description: `Account ${key} updated successfully`,
        variant: "success",
      });
    } catch (error) {
      toast({
        title: "Error",
        description: `Failed to update ${key}`,
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

  // Rest of the component JSX remains the same
  return (
    <div className="space-y-6">
      <Button variant="ghost" onClick={() => navigate("/admin/users")} className="mb-4">
        <ArrowLeft className="mr-2 h-4 w-4" />
        Back to Users
      </Button>

      <div className="grid gap-6">
        <Card>
          <CardHeader>
            <CardTitle>User Information</CardTitle>
            <CardDescription>View and update user details</CardDescription>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <FormField
                    control={form.control}
                    name="username"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Username</FormLabel>
                        <FormControl>
                          <Input {...field} disabled />
                        </FormControl>
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="email"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Email</FormLabel>
                        <FormControl>
                          <Input {...field} disabled />
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </div>

                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>New Password</FormLabel>
                      <FormControl>
                        <Input type="password" placeholder="Enter new password" {...field} />
                      </FormControl>
                      <FormDescription>Leave blank to keep current password</FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <Button type="submit">Update Password</Button>
              </form>
            </Form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Role & Permissions</CardTitle>
            <CardDescription>Manage user role and account status</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="space-y-2">
              <Label>User Role</Label>
              <Select value={selectedRole} onValueChange={handleRoleChange}>
                <SelectTrigger>
                  <SelectValue placeholder="Select role" />
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
                  <Label>Account Enabled</Label>
                  <p className="text-sm text-muted-foreground">Allow or disable user access</p>
                </div>
                <Switch
                  checked={accountStates.enabled}
                  onCheckedChange={(checked) => handleAccountStateChange("enabled", checked)}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Account Locked</Label>
                  <p className="text-sm text-muted-foreground">Temporarily lock account access</p>
                </div>
                <Switch
                  checked={!accountStates.accountNonLocked}
                  onCheckedChange={(checked) => handleAccountStateChange("accountNonLocked", !checked)}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Account Expired</Label>
                  <p className="text-sm text-muted-foreground">Mark account as expired</p>
                </div>
                <Switch
                  checked={!accountStates.accountNonExpired}
                  onCheckedChange={(checked) => handleAccountStateChange("accountNonExpired", !checked)}
                />
              </div>

              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>Credentials Expired</Label>
                  <p className="text-sm text-muted-foreground">Force password reset</p>
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
