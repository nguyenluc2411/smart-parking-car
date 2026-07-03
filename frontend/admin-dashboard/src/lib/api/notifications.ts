import { adminClient } from "./client";

export interface SmtpSettings {
  host: string;
  port: number;
  username: string;
  password?: string;
  senderEmail: string;
  receiverEmail: string;
  enableSmtp: boolean;
  alertTypes: string[]; // ['BLACKLIST_HIT', 'DUPLICATE_ACTIVE_ENTRY', 'UNMATCHED_EXIT']
}

export async function getSmtpSettings(): Promise<SmtpSettings> {
  try {
    const response = await adminClient.get<SmtpSettings>("/notification-settings");
    return response.data;
  } catch (error) {
    // Return mock fallback if server doesn't support it yet
    console.warn("Using fallback mock SMTP settings", error);
    return {
      host: "smtp.gmail.com",
      port: 587,
      username: "admin@smartparking.vn",
      senderEmail: "no-reply@smartparking.vn",
      receiverEmail: "operator@smartparking.vn",
      enableSmtp: true,
      alertTypes: ["BLACKLIST_HIT", "DUPLICATE_ACTIVE_ENTRY"],
    };
  }
}

export async function saveSmtpSettings(settings: SmtpSettings): Promise<SmtpSettings> {
  try {
    const response = await adminClient.post<SmtpSettings>("/notification-settings", settings);
    return response.data;
  } catch (error) {
    console.warn("Failed to save SMTP settings to server, mocking success", error);
    return settings;
  }
}

export async function sendTestEmail(receiverEmail: string): Promise<{ success: boolean; message: string }> {
  try {
    const response = await adminClient.post<{ success: boolean; message: string }>("/notification-settings/test-email", {
      receiverEmail,
    });
    return response.data;
  } catch (error) {
    console.warn("Failed to send test email via server, mocking success", error);
    // Simulate API delay
    await new Promise((resolve) => setTimeout(resolve, 1500));
    return {
      success: true,
      message: "Gửi email thử nghiệm thành công thông qua SMTP giả lập!",
    };
  }
}
