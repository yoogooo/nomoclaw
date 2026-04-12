import { BriefcaseBusiness, CalendarRange, Clock3, LineChart, Sparkles } from "lucide-vue-next";

export type ExecutionType = "once" | "recurring";
export type RecurringMode = "minute" | "hour" | "day" | "week" | "month";

export interface TaskTemplate {
  id: string;
  label: string;
  description: string;
  icon: unknown;
  accent: string;
  title: string;
  taskContent: string;
  executionType: ExecutionType;
  recurringMode: RecurringMode;
  time: string;
  weekdays: string[];
  monthlyDay: number;
  minuteInterval: number;
  hourInterval: number;
  hourlyMinute: number;
}

export type TranslateFn = (key: string) => string;

export function buildCronTaskTemplates(t: TranslateFn): TaskTemplate[] {
  return [
    {
      id: "market-brief",
      label: t("cron.templates.marketBrief.label"),
      description: t("cron.templates.marketBrief.description"),
      icon: LineChart,
      accent: "var(--color-brand-primary)",
      title: t("cron.templates.marketBrief.title"),
      taskContent: t("cron.templates.marketBrief.taskContent"),
      executionType: "recurring",
      recurringMode: "day",
      time: "08:30",
      weekdays: ["2"],
      monthlyDay: 1,
      minuteInterval: 15,
      hourInterval: 1,
      hourlyMinute: 0
    },
    {
      id: "work-reminder",
      label: t("cron.templates.workReminder.label"),
      description: t("cron.templates.workReminder.description"),
      icon: BriefcaseBusiness,
      accent: "var(--color-warning-500)",
      title: t("cron.templates.workReminder.title"),
      taskContent: t("cron.templates.workReminder.taskContent"),
      executionType: "recurring",
      recurringMode: "day",
      time: "09:00",
      weekdays: ["2"],
      monthlyDay: 1,
      minuteInterval: 30,
      hourInterval: 1,
      hourlyMinute: 0
    },
    {
      id: "weekly-review",
      label: t("cron.templates.weeklyReview.label"),
      description: t("cron.templates.weeklyReview.description"),
      icon: CalendarRange,
      accent: "var(--color-semantic-success)",
      title: t("cron.templates.weeklyReview.title"),
      taskContent: t("cron.templates.weeklyReview.taskContent"),
      executionType: "recurring",
      recurringMode: "week",
      time: "18:00",
      weekdays: ["6"],
      monthlyDay: 1,
      minuteInterval: 30,
      hourInterval: 1,
      hourlyMinute: 0
    },
    {
      id: "hourly-watch",
      label: t("cron.templates.hourlyWatch.label"),
      description: t("cron.templates.hourlyWatch.description"),
      icon: Clock3,
      accent: "var(--color-brand-deep)",
      title: t("cron.templates.hourlyWatch.title"),
      taskContent: t("cron.templates.hourlyWatch.taskContent"),
      executionType: "recurring",
      recurringMode: "hour",
      time: "08:00",
      weekdays: ["2"],
      monthlyDay: 1,
      minuteInterval: 30,
      hourInterval: 1,
      hourlyMinute: 0
    },
    {
      id: "custom",
      label: t("cron.templates.custom.label"),
      description: t("cron.templates.custom.description"),
      icon: Sparkles,
      accent: "var(--color-text-heading-deep)",
      title: "",
      taskContent: "",
      executionType: "recurring",
      recurringMode: "day",
      time: "10:00",
      weekdays: ["2"],
      monthlyDay: 1,
      minuteInterval: 30,
      hourInterval: 1,
      hourlyMinute: 0
    }
  ];
}
