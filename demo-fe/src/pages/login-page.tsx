import { zodResolver } from "@hookform/resolvers/zod";
import { jwtDecode } from "jwt-decode";
import { memo, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import * as z from "zod";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/context/auth-context";
import { APP_API_URL } from "@/env";
import { useToast } from "@/hooks/use-toast";
import { JwtPayload, LoginRequest } from "@/types/auth";
import { AuthService } from "@/services/auth.service";
import { FcGoogle } from "react-icons/fc";

const loginSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(1, "Password is required")
});

type LoginFormValues = z.infer<typeof loginSchema>;

const LoginPage = memo(() => {
  const navigate = useNavigate();
  const { setToken, token } = useAuth();
  const { toast } = useToast();

  const [step, setStep] = useState(1);
  const [jwtToken, setJwtToken] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors }
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      username: "",
      password: ""
    }
  });

  const handleSuccessfulLogin = (token, decodedToken) => {
    const user = {
      username: decodedToken.sub,
      roles: decodedToken.roles ? decodedToken.roles.split(",") : []
    };
    window.localStorage.setItem("JWT_TOKEN", token);
    window.localStorage.setItem("USER", JSON.stringify(user));

    //store the token on the context state  so that it can be shared any where in our application by context provider
    setToken(token);

    navigate("/");
  };

  const onLoginHandler = async (values: LoginFormValues) => {
    setIsLoading(true);
    try {
      const response = await AuthService.login(values as LoginRequest);
      const { data } = response.data;

      if (data.jwtToken) {
        const decodedToken = jwtDecode<JwtPayload>(data.jwtToken);
        if (decodedToken.is2faEnabled) {
          setJwtToken(data.jwtToken);
          setStep(2); // Move to 2FA verification
        } else {
          handleSuccessfulLogin(data.jwtToken, decodedToken);
        }
      }

      toast({
        title: "Success",
        description: "Login successful!"
      });
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

  return (
    <div className="flex min-h-screen items-center justify-center p-4 sm:p-8">
      <div className="w-full max-w-md space-y-6">
        <Card>
          <CardHeader className="text-center">
            <CardTitle className="text-2xl">Welcome back</CardTitle>
            <CardDescription>Login with your email or Google account</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onLoginHandler)} className="space-y-6">
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
                    <span className="w-full border-t"></span>
                  </div>
                  <div className="relative flex justify-center text-xs uppercase">
                    <span className="bg-background px-2 text-muted-foreground">Or continue with email</span>
                  </div>
                </div>

                <div className="grid gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="email">Email</Label>
                    <Input {...register("username")} id="username" type="text" disabled={isLoading} />
                    {errors.username && <p className="text-sm text-destructive">{errors.username.message}</p>}
                  </div>

                  <div className="grid gap-2">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="password">Password</Label>
                      <Button
                        variant="link"
                        className="px-0 font-normal"
                        onClick={() => navigate("/forgot-password")}
                        type="button"
                      >
                        Forgot password?
                      </Button>
                    </div>
                    <Input {...register("password")} id="password" type="password" disabled={isLoading} />
                    {errors.password && <p className="text-sm text-destructive">{errors.password.message}</p>}
                  </div>
                </div>

                <Button type="submit" className="w-full" disabled={isLoading}>
                  {isLoading ? "Signing in..." : "Sign in"}
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
});

LoginPage.displayName = "LoginPage";

export default LoginPage;
