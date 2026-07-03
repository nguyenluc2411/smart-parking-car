import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getSmtpSettings, saveSmtpSettings, sendTestEmail, type SmtpSettings } from "@/lib/api/notifications";

export function useSmtpSettings() {
  return useQuery({
    queryKey: ["smtp-settings"],
    queryFn: getSmtpSettings,
  });
}

export function useSaveSmtpSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (settings: SmtpSettings) => saveSmtpSettings(settings),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["smtp-settings"] });
    },
  });
}

export function useSendTestEmail() {
  return useMutation({
    mutationFn: (receiverEmail: string) => sendTestEmail(receiverEmail),
  });
}
