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
    languageEnUS: "English"
  },
  chat: {
    sidebar: {
      history: "Conversations",
      viewingGroup: "Viewing all conversations under {label}.",
      viewingAgent: "Viewing conversations under {label}.",
      createConversation: "New Conversation",
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
      copyFailed: "Copy failed, please try again.",
      saveTipNoAgent: "No target Agent selected, unable to save as tip.",
      copy: "Copy",
      saveTip: "Save as Tip",
      tipSaved: "Tip saved",
      saveTipConfirm: "Save as tip?",
      savedAsTip: "Saved as tip",
      openFile: "Open {name}",
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
    networkError: "Request was not sent successfully: network error or service unavailable. Please check and retry.",
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
      subtitle: "Model configuration is persisted in DB. Provider settings, default model, and capabilities are written to MySQL."
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
