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

export const cronTaskTemplates: TaskTemplate[] = [
  {
    id: "market-brief",
    label: "晨间简报",
    description: "每天固定时间整理最新动态，适合日报、行情和舆情跟踪。",
    icon: LineChart,
    accent: "var(--color-brand-primary)",
    title: "晨间简报",
    taskContent: "搜索今天最新的重要动态，提炼 3 到 5 条核心信息，按要点输出简报。",
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
    label: "工作提醒",
    description: "定时推送待办提醒、晨会提示或巡检指令。",
    icon: BriefcaseBusiness,
    accent: "var(--color-warning-500)",
    title: "工作提醒",
    taskContent: "提醒我开始处理今天最重要的工作，并给出一个简短的执行清单。",
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
    label: "每周复盘",
    description: "按周生成总结，适合项目进展、销售数据或运营回顾。",
    icon: CalendarRange,
    accent: "var(--color-semantic-success)",
    title: "每周复盘",
    taskContent: "整理本周的关键进展、风险和下周建议，输出一份简洁复盘。",
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
    label: "定时巡检",
    description: "按间隔自动巡检，适合状态检查、值守提醒和告警汇总。",
    icon: Clock3,
    accent: "var(--color-brand-deep)",
    title: "定时巡检",
    taskContent: "检查指定事项是否有异常或新变化，如果有就输出简短说明。",
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
    label: "自定义任务",
    description: "从空白模板开始，自由配置任务内容和执行计划。",
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
