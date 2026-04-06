const zhCN = {
  common: {
    confirm: "确定",
    cancel: "取消",
    save: "保存",
    delete: "删除",
    edit: "编辑",
    refresh: "刷新配置",
    enabled: "已启用",
    disabled: "已停用",
    unknown: "未知",
    success: "成功",
    failed: "失败"
  },
  nav: {
    chat: "对话控制台",
    cron: "定时任务管理",
    agents: "Agent 管理",
    channels: "Channel 管理",
    models: "模型管理",
    settings: "个人配置"
  },
  settings: {
    title: "设置",
    subtitle: "管理当前工作区的账户信息和界面偏好。",
    account: "账户信息",
    currentIdentity: "当前身份",
    defaultTimezone: "默认时区",
    preferences: "界面偏好",
    layoutMode: "布局模式",
    workspaceMode: "聊天工作台",
    theme: "主题风格",
    themeLight: "浅色",
    themeDark: "深色",
    language: "语言",
    languageZhCN: "简体中文",
    languageEnUS: "English"
  },
  chat: {
    sidebar: {
      history: "历史对话",
      viewingGroup: "正在查看 {label} 下的全部对话。",
      viewingAgent: "正在查看 {label} 下的对话记录。",
      createConversation: "创建新对话",
      noConversations: "暂无历史对话",
      unnamed: "未命名对话",
      updatedAt: "更新于 {time}",
      rename: "修改名称",
      renamePrompt: "请输入新的对话名称",
      delete: "删除"
    },
    messages: {
      panelTitle: "对话区",
      noMessages: "当前对话暂无消息。",
      copyFailed: "复制失败，请重试",
      saveTipNoAgent: "当前未选择可归属的 Agent，无法保存锦囊。",
      copy: "复制",
      saveTip: "保存为锦囊",
      tipSaved: "锦囊已保存",
      saveTipConfirm: "保存为锦囊？",
      savedAsTip: "已保存为锦囊",
      openFile: "打开 {name}",
      processing: "处理进行中…",
      runTitle: "执行过程",
      noExtraDetails: "无附加详情"
    },
    composer: {
      placeholder: "例如：请结合我上传的文件，整理出一版可直接使用的结论和建议。",
      upload: "上传文件",
      jinnang: "锦囊",
      send: "发送",
      stop: "取消",
      modelPlaceholder: "选择已配置模型",
      uploadHintUploading: "文件上传中...",
      removeTip: "删除锦囊",
      uploadDisabled: "当前模型不支持上传文件",
      singleTypeOnly: "同一条消息只能上传同一类型的文件",
      unsupportedType: "当前模型不支持该类型文件",
      mixedTypeNotAllowed: "图片和其他文件不能混合上传",
      maxImages: "当前模型最多上传 {count} 张图片",
      maxFiles: "当前模型最多上传 {count} 个非图片文件",
      clearedByModelSwitch: "已清空当前草稿附件，新模型不支持这些文件",
      sameTypeOnlyHint: "同一条消息只能上传同一类型文件。",
      mixedTypeHint: "支持同一条消息上传多种类型文件。",
      uploadLimitHint: "图片最多 {maxImages} 张，非图片文件最多 {maxFiles} 个。{sameTypeHint}",
      switchingConversation: "正在切换会话，请稍候再上传",
      dropToUpload: "松开即可上传文件",
      closePreview: "关闭预览",
      attachmentPreview: "附件预览",
      previewImageAttachment: "预览图片 {name}",
      removeAttachment: "移除附件 {name}"
    },
    runtime: {
      title: "运行时日志",
      subtitle: "统一展示计划生成、审批入口、步骤执行、失败原因和循环终止。",
      expand: "展开日志",
      collapse: "收起日志",
      highRiskStep: "系统准备执行高风险步骤",
      processingStep: "正在处理任务步骤",
      stepNeedApproval: "步骤 {index} 需要人工确认后才能继续执行。",
      approvalRiskHint: "风险说明：此操作可能改动本地环境、页面状态或产生不可逆结果。若与当前意图不符，请直接拒绝。",
      stepPrefix: "步骤 {index}",
      planCreatedWithoutSteps: "计划已生成，但没有可展示的步骤。",
      planCreated: "计划已生成:",
      messageSubmitted: "消息已提交 messageUid={messageUid}",
      approvalSubmitted: "审批已提交: {action} {stepUid}",
      modelToolCall: "模型请求工具调用 (Round {round})",
      stepWaitingApproval: "步骤等待审批: {stepUid}",
      stepStarted: "开始执行: [Round {round}] {title}",
      stepFinished: "步骤完成: {title}\n输出: {output}",
      stepFailed: "步骤失败: {title}\n错误: {errorMessage}",
      stepRejected: "步骤已被拒绝: {title}",
      roundTokenUsage: "Round {round} Token 使用: input={input}, output={output}, total={total}, model={modelName}",
      messageCompleted: "消息处理结束: {status} / {message}",
      stopReason: "停止原因: {reason} ({roundsUsed}/{maxRounds})",
      loopLimitReached: "达到最大循环次数({maxRounds})，任务终止。失败步骤: {failedStepId}",
      messageCanceled: "消息处理已取消"
    },
    approval: {
      waiting: "高风险操作待确认",
      approve: "继续执行",
      approving: "继续执行中",
      reject: "拒绝本次操作",
      rejecting: "拒绝中"
    },
    jinnang: {
      count: "共 {count} 个锦囊",
      empty: "暂无可选锦囊",
      view: "查看",
      closePanel: "关闭锦囊面板",
      closePreview: "关闭查看弹窗"
    },
    runSummary: {
      completed: "已完成 {completedSteps}/{totalSteps} 步",
      failed: "执行失败，已完成 {completedSteps}/{totalSteps} 步",
      rejected: "执行已拒绝，已完成 {completedSteps}/{totalSteps} 步",
      canceled: "已取消，已完成 {completedSteps}/{totalSteps} 步",
      waitingApproval: "等待确认，已完成 {completedSteps}/{totalSteps} 步",
      running: "正在处理，已完成 {completedSteps}/{totalSteps} 步",
      planned: "已规划 {totalSteps} 步，等待开始",
      preparing: "正在准备执行步骤"
    },
    agentSidebar: {
      subtitle: "你的私人助理",
      directChat: "单聊",
      team: "团队"
    }
  },
  cron: {
    weekday: {
      monday: "周一",
      tuesday: "周二",
      wednesday: "周三",
      thursday: "周四",
      friday: "周五",
      saturday: "周六",
      sunday: "周日"
    },
    weekdayShort: {
      monday: "一",
      tuesday: "二",
      wednesday: "三",
      thursday: "四",
      friday: "五",
      saturday: "六",
      sunday: "日"
    },
    executionType: {
      once: "只执行一次",
      recurring: "周期性执行"
    },
    recurringMode: {
      minute: "按分钟",
      hour: "按小时",
      day: "按天",
      week: "按周",
      month: "按月"
    },
    validation: {
      pickDateTime: "请选择执行日期和时间",
      onceAfterNow: "只执行一次的时间必须晚于当前时间",
      invalidMinuteInterval: "请设置有效的分钟间隔",
      invalidHourInterval: "请设置有效的小时间隔",
      pickOneWeekday: "按周执行至少选择一天",
      invalidEndAt: "截止日期格式不正确",
      endAtAfterFirstRun: "截止日期必须晚于首次触发时间"
    },
    list: {
      title: "任务列表",
      subtitle: "按 Agent 分组查看调度中的任务，支持批量管理。",
      createTask: "创建任务",
      createFirstTask: "创建第一个任务",
      selectedCount: "已选 {count} 项",
      deleteSelected: "删除选中",
      bulkManage: "批量管理",
      jobCount: "{count} 个任务",
      nextRunAt: "下次执行 {time}",
      emptyTitle: "当前还没有定时任务",
      emptySubtitle: "创建后可在此统一查看状态、执行记录和执行报告。"
    },
    form: {
      taskInfo: "任务信息",
      executeAgent: "执行 Agent",
      executeAgentPlaceholder: "选择负责执行这个任务的 Agent",
      taskTitle: "任务标题",
      taskTitlePlaceholder: "例如：工作日报、每日市场简报、服务器巡检",
      taskContent: "任务内容",
      taskContentPlaceholder: "直接写你希望它定时帮你做什么。越具体，执行结果越稳定。",
      plan: "执行计划",
      executionType: "执行类型",
      executeDateTime: "执行日期与时间",
      recurringFrequency: "重复频率",
      everyMinutes: "每隔几分钟执行一次",
      everyHours: "每隔几小时执行一次",
      minuteOfHour: "每小时的第几分钟执行",
      minuteOfHourHint: "例如填 22，表示在每小时的 22 分执行。",
      executeTime: "执行时间",
      weekday: "每周哪一天",
      dayOfMonth: "每月几号执行",
      endAtOptional: "截止日期（可选）",
      timezone: "时区",
      planPreview: "计划预览",
      nextRuns: "最近 5 次执行计划",
      noRunnableTime: "暂无可执行时间",
      noAvailableAgent: "当前没有可用 Agent，先去 Agent 页面创建或启用一个 Agent。",
      createTask: "创建任务"
    },
    edit: {
      title: "修改任务"
    },
    detail: {
      title: "任务详情",
      selectOneHint: "请选择左侧的一个定时任务。",
      emptyTitle: "等待任务创建",
      emptySubtitle: "当左侧出现任务后，这里会自动展示第一个任务的完整详情。",
      emptyStep1: "创建并注册一个定时任务",
      emptyStep2: "返回任务列表点击“刷新”",
      emptyStep3: "在详情区查看配置和执行记录",
      paused: "已暂停",
      runNow: "立即执行",
      runNowConfirm: "确认现在执行这个任务吗？",
      editTask: "修改任务",
      deleteTask: "删除任务",
      tabConfig: "配置",
      tabResult: "执行记录",
      frequency: "执行频率",
      nextRun: "下次执行",
      currentStatus: "当前状态",
      endAt: "截止日期",
      noTaskContent: "暂无任务内容",
      systemInfo: "系统信息",
      cronExpression: "Cron 表达式",
      triggerState: "调度状态",
      subscriptionTitle: "通知推送 Channel",
      addSubscription: "新增订阅",
      saveSubscription: "保存订阅",
      noEnabledChannels: "暂无启用中的通道，请先到 Channel 管理页面启用飞书或钉钉。",
      noSubscriptionYet: "暂未配置推送通道，点击“新增订阅”后选择一个通道。",
      noResults: "当前任务还没有执行记录。",
      noResultSummary: "无结果摘要",
      previewResult: "预览结果",
      openReport: "打开报告",
      previewTitle: "执行结果预览 · {time}",
      longRunning: "长期执行",
      channel: {
        feishu: "飞书",
        dingtalk: "钉钉"
      }
    },
    empty: {
      title: "还没有定时任务",
      subtitle: "选择一个常用模板，快速创建你的第一个自动执行任务。",
      createBlank: "创建空白任务"
    },
    templates: {
      marketBrief: {
        label: "晨间简报",
        description: "每天固定时间整理最新动态，适合日报、行情和舆情跟踪。",
        title: "晨间简报",
        taskContent: "搜索今天最新的重要动态，提炼 3 到 5 条核心信息，按要点输出简报。"
      },
      workReminder: {
        label: "工作提醒",
        description: "定时推送待办提醒、晨会提示或巡检指令。",
        title: "工作提醒",
        taskContent: "提醒我开始处理今天最重要的工作，并给出一个简短的执行清单。"
      },
      weeklyReview: {
        label: "每周复盘",
        description: "按周生成总结，适合项目进展、销售数据或运营回顾。",
        title: "每周复盘",
        taskContent: "整理本周的关键进展、风险和下周建议，输出一份简洁复盘。"
      },
      hourlyWatch: {
        label: "定时巡检",
        description: "按间隔自动巡检，适合状态检查、值守提醒和告警汇总。",
        title: "定时巡检",
        taskContent: "检查指定事项是否有异常或新变化，如果有就输出简短说明。"
      },
      custom: {
        label: "自定义任务",
        description: "从空白模板开始，自由配置任务内容和执行计划。"
      }
    },
    batchDeleteSuffix: "\n- 以及其他 {count} 项",
    batchDeleteSuccess: "已删除 {count} 个任务",
    batchDeletePartial: "成功删除 {success} 个任务，失败 {failed} 个"
  },
  http: {
    networkError: "请求未发送成功：网络连接异常或服务不可达，请检查网络/服务后重试。",
    withReason: "{base} 原因：{reason}",
    400: "请求参数有误，请检查输入后重试。",
    401: "登录状态已失效，请重新登录后重试。",
    403: "当前没有该操作权限。",
    404: "请求的资源不存在或已被删除。",
    409: "资源状态冲突，暂时无法完成操作。",
    422: "提交内容未通过校验，请修改后重试。",
    429: "请求过于频繁，请稍后再试。",
    500: "服务暂时不可用，请稍后重试。",
    default: "请求失败（HTTP {status}）。"
  },
  dialogs: {
    deleteConversationTitle: "删除对话",
    deleteConversationContent: "确认删除“{title}”？删除后不可恢复。",
    deleteCronTitle: "删除定时任务",
    deleteCronContent: "确认删除“{title}”？",
    deleteCronBatchTitle: "批量删除任务",
    deleteCronBatchContent: "确认删除选中的 {count} 个定时任务？\n\n{preview}{suffix}",
    deleteAgentTitle: "删除 Agent",
    deleteAgentContent: "你即将删除“{name}”，确认继续操作么？",
    deleteAgentRiskTitle: "删除后数据不可恢复",
    deleteAgentRiskContent: "删除后将同时清理该 Agent 的数据与工作目录文件（含会话、步骤、锦囊、附件、工作区文件）。此操作不可恢复，确认继续吗？",
    confirmDelete: "删除",
    confirmContinue: "继续",
    confirmPermanentDelete: "确认彻底删除"
  },
  toast: {
    configRefreshed: "配置已刷新",
    refreshFailed: "刷新失败",
    saveFailed: "保存失败",
    taskTriggered: "任务已触发执行",
    taskCreated: "任务已创建",
    taskPaused: "任务已暂停",
    taskResumed: "任务已恢复",
    taskUpdated: "任务已更新",
    taskDeleted: "任务已删除",
    subscriptionsSaved: "推送订阅已保存",
    conversationDeleted: "对话已删除",
    tipSavedToDb: "锦囊已保存到数据库",
    tipDeleted: "锦囊已删除",
    tipUpdated: "锦囊已更新",
    docSaved: "文档配置已保存",
    basicSaved: "基本信息已保存到数据库",
    agentCreated: "Agent 已创建",
    defaultAgentDeleteDenied: "默认 Agent 不支持删除",
    agentDeleted: "Agent 已删除，相关数据与工作目录已清理",
    skillImported: "Skill 已导入：{name}",
    approveSuccess: "已批准，任务继续执行",
    approveFailed: "批准失败，请重试",
    rejectSuccess: "已拒绝当前步骤",
    rejectFailed: "拒绝失败，请重试",
    chooseModelFirst: "请先选择模型",
    uploadRuleNotMatch: "文件不符合当前模型上传规则"
  },
  agents: {
    common: {
      noDescription: "暂无描述"
    },
    detail: {
      title: "Agent 详细信息",
      empty: "请先在左侧选择一个 Agent，或新建 Agent。"
    },
    list: {
      title: "Agent 列表",
      newAgent: "新建 Agent",
      empty: "当前没有 Agent",
      member: "成员"
    },
    basic: {
      displayName: "显示名称",
      agentName: "Agent 标识",
      agentNameHint: "创建后不可修改，仅允许字母/数字/下划线/中划线。",
      description: "描述",
      iconSelect: "Icon 选择",
      preview: "实时预览",
      iconStyle: "图标样式",
      themeColor: "主题色"
    },
    create: {
      title: "新建 Agent",
      displayNamePlaceholder: "例如：通用助手",
      agentNamePlaceholder: "例如：agent_general_assistant",
      submit: "创建 Agent"
    },
    skills: {
      description: "导入后可在该 Agent 的系统提示词中按需启用 Skills。",
      import: "导入 Skill",
      empty: "暂无技能"
    },
    tools: {
      empty: "暂无工具"
    },
    tips: {
      titlePlaceholder: "锦囊标题",
      contentPlaceholder: "锦囊内容",
      add: "新增锦囊",
      deleteConfirm: "确认删除「{title}」？",
      empty: "暂无锦囊",
      untitled: "未命名锦囊"
    },
    docs: {
      listTitle: "配置文件清单",
      cancelEdit: "取消编辑",
      enableEdit: "启用编辑",
      saveConfig: "保存配置"
    },
    skillDrawer: {
      status: "状态",
      disabled: "已关闭",
      description: "描述",
      path: "完整路径"
    },
    import: {
      title: "导入 Skill",
      subtitle: "支持从 URL、压缩包或快速创建导入 Skill。",
      tabUrl: "网址导入",
      tabArchive: "压缩包导入",
      tabCreate: "自建 Skill",
      supportedSources: "支持来源",
      urlExamples: "URL 示例",
      urlPlaceholder: "请输入 Skill 仓库或资源地址",
      archiveHint: "请上传 zip 压缩包，系统会自动解析 Skill 目录结构。",
      pickArchive: "选择压缩包",
      skillKeyPlaceholder: "例如：my-skill",
      displayNamePlaceholder: "例如：订单查询助手",
      descriptionPlaceholder: "输入简要描述，帮助识别该 Skill 的用途",
      purpose: "用途说明",
      purposePlaceholder: "说明这个 Skill 用于解决什么问题",
      attachToAgent: "立即启用到当前 Agent",
      createSkill: "创建 Skill",
      importSkill: "导入 Skill"
    },
    docsTemplate: {
      defaultAgentName: "该 Agent",
      defaultRole: "成员",
      soul: "你是 {name} 的内核人格，保持清晰、稳健、可执行。",
      goalTitle: "目标",
      goalItem: "在当前职责范围内完成任务",
      outputRulesTitle: "输出约束",
      outputRulesItem: "先结论，后细节",
      memoryItem1: "记录长期偏好",
      memoryItem2: "记录高价值上下文",
      toolsItem1: "列出允许调用的工具",
      toolsItem2: "列出工具风险边界",
      userItem1: "记录该 Agent 服务对象的偏好、约束与上下文。"
    },
    time: {
      justNow: "刚刚",
      minutesAgo: "{count}分钟前",
      hoursAgo: "{count}小时前",
      daysAgo: "{count}天前",
      monthsAgo: "{count}个月前",
      yearsAgo: "{count}年前"
    }
  },
  channels: {
    cards: {
      feishu: {
        title: "飞书 Channel",
        subtitle: "长连接接收消息，支持 Reaction ACK",
        summaryConfigured: "App ID: {appId}",
        summaryEmpty: "未配置 App ID"
      },
      dingtalk: {
        title: "钉钉 Channel",
        subtitle: "Stream SDK 接收消息，支持会话 webhook 回复",
        summaryConfigured: "Robot Code: {robotCode}",
        summaryEmpty: "未配置 Robot Code"
      },
      mentionPolicy: "@响应策略",
      mentionOnly: "仅 @ 触发",
      allMessages: "所有消息",
      summary: "配置摘要"
    },
    editor: {
      feishuTitle: "编辑飞书通道",
      dingtalkTitle: "编辑钉钉通道",
      enable: "启用",
      enableDingtalk: "启用钉钉通道",
      mentionOnly: "仅响应 @ 机器人",
      feishuAppId: "App ID（启用时必填）",
      feishuAppSecret: "App Secret（启用时必填）",
      feishuAppIdPlaceholder: "cli_xxx",
      allowList: "Allow List（可选，逗号分隔）",
      allowListFeishuPlaceholder: "ou_xxx, ou_yyy",
      dingtalkClientId: "Client ID（启用时必填）",
      dingtalkClientSecret: "Client Secret（启用时必填）",
      dingtalkRobotCode: "Robot Code（启用时必填）",
      dingtalkRobotCodePlaceholder: "dingxxxx",
      allowListDingtalkPlaceholder: "manager001, manager002"
    },
    errors: {
      feishuRequired: "飞书启用时必须填写 appId 和 appSecret",
      dingtalkRequired: "钉钉启用时必须填写 clientId、clientSecret、robotCode"
    },
    toast: {
      saved: "Channel 配置已保存"
    }
  },
  pages: {
    chat: {
      runtimeExpand: "展开日志"
    },
    cron: {
      title: "定时任务",
      subtitle: "按 Agent 分组查看和管理调度任务。"
    },
    agents: {
      title: "Agent 管理",
      subtitle: "统一管理 Agent 的基本信息、技能、工具、锦囊和配置文件。",
      tabBasic: "基本信息",
      tabSkills: "技能",
      tabTools: "工具",
      tabTips: "锦囊",
      tabDocs: "配置文件"
    },
    channels: {
      title: "Channel 管理",
      subtitle: "点击卡片编辑通道信息，保存后写入 ~/.nomoclaw/nomoclaw.json。"
    },
    models: {
      title: "模型管理",
      subtitle: "模型配置已切到数据库持久化。页面编辑的 provider、默认模型和模型能力信息会直接写入 MySQL。"
    },
    forbidden: {
      title: "无权访问",
      description: "这个项目当前只允许来自本机的访问请求。若你是通过非授权地址、代理或远程网络进入，页面和 API 都会被拒绝。",
      reason: "拒绝原因",
      backHome: "返回首页",
      defaultReason: "当前来源地址不在允许范围内，系统已拒绝本次访问。"
    },
    designSystem: {
      light: "Light 页面",
      dark: "Dark 页面"
    }
  },
  errors: {
    loadSystemPathFailed: "系统路径配置加载失败，请检查后端 /api/system/config。",
    loadModelConfigFailed: "模型配置加载失败，请检查后端 /api/system/models。",
    fillAgentName: "请填写 Agent 名称",
    fillAgentIdentifier: "请填写 Agent 标识",
    noAvailableModel: "系统中还没有可用模型，暂时无法创建 Agent",
    fillTipTitleAndContent: "请填写锦囊标题和内容",
    fillDisplayName: "请填写显示名称",
    noModelForAgent: "当前 Agent 缺少可用模型，请先在系统模型管理中配置模型"
  },
  format: {
    yesterday: "昨日 {time}",
    cronFallback: "按自定义计划执行",
    statusActive: "已启用",
    statusPaused: "已暂停",
    statusUnknown: "未知状态",
    timezoneShanghai: "中国标准时间",
    timezoneUtc: "协调世界时",
    fallbackNoAgent: "未绑定 Agent",
    fallbackNoName: "未命名任务",
    approval: {
      tool: "工具",
      action: "操作",
      urlMissing: "未提供 URL",
      selectorMissing: "未提供 selector",
      textMissing: "未提供文本",
      defaultTempPath: "默认临时路径",
      keyMissing: "未提供 key",
      commandMissing: "未提供命令",
      pathMissing: "未提供路径",
      taskMissing: "未提供任务说明",
      cronMissing: "未提供 cron",
      workingDirectory: "工作目录",
      browser: {
        open: "即将打开网页：{url}",
        click: "即将点击页面元素：{selector}",
        type: "即将在 {selector} 输入：{text}",
        extract: "即将读取页面元素文字：{selector}",
        screenshot: "即将保存页面截图到：{output}",
        waitFor: "即将等待页面元素出现：{selector}",
        pressKey: "即将向页面发送按键：{key}",
        generic: "即将执行浏览器动作：{action}"
      },
      command: "即将执行本地命令：{command}{cwd}",
      file: "即将对本地文件执行 {action}：{path}",
      cron: "即将创建定时任务：{task}\n执行表达式：{expression}",
      generic: "即将调用工具 {toolName} 执行一步高风险操作。"
    }
  },
} as const;

export default zhCN;
