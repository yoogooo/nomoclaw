const enUS = {
  common: {
    confirm: "Confirm",
    cancel: "Cancel",
    save: "Save",
    delete: "Delete",
    edit: "Edit",
    refresh: "Refresh",
    enabled: "Enabled",
    disabled: "Disabled",
    unknown: "Unknown",
    success: "Success",
    failed: "Failed"
  },
  nav: {
    chat: "Chat Console",
    cron: "Scheduled Tasks",
    agents: "Agent Management",
    channels: "Channel Management",
    models: "Model Management",
    settings: "Settings"
  },
  settings: {
    title: "Settings",
    subtitle: "Manage account information and interface preferences for this workspace.",
    account: "Account",
    currentIdentity: "Current Identity",
    defaultTimezone: "Default Timezone",
    preferences: "Interface Preferences",
    layoutMode: "Layout Mode",
    workspaceMode: "Chat Workspace",
    theme: "Theme",
    themeLight: "Light",
    themeDark: "Dark",
    language: "Language",
    languageZhCN: "简体中文",
    languageEnUS: "English",
    runtimeLogVisibility: "Runtime Log",
    runtimeLogOn: "Visible",
    runtimeLogOff: "Hidden",
    updateCheckNow: "Check Updates",
    updateRetry: "Retry",
    updateInstallNow: "Install Update",
    updateDownloading: "Downloading update {progress}",
    updateVersion: "New version {version}",
    updateFailed: "Update failed"
  },
  modelGate: {
    title: "Set up a model first",
    description: "Model setup only takes about a minute. Once done, Chat, Agents, Scheduled Tasks, and Channels are ready to use.",
    status: {
      missingModel: "No available model detected yet. Open Model Management to complete setup.",
      checkFailed: "Failed to check model readiness. Try again later or open Model Management directly."
    },
    actions: {
      goConfig: "Go to Model Config",
      dismissForever: "Don't show again"
    }
  },
  chat: {
    sidebar: {
      history: "Conversations",
      viewingGroup: "Viewing all conversations under {label}.",
      viewingAgent: "Viewing conversations under {label}.",
      createConversation: "+ New Chat",
      refreshHistory: "Refresh Conversation History",
      refreshHistoryTooltip: "Refresh history (right-click anywhere on page: reload whole page)",
      noConversations: "No conversation history",
      unnamed: "Untitled Conversation",
      updatedAt: "Updated at {time}",
      rename: "Rename",
      renamePrompt: "Enter a new conversation name",
      delete: "Delete"
    },
    messages: {
      panelTitle: "Conversation",
      noMessages: "No messages in this conversation yet.",
      newConversationTitle: "Start a new conversation",
      newConversationHint: "This is a new conversation. Use a starter prompt to begin.",
      starterTemplate1Title: "Daily AI News Digest",
      starterTemplate1Summary: "Set up a recurring morning task that tracks the day's AI headlines. It summarizes key updates in a consistent, easy-to-scan format.",
      starterTemplate1Prompt: "Create a recurring task that runs every day at 8:00 AM: find the latest AI headlines of the day, select the top 10 most relevant updates, and output each with title, one-line summary, source link, and impact note.",
      starterTemplate2Title: "Problem Analysis",
      starterTemplate2Summary: "Useful for work or life decisions with multiple constraints. It identifies the core issue, compares practical options with pros and cons, and points to the best next step.",
      starterTemplate2Prompt: "I will describe a problem. First summarize the core issue, then propose 3 solution options with pros and cons, and finally recommend the best next action.",
      starterTemplate3Title: "E-commerce Homepage Build",
      starterTemplate3Summary: "Ideal for building a brand site homepage from scratch. It outputs IA, section layout, and copy direction so you can move directly into design or implementation.",
      starterTemplate3Prompt: "Create a homepage for a North America-focused 3C accessories DTC store: provide IA first (nav + section hierarchy), then a homepage wireframe including Hero value props, bestseller block, social proof, shipping/returns policy, FAQ, and primary CTA copy; then generate the actual runnable page based on that IA and layout, and automatically open a preview for me.",
      copyFailed: "Copy failed, please try again.",
      saveTipNoAgent: "No target Agent selected, unable to save as tip.",
      copy: "Copy",
      saveTip: "Save as Tip",
      tipSaved: "Tip saved",
      saveTipConfirm: "Save as tip?",
      savedAsTip: "Saved as tip",
      openFile: "Open {name}",
      expandMessage: "Expand",
      collapseMessage: "Collapse",
      processing: "Processing...",
      runTitle: "Execution",
      noExtraDetails: "No extra details"
    },
    composer: {
      placeholder: "For example: summarize the uploaded file into directly actionable conclusions and recommendations.",
      upload: "Upload",
      jinnang: "Tips",
      send: "Send",
      stop: "Cancel",
      modelPlaceholder: "Select a configured model",
      uploadHintUploading: "Uploading files...",
      removeTip: "Remove tip",
      uploadDisabled: "The current model does not support file upload",
      singleTypeOnly: "Only one file type can be uploaded per message",
      unsupportedType: "This file type is not supported by the current model",
      mixedTypeNotAllowed: "Images and other files cannot be mixed",
      maxImages: "At most {count} images can be uploaded",
      maxFiles: "At most {count} non-image files can be uploaded",
      clearedByModelSwitch: "Draft attachments were cleared because the new model does not support them",
      sameTypeOnlyHint: "Only one file type can be uploaded per message.",
      mixedTypeHint: "Multiple file types are supported in one message.",
      uploadLimitHint: "Up to {maxImages} images and {maxFiles} non-image files per message. {sameTypeHint}",
      switchingConversation: "Switching conversation, please try upload in a moment",
      dropToUpload: "Release to upload files",
      closePreview: "Close preview",
      attachmentPreview: "Attachment preview",
      previewImageAttachment: "Preview image {name}",
      removeAttachment: "Remove attachment {name}"
    },
    runtime: {
      title: "Runtime Log",
      subtitle: "Shows planning, approvals, step execution, failure reasons, and loop termination.",
      expand: "Expand log",
      collapse: "Collapse log",
      browserRuntime: {
        checkingTitle: "Preparing browser environment",
        downloadingTitle: "Downloading browser runtime",
        checkingDesc: "Checking dependencies…",
        downloadingDescSimple: "Please wait, downloading now.",
        elapsedNow: "just started",
        elapsedSeconds: "about {seconds} seconds in",
        elapsedMinutes: "about {minutes} minutes in",
        elapsedMinutesSeconds: "about {minutes}m {seconds}s in",
        oneTimeHint: "Usually only once. Later runs reuse local cache."
      },
      highRiskStep: "System is preparing a high-risk step",
      processingStep: "Processing task step",
      stepNeedApproval: "Step {index} requires manual approval before continuing.",
      approvalRiskHint: "Risk notice: this action may modify local environment, page state, or trigger irreversible results. Reject if it does not match your intent.",
      stepPrefix: "Step {index}",
      planCreatedWithoutSteps: "Plan created, but no displayable steps.",
      planCreated: "Plan created:",
      messageSubmitted: "Message submitted messageUid={messageUid}",
      approvalSubmitted: "Approval submitted: {action} {stepUid}",
      modelToolCall: "Model requested tool call (Round {round})",
      stepWaitingApproval: "Step waiting for approval: {stepUid}",
      stepStarted: "Step started: [Round {round}] {title}",
      stepFinished: "Step finished: {title}\nOutput: {output}",
      stepFailed: "Step failed: {title}\nError: {errorMessage}",
      stepRejected: "Step rejected: {title}",
      roundTokenUsage: "Round {round} token usage: input={input}, output={output}, total={total}, model={modelName}",
      messageCompleted: "Message completed: {status} / {message}",
      stopReason: "Stop reason: {reason} ({roundsUsed}/{maxRounds})",
      loopLimitReached: "Loop limit reached ({maxRounds}). Task terminated. Failed step: {failedStepId}",
      messageCanceled: "Message processing canceled"
    },
    approval: {
      waiting: "High-risk action pending confirmation",
      approve: "Continue",
      approving: "Continuing",
      reject: "Reject this action",
      rejecting: "Rejecting"
    },
    jinnang: {
      count: "{count} tips",
      empty: "No tips available",
      view: "View",
      closePanel: "Close tips panel",
      closePreview: "Close preview"
    },
    runSummary: {
      completed: "Completed {completedSteps}/{totalSteps} steps",
      failed: "Failed after completing {completedSteps}/{totalSteps} steps",
      rejected: "Rejected after completing {completedSteps}/{totalSteps} steps",
      canceled: "Canceled after completing {completedSteps}/{totalSteps} steps",
      waitingApproval: "Waiting for approval, completed {completedSteps}/{totalSteps} steps",
      running: "Running, completed {completedSteps}/{totalSteps} steps",
      planned: "Planned {totalSteps} steps, waiting to start",
      preparing: "Preparing execution steps"
    },
    agentSidebar: {
      subtitle: "Your personal assistant",
      directChat: "Direct",
      team: "Team"
    }
  },
  cron: {
    weekday: {
      monday: "Monday",
      tuesday: "Tuesday",
      wednesday: "Wednesday",
      thursday: "Thursday",
      friday: "Friday",
      saturday: "Saturday",
      sunday: "Sunday"
    },
    weekdayShort: {
      monday: "Mon",
      tuesday: "Tue",
      wednesday: "Wed",
      thursday: "Thu",
      friday: "Fri",
      saturday: "Sat",
      sunday: "Sun"
    },
    executionType: {
      once: "Run once",
      recurring: "Recurring"
    },
    recurringMode: {
      minute: "By minute",
      hour: "By hour",
      day: "By day",
      week: "By week",
      month: "By month"
    },
    validation: {
      pickDateTime: "Please select execution date and time",
      onceAfterNow: "One-time execution must be later than now",
      invalidMinuteInterval: "Please set a valid minute interval",
      invalidHourInterval: "Please set a valid hour interval",
      pickOneWeekday: "Select at least one weekday",
      invalidEndAt: "Invalid end date format",
      endAtAfterFirstRun: "End date must be later than the first run"
    },
    list: {
      title: "Task List",
      subtitle: "View scheduled tasks grouped by Agent and manage them in bulk.",
      createTask: "Create Task",
      createFirstTask: "Create First Task",
      selectedCount: "{count} selected",
      deleteSelected: "Delete Selected",
      bulkManage: "Bulk Manage",
      jobCount: "{count} tasks",
      nextRunAt: "Next run {time}",
      emptyTitle: "No scheduled tasks yet",
      emptySubtitle: "After creating tasks, view status, execution logs, and reports here."
    },
    form: {
      taskInfo: "Task Info",
      executeAgent: "Execute Agent",
      executeAgentPlaceholder: "Select an Agent to execute this task",
      taskTitle: "Task Title",
      taskTitlePlaceholder: "e.g. Daily report, market brief, server inspection",
      taskContent: "Task Content",
      taskContentPlaceholder: "Describe exactly what you want it to do. More specific prompts produce more stable results.",
      plan: "Execution Plan",
      executionType: "Execution Type",
      executeDateTime: "Execution Date & Time",
      recurringFrequency: "Recurring Frequency",
      everyMinutes: "Run every N minutes",
      everyHours: "Run every N hours",
      minuteOfHour: "Minute of each hour",
      minuteOfHourHint: "For example, 22 means run at minute 22 of each hour.",
      executeTime: "Execution Time",
      weekday: "Weekday",
      dayOfMonth: "Day of month",
      endAtOptional: "End date (optional)",
      timezone: "Timezone",
      planPreview: "Plan Preview",
      nextRuns: "Next 5 runs",
      noRunnableTime: "No runnable time",
      noAvailableAgent: "No available Agent. Please create or enable one on the Agent page first.",
      createTask: "Create Task"
    },
    edit: {
      title: "Edit Task"
    },
    detail: {
      title: "Task Details",
      selectOneHint: "Select one scheduled task from the left.",
      emptyTitle: "Waiting for task creation",
      emptySubtitle: "When tasks appear on the left, full details of the first task will be shown here.",
      emptyStep1: "Create and register a scheduled task",
      emptyStep2: "Return to task list and click refresh",
      emptyStep3: "Review configuration and execution records in details",
      paused: "Paused",
      runNow: "Run Now",
      runNowConfirm: "Execute this task now?",
      editTask: "Edit Task",
      deleteTask: "Delete Task",
      tabConfig: "Config",
      tabResult: "Execution Logs",
      frequency: "Frequency",
      nextRun: "Next Run",
      currentStatus: "Current Status",
      endAt: "End Date",
      noTaskContent: "No task content",
      systemInfo: "System Info",
      cronExpression: "Cron Expression",
      triggerState: "Trigger State",
      subscriptionTitle: "Notification Channel",
      addSubscription: "Add Subscription",
      saveSubscription: "Save Subscription",
      noEnabledChannels: "No enabled channels. Enable Feishu or DingTalk in Channel Management first.",
      noSubscriptionYet: "No push channel configured yet. Click Add Subscription to select one.",
      targetRequired: "No delivery target selected. Please choose a contact/group or enter target manually.",
      botRequired: "No bot selected. Please select an available bot first.",
      botPlaceholder: "Select bot",
      targetSearchPlaceholder: "Search Feishu contacts, groups, or bot name",
      targetManualPlaceholder: "Enter target manually (webhook/chat_id/open_id)",
      targetCustomValue: "Manual value",
      searchTargetError: "Target search failed. Switch to manual input.",
      searchTargetFallback: "Directory search unavailable. Manual input is still available.",
      targetMode: {
        search: "Search Directory",
        manual: "Manual Input"
      },
      targetKind: {
        group: "Group",
        user: "User",
        webhook: "Webhook"
      },
      targetSource: {
        platform: "Platform",
        history: "History"
      },
      noResults: "No execution records yet.",
      noResultSummary: "No result summary",
      previewResult: "Preview Result",
      openReport: "Open Report",
      previewTitle: "Execution Result Preview · {time}",
      longRunning: "Long-term",
      channel: {
        feishu: "Feishu",
        dingtalk: "DingTalk"
      }
    },
    empty: {
      title: "No scheduled tasks yet",
      subtitle: "Pick a common template to quickly create your first automation task.",
      createBlank: "Create blank task"
    },
    templates: {
      marketBrief: {
        label: "Morning Brief",
        description: "Summarize key updates at a fixed time every day.",
        title: "Morning Brief",
        taskContent: "Search today's key updates, extract 3-5 highlights, and output a concise brief."
      },
      workReminder: {
        label: "Work Reminder",
        description: "Send periodic todo reminders, standup prompts, or inspection instructions.",
        title: "Work Reminder",
        taskContent: "Remind me to start today's most important work and provide a short checklist."
      },
      weeklyReview: {
        label: "Weekly Review",
        description: "Generate weekly summaries for project progress or operations review.",
        title: "Weekly Review",
        taskContent: "Summarize this week's progress, risks, and next-week suggestions."
      },
      hourlyWatch: {
        label: "Timed Check",
        description: "Run periodic checks for status, on-duty reminders, and alert rollups.",
        title: "Timed Check",
        taskContent: "Check designated items for anomalies or changes and output a short report if needed."
      },
      custom: {
        label: "Custom Task",
        description: "Start from a blank template and define task content and schedule freely."
      }
    },
    batchDeleteSuffix: "\n- and {count} more",
    batchDeleteSuccess: "{count} tasks deleted",
    batchDeletePartial: "{success} tasks deleted, {failed} failed"
  },
  http: {
    networkError: "Cannot reach backend service (it may not be started). Start the service and try again.",
    withReason: "{base} Reason: {reason}",
    400: "Invalid request parameters. Please review your input and retry.",
    401: "Session expired. Please sign in again.",
    403: "You do not have permission for this action.",
    404: "Requested resource does not exist or has been removed.",
    409: "Resource state conflict. Operation cannot be completed now.",
    422: "Submitted content failed validation. Please revise and retry.",
    429: "Too many requests. Please try again later.",
    500: "Service is temporarily unavailable. Please try again later.",
    default: "Request failed (HTTP {status})."
  },
  dialogs: {
    deleteConversationTitle: "Delete Conversation",
    deleteConversationContent: "Delete “{title}”? This action cannot be undone.",
    deleteCronTitle: "Delete Scheduled Task",
    deleteCronContent: "Delete “{title}”?",
    deleteCronBatchTitle: "Batch Delete Tasks",
    deleteCronBatchContent: "Delete {count} selected scheduled tasks?\n\n{preview}{suffix}",
    deleteAgentTitle: "Delete Agent",
    deleteAgentContent: "You are about to delete “{name}”. Continue?",
    deleteAgentRiskTitle: "This action cannot be undone",
    deleteAgentRiskContent: "Deletion will also remove this Agent's data and workspace files (conversations, steps, tips, attachments, workspace files). Continue?",
    confirmDelete: "Delete",
    confirmContinue: "Continue",
    confirmPermanentDelete: "Delete Permanently"
  },
  toast: {
    configRefreshed: "Configuration refreshed",
    refreshFailed: "Refresh failed",
    saveFailed: "Save failed",
    taskTriggered: "Task execution triggered",
    taskCreated: "Task created",
    taskPaused: "Task paused",
    taskResumed: "Task resumed",
    taskUpdated: "Task updated",
    taskDeleted: "Task deleted",
    subscriptionsSaved: "Subscriptions saved",
    conversationDeleted: "Conversation deleted",
    tipSavedToDb: "Tip saved to database",
    tipDeleted: "Tip deleted",
    tipUpdated: "Tip updated",
    docSaved: "Document configuration saved",
    basicSaved: "Basic information saved",
    agentCreated: "Agent created",
    defaultAgentDeleteDenied: "Default Agent cannot be deleted",
    agentDeleted: "Agent deleted; related data and workspace files were cleaned up",
    skillImported: "Skill imported: {name}",
    approveSuccess: "Approved, task resumed",
    approveFailed: "Approve failed, please try again",
    rejectSuccess: "Current step rejected",
    rejectFailed: "Reject failed, please try again",
    chooseModelFirst: "Please select a model first",
    uploadRuleNotMatch: "Files do not match current model upload policy"
  },
  agents: {
    common: {
      noDescription: "No description"
    },
    detail: {
      title: "Agent Details",
      empty: "Select an Agent on the left, or create a new one first."
    },
    list: {
      title: "Agent List",
      newAgent: "New Agent",
      empty: "No agents yet",
      member: "Member"
    },
    basic: {
      displayName: "Display Name",
      agentName: "Agent Identifier",
      agentNameHint: "Immutable after creation. Only letters, numbers, underscore, and hyphen are allowed.",
      description: "Description",
      iconSelect: "Icon",
      preview: "Live Preview",
      iconStyle: "Icon Style",
      themeColor: "Theme Color"
    },
    create: {
      title: "Create Agent",
      displayNamePlaceholder: "e.g. General Assistant",
      agentNamePlaceholder: "e.g. agent_general_assistant",
      submit: "Create Agent"
    },
    skills: {
      description: "Imported skills can be enabled in this Agent's system prompt as needed.",
      import: "Import Skill",
      empty: "No skills"
    },
    tools: {
      empty: "No tools"
    },
    tips: {
      titlePlaceholder: "Tip title",
      contentPlaceholder: "Tip content",
      add: "Add Tip",
      deleteConfirm: "Delete \"{title}\"?",
      empty: "No tips",
      untitled: "Untitled Tip"
    },
    docs: {
      listTitle: "Config Files",
      cancelEdit: "Cancel Editing",
      enableEdit: "Enable Editing",
      saveConfig: "Save Config"
    },
    skillDrawer: {
      status: "Status",
      disabled: "Disabled",
      description: "Description",
      path: "Full Path"
    },
    import: {
      title: "Import Skill",
      subtitle: "Import skills from URL, archive, or quick-create.",
      tabUrl: "Import by URL",
      tabArchive: "Import Archive",
      tabCreate: "Create Skill",
      supportedSources: "Supported Sources",
      urlExamples: "URL Examples",
      urlPlaceholder: "Enter a Skill repository or resource URL",
      archiveHint: "Upload an archive. The system will parse the Skill directory automatically.",
      pickArchive: "Select archive",
      archiveSupportFormat: "Supports .zip / .tar.gz / .tgz",
      archiveChooseButton: "Choose file",
      archiveSelectedLabel: "Selected file",
      archiveNotSelected: "No file selected",
      skillKeyPlaceholder: "e.g. my-skill",
      displayNamePlaceholder: "e.g. Order Query Assistant",
      descriptionPlaceholder: "Add a brief description to identify this Skill",
      purpose: "Purpose",
      purposePlaceholder: "Describe what problem this Skill solves",
      attachToAgent: "Enable for current Agent immediately",
      createSkill: "Create Skill",
      importSkill: "Import Skill"
    },
    docsTemplate: {
      defaultAgentName: "this Agent",
      defaultRole: "member",
      soul: "You are the core persona of {name}. Stay clear, stable, and actionable.",
      goalTitle: "Goal",
      goalItem: "Complete tasks within the current responsibility scope",
      outputRulesTitle: "Output Rules",
      outputRulesItem: "Conclusion first, then details",
      memoryItem1: "Record long-term preferences",
      memoryItem2: "Record high-value context",
      toolsItem1: "List tools allowed to call",
      toolsItem2: "List risk boundaries for tools",
      userItem1: "Record the preferences, constraints, and context of this Agent's target users."
    },
    time: {
      justNow: "Just now",
      minutesAgo: "{count} min ago",
      hoursAgo: "{count} hr ago",
      daysAgo: "{count} day(s) ago",
      monthsAgo: "{count} month(s) ago",
      yearsAgo: "{count} year(s) ago"
    }
  },
  channels: {
    cards: {
      feishu: {
        title: "Feishu Channel",
        subtitle: "Receives messages via long connection and supports reaction ACK",
        summaryConfigured: "App ID: {appId}",
        summaryEmpty: "App ID not configured"
      },
      dingtalk: {
        title: "DingTalk Channel",
        subtitle: "Receives messages via Stream SDK and supports webhook replies",
        summaryConfigured: "Robot Code: {robotCode}",
        summaryEmpty: "Robot Code not configured"
      },
      mentionPolicy: "@ mention policy",
      mentionOnly: "Only when @ mentioned",
      allMessages: "All messages",
      summary: "Configuration summary"
    },
    editor: {
      feishuTitle: "Edit Feishu Channel",
      dingtalkTitle: "Edit DingTalk Channel",
      enable: "Enable",
      enableDingtalk: "Enable DingTalk channel",
      mentionOnly: "Respond only when @ bot",
      feishuAppId: "App ID (required when enabled)",
      feishuAppSecret: "App Secret (required when enabled)",
      feishuAppIdPlaceholder: "cli_xxx",
      allowList: "Allow List (optional, comma-separated)",
      allowListFeishuPlaceholder: "ou_xxx, ou_yyy",
      addBot: "Add Bot",
      setDefault: "Set Default",
      defaultBot: "Default Bot",
      botCount: "{count} bots",
      botName: "Bot Name",
      botId: "Bot ID",
      openIdLabel: "Open ID",
      openIdMissing: "Not resolved yet (auto-resolve on save)",
      dingtalkClientId: "Client ID (required when enabled)",
      dingtalkClientSecret: "Client Secret (required when enabled)",
      dingtalkRobotCode: "Robot Code (required when enabled)",
      dingtalkRobotCodePlaceholder: "dingxxxx",
      allowListDingtalkPlaceholder: "manager001, manager002"
    },
    errors: {
      feishuRequired: "When Feishu is enabled, appId and appSecret are required",
      dingtalkRequired: "When DingTalk is enabled, clientId, clientSecret, and robotCode are required"
    },
    toast: {
      saved: "Channel configuration saved"
    }
  },
  models: {
    status: {
      configured: "Configured",
      pendingLocalUrl: "Local URL required",
      pendingApiKey: "API key required"
    },
    actions: {
      editConfig: "Edit Config",
      loadLocalModels: "Load Local Models",
      addModel: "Add Model",
      testConnection: "Test Connection"
    },
    labels: {
      defaultModel: "Default Model",
      notSet: "Not set",
      modelCount: "Model Count",
      modelList: "Model List",
      modelIndex: "Model {index}",
      displayName: "Display Name",
      urlPolicy: "URL Policy",
      urlPolicyFixed: "Official endpoint only",
      urlPolicyCustom: "Customizable",
      uploadPolicyTitle: "File Upload Policy",
      uploadEnabled: "Upload Enabled",
      singleMimeGroupOnly: "Single type only",
      allowMixedImageAndFile: "Allow mixed image and file",
      allowedFileTypes: "Allowed file types",
      maxFilesPerMessage: "Max non-image files",
      maxImagesPerMessage: "Max images"
    },
    editor: {
      defaultTitle: "Edit Model Provider",
      freezeUrlHint: "This provider uses an official endpoint and cannot be modified on this page.",
      customUrlHint: "This provider supports custom Base URL.",
      apiKeyPlaceholder: "Enter API key",
      defaultModelPlaceholder: "Select default model",
      capabilitiesPlaceholder: "Select capabilities",
      allowedFileTypesPlaceholder: "e.g. image / pdf / text"
    },
    errors: {
      baseUrlRequired: "{name} requires Base URL",
      apiKeyRequired: "{name} requires API key",
      modelIdRequired: "{name} has entries with empty model ID",
      defaultModelNotInList: "{name} default model is not in the model list"
    },
    toast: {
      saved: "Model configuration saved",
      localModelsLoaded: "Local Ollama models loaded",
      localModelsLoadFailed: "Failed to load local models",
      connectionTestSuccess: "Connection test succeeded",
      connectionTestFailed: "Connection test failed"
    }
  },
  pages: {
    chat: {
      runtimeExpand: "Expand log"
    },
    cron: {
      title: "Scheduled Tasks",
      subtitle: "View and manage scheduled jobs by Agent."
    },
    agents: {
      title: "Agent Management",
      subtitle: "Manage Agent profile, skills, tools, tips, and config files.",
      tabBasic: "Basic",
      tabSkills: "Skills",
      tabTools: "Tools",
      tabTips: "Tips",
      tabDocs: "Config Files"
    },
    channels: {
      title: "Channel Management",
      subtitle: "Edit channel settings and save to ~/.nomoclaw/nomoclaw.json."
    },
    models: {
      title: "Model Management",
      subtitle: "Manage available models and default options here. Changes take effect immediately after saving."
    },
    forbidden: {
      title: "Access Denied",
      description: "This project only allows local access. Requests from unauthorized hosts, proxies, or remote networks are rejected.",
      reason: "Reason",
      backHome: "Back to Home",
      defaultReason: "The source address is not allowed. Access is denied."
    },
    designSystem: {
      light: "Light Page",
      dark: "Dark Page"
    }
  },
  errors: {
    loadSystemPathFailed: "Failed to load system path config. Please check backend /api/system/config.",
    loadModelConfigFailed: "Failed to load model config. Please check backend /api/system/models.",
    fillAgentName: "Please enter Agent name",
    fillAgentIdentifier: "Please enter Agent identifier",
    noAvailableModel: "No available model is configured yet. Cannot create Agent.",
    fillTipTitleAndContent: "Please fill both tip title and content",
    fillDisplayName: "Please fill display name",
    noModelForAgent: "Current Agent has no available model. Please configure models first."
  },
  format: {
    yesterday: "Yesterday {time}",
    cronFallback: "Run on custom schedule",
    statusActive: "Enabled",
    statusPaused: "Paused",
    statusUnknown: "Unknown status",
    timezoneShanghai: "China Standard Time",
    timezoneUtc: "Coordinated Universal Time",
    fallbackNoAgent: "No Agent bound",
    fallbackNoName: "Untitled Task",
    approval: {
      tool: "Tool",
      action: "action",
      urlMissing: "URL not provided",
      selectorMissing: "selector not provided",
      textMissing: "text not provided",
      defaultTempPath: "default temp path",
      keyMissing: "key not provided",
      commandMissing: "command not provided",
      pathMissing: "path not provided",
      taskMissing: "task not provided",
      cronMissing: "cron not provided",
      workingDirectory: "Working directory",
      browser: {
        open: "Will open webpage: {url}",
        click: "Will click element: {selector}",
        type: "Will type in {selector}: {text}",
        extract: "Will read text from element: {selector}",
        screenshot: "Will save screenshot to: {output}",
        waitFor: "Will wait for element: {selector}",
        pressKey: "Will send key to page: {key}",
        generic: "Will execute browser action: {action}"
      },
      command: "Will execute local command: {command}{cwd}",
      file: "Will execute {action} on local file: {path}",
      cron: "Will create scheduled task: {task}\nExpression: {expression}",
      generic: "Will call tool {toolName} for a high-risk operation."
    }
  }
} as const;

export default enUS;
