package vn.doit.hrm.service.reminder.dto;

import java.util.List;
import vn.doit.hrm.service.reminder.ReminderStore;

/** Tình trạng email nhắc việc cho Quản trị: có SMTP chưa, lần chạy gần nhất, ai chưa có email. */
public record ReminderStatusDTO(boolean mailConfigured, ReminderStore.Run lastRun, List<String> missingRecipients) {}
