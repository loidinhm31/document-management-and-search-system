import { Label } from "@radix-ui/react-menu";
import { Loader2 } from "lucide-react";
import React, { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useToast } from "@/hooks/use-toast";

const TIMER_DURATION = 300; // 5 minutes in seconds
const MAX_ATTEMPTS = 5;
const LOCK_DURATION = 1800; // 30 minutes in seconds

export default function OtpVerificationForm({ onVerified, onResend }) {
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  const [timer, setTimer] = useState(TIMER_DURATION);
  const [isLocked, setIsLocked] = useState(false);
  const [isLockTimer, setIsLockTimer] = useState(false);
  const [lockTimer, setLockTimer] = useState(0);
  const [otpValue, setOtpValue] = useState("");

  // Timer countdown effect
  useEffect(() => {
    let interval;
    if (timer > 0) {
      interval = setInterval(() => {
        setTimer((prev) => prev - 1);
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [timer]);

  // Lock timer countdown effect
  useEffect(() => {
    let interval;
    if (lockTimer > 0) {
      interval = setInterval(() => {
        setLockTimer((prev) => prev - 1);
      }, 1000);
    } else if (lockTimer === 0 && isLockTimer) {
      setIsLockTimer(false);
    }
    return () => clearInterval(interval);
  }, [lockTimer, isLocked]);

  const formatTime = (seconds) => {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, "0")}`;
  };

  const handleResendOtp = async () => {
    try {
      setIsLoading(true);
      await onResend();
      setTimer(TIMER_DURATION);
      toast({
        title: "Success",
        description: "OTP code has been resent",
        variant: "success",
      });
    } catch (_error) {
      toast({
        title: "Error",
        description: "Failed to resend OTP code",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isLockTimer || isLocked || !otpValue || otpValue.length !== 6) return;

    try {
      setIsLoading(true);
      const response = await onVerified(otpValue);
      if (response.data) {
        if (response.data.locked) {
          setIsLocked(true);
          setOtpValue("");
        } else if (!response.data.verified) {
          if (response.data.otpCount >= MAX_ATTEMPTS) {
            setIsLocked(true);
            setLockTimer(LOCK_DURATION);
            toast({
              title: "Error",
              description: `Maximum attempts reached. Please try again after ${formatTime(LOCK_DURATION)}`,
              variant: "destructive",
            });
          } else {
            setOtpValue("");
            toast({
              title: "Error",
              description: `Invalid OTP code. ${MAX_ATTEMPTS - response.data.otpCount} attempts remaining`,
              variant: "destructive",
            });
          }
        }
      } else {
        setOtpValue("");
        toast({
          title: "Error",
          description: `Error occurred`,
          variant: "destructive",
        });
      }
    } catch (error) {
      console.error(error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleOtpChange = (e) => {
    const value = e.target.value.replace(/[^\d]/g, "").slice(0, 6);
    setOtpValue(value);
  };

  return (
    <Card className="w-full max-w-md">
      <CardHeader>
        <CardTitle>Verify your account</CardTitle>
        <CardDescription>Enter the 6-digit code sent to your email</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label>Verification Code</Label>
            <Input
              value={otpValue}
              onChange={handleOtpChange}
              disabled={isLoading || isLockTimer || isLocked}
              placeholder="Enter 6-digit code"
              maxLength={6}
              className="text-center text-2xl tracking-widest"
            />
          </div>

          <div className="flex flex-col gap-4">
            <Button
              type="submit"
              disabled={isLoading || isLockTimer || isLocked || otpValue?.length !== 6}
              className="w-full"
            >
              {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Verify Account
            </Button>

            {isLockTimer || isLocked ? (
              <div className="text-center text-sm text-muted-foreground">
                OTP verification locked. Try again later {isLockTimer && `in ${formatTime(lockTimer)}`}
              </div>
            ) : (
              <>
                <div className="text-center text-sm text-muted-foreground">Code expires in {formatTime(timer)}</div>

                <Button
                  type="button"
                  variant="link"
                  disabled={isLoading || timer > 0}
                  onClick={handleResendOtp}
                  className="w-full"
                >
                  Resend Code
                </Button>
              </>
            )}
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
