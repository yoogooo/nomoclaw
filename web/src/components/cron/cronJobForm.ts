import type { ComputedRef } from "vue";
import type { SelectOption } from "naive-ui";
import type { CronJob, ModelConfig } from "@/types/api";
import type { ExecutionType, RecurringMode, TaskTemplate } from "./cronTaskTemplates";

export interface CronJobFormValue {
  agentUid: string;
  title: string;
  taskContent: string;
  status: string;
  timezone: string;
  modelProvider: string;
  modelName: string;
  executionType: ExecutionType;
  recurringMode: RecurringMode;
  time: string;
  weekdays: string[];
  monthlyDay: number;
  monthField: string;
  minuteInterval: number;
  hourInterval: number;
  hourlyMinute: number;
  onceDate: string;
  onceTime: string;
  endAtLocal: string;
}

export interface CronJobFormSubmitPayload {
  agentUid: string;
  title: string;
  expression: string;
  timezone: string;
  endAt?: string;
  modelProvider?: string;
  modelName?: string;
  taskContent: string;
  status: string;
}

export type CronJobFormMode = "create" | "edit";

export interface CronJobFormInitInput {
  mode: CronJobFormMode;
  initialTemplateId?: string | null;
  job?: CronJob | null;
}

export type CronJobFormOption = SelectOption;

export interface CronJobFormDependencies {
  agentOptions: ComputedRef<CronJobFormOption[]>;
  taskTemplates?: ComputedRef<TaskTemplate[]>;
  fallbackTimezone: string;
  t: (key: string) => string;
  loadModelConfig: () => Promise<ModelConfig>;
}
