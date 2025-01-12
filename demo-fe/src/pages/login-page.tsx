import { zodResolver } from "@hookform/resolvers/zod";
import { jwtDecode } from "jwt-decode";
import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { FcGoogle } from "react-icons/fc";
import { Link, useNavigate } from "react-router-dom";
import * as z from "zod";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useAuth } from "@/context/auth-context";
import { APP_API_URL } from "@/env";
import { useToast } from "@/hooks/use-toast";
import { AuthService } from "@/services/auth.service";
import { LoginRequest } from "@/types/auth";

// Define schema for login form
const loginSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(1, "Password is required")
});

// Define schema for 2FA verification form
const twoFactorSchema = z.object({
  code: z.string().min(6, "Code must be 6 digits").max(6)
});

type LoginFormValues = z.infer<typeof loginSchema>;
type TwoFactorFormValues = z.infer<typeof twoFactorSchema>;

const LoginPage = () => {
  const navigate = useNavigate();
  const { setToken } = useAuth();
  const { toast } = useToast();

  // State for managing login flow
  const [step, setStep] = useState(1); // 1 = login, 2 = 2FA
  const [jwtToken, setJwtToken] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  // Initialize login form
  const loginForm = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: "",
      password: ""
    }
  });

  // Initialize 2FA form
  const twoFactorForm = useForm<TwoFactorFormValues>({
    resolver: zodResolver(twoFactorSchema),
    defaultValues: {
      code: ""
    }
  });

  // Handle successful login
  const handleSuccessfulLogin = (token: string, decodedToken: any) => {
    const user = {
      username: decodedToken.sub,
      roles: decodedToken.roles ? decodedToken.roles.split(",") : []
    };

    // Store tokens and user info
    localStorage.setItem("JWT_TOKEN", token);
    localStorage.setItem("USER", JSON.stringify(user));

    // Update auth context
    setToken(token);

    // Show success message
    toast({
      title: "Success",
      description: "Login successful!",
      variant: "success",
    });

    // Redirect to home
    navigate("/");
  };

  // Handle login submission
  const onLoginSubmit = async (data: LoginFormValues) => {
    setIsLoading(true);
    try {
      const response = await AuthService.login(data as LoginRequest);
      const { data: responseData } = response.data;

      if (responseData.jwtToken) {
        const decodedToken = jwtDecode<any>(responseData.jwtToken);
        if (decodedToken.is2faEnabled) {
          setJwtToken(responseData.jwtToken);
          setStep(2); // Move to 2FA verification
        } else {
          handleSuccessfulLogin(responseData.jwtToken, decodedToken);
        }
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Invalid credentials",
        variant: "destructive"
      });
    } finally {
      setIsLoading(false);
    }
  };

  // Handle 2FA verification
  const onTwoFactorSubmit = async (data: TwoFactorFormValues) => {
    setIsLoading(true);
    try {
      await AuthService.verify2FA(data.code, jwtToken);
      const decodedToken = jwtDecode<any>(jwtToken);
      handleSuccessfulLogin(jwtToken, decodedToken);
    } catch (error) {
      toast({
        title: "Error",
        description: "Invalid verification code",
        variant: "destructive"
      });
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center p-4 sm:p-8">
      <div className="w-full max-w-md space-y-6">
        <Card>
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">
              {step === 1 ? "Welcome back" : "Two-Factor Authentication"}
            </CardTitle>
            <CardDescription>
              {step === 1
                ? "Login with your email or Google account"
                : "Enter the verification code to continue"}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {step === 1 ? (
              // Login Form
              <Form {...loginForm}>
                <form onSubmit={loginForm.handleSubmit(onLoginSubmit)} className="space-y-6">
                  <div className="grid gap-6">
                    <Link
                      to={`${APP_API_URL}/oauth2/authorization/google`}
                      className="flex gap-1 items-center justify-center flex-1 border p-2 shadow-sm shadow-slate-200 rounded-md hover:bg-slate-300 transition-all duration-300"
                    >
                      <span>
                        <FcGoogle className="text-2xl" />
                      </span>
                      <span className="font-semibold sm:text-customText text-xs">Login with Google</span>
                    </Link>

                    <div className="relative">
                      <div className="absolute inset-0 flex items-center">
                        <span className="w-full border-t" />
                      </div>
                      <div className="relative flex justify-center text-xs uppercase">
                        <span className="bg-background px-2 text-muted-foreground">Or continue with email</span>
                      </div>
                    </div>

                    <div className="grid gap-4">
                      <FormField
                        control={loginForm.control}
                        name="username"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Username</FormLabel>
                            <FormControl>
                              <Input
                                {...field}
                                type="text"
                                disabled={isLoading}
                                placeholder="Enter your username"
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />

                      <FormField
                        control={loginForm.control}
                        name="password"
                        render={({ field }) => (
                          <FormItem>
                            <div className="flex items-center justify-between">
                              <FormLabel>Password</FormLabel>
                              <Button
                                variant="link"
                                className="px-0 font-normal"
                                onClick={() => navigate("/forgot-password")}
                                type="button"
                              >
                                Forgot password?
                              </Button>
                            </div>
                            <FormControl>
                              <Input
                                {...field}
                                type="password"
                                disabled={isLoading}
                                placeholder="Enter your password"
                              />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    </div>

                    <Button type="submit" className="w-full" disabled={isLoading}>
                      {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                      Sign In
                    </Button>

                    <p className="text-center text-sm text-muted-foreground">
                      Don't have an account?{" "}
                      <Button
                        variant="link"
                        className="px-0 font-normal"
                        onClick={() => navigate("/register")}
                        type="button"
                      >
                        Sign up
                      </Button>
                    </p>
                  </div>
                </form>
              </Form>
            ) : (
              // 2FA Verification Form
              <Form {...twoFactorForm}>
                <form onSubmit={twoFactorForm.handleSubmit(onTwoFactorSubmit)} className="space-y-6">
                  <FormField
                    control={twoFactorForm.control}
                    name="code"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Verification Code</FormLabel>
                        <FormControl>
                          <Input
                            {...field}
                            type="text"
                            disabled={isLoading}
                            placeholder="Enter 6-digit code"
                            maxLength={6}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <Button type="submit" className="w-full" disabled={isLoading}>
                    {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Verify Code
                  </Button>
                </form>
              </Form>
            )}
          </CardContent>
        </Card>

        <p className="text-center text-xs text-muted-foreground">
          By clicking continue, you agree to our{" "}
          <Button variant="link" className="h-auto p-0 text-xs font-normal">
            Terms of Service
          </Button>{" "}
          and{" "}
          <Button variant="link" className="h-auto p-0 text-xs font-normal">
            Privacy Policy
          </Button>
        </p>
      </div>
    </div>
  );
};

export default LoginPage;