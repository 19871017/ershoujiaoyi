export interface ReportHandlingTarget {
  auditType?: string | null
  targetType?: string | null
  targetId?: string | number | null
}

export interface ReportHandlingGuide {
  targetLabel: string
  traceHint: string
  steps: string[]
  remarkTemplates: string[]
}

const reportTypeLabels: Record<string, string> = {
  PRODUCT: '商品举报',
  GOODS: '商品举报',
  ORDER: '订单举报',
  TRADE_ORDER: '订单举报',
  AFTER_SALES: '售后举报',
  USER: '用户举报',
  CHAT: '私聊举报',
  COMMUNITY_POST: '社区帖子举报',
  COMMUNITY_COMMENT: '社区评论举报',
  POST: '社区帖子举报',
  COMMENT: '社区评论举报',
  REPORT: '举报复核'
}

export function reportHandlingGuide(target: ReportHandlingTarget): ReportHandlingGuide | null {
  if (String(target.auditType || '').toUpperCase() !== 'REPORT') return null
  const targetType = String(target.targetType || '').toUpperCase()
  const targetId = String(target.targetId || '').trim()
  if (!targetType || !targetId || /(preview|demo|mock|sample|placeholder)/i.test(targetId)) return null
  const targetLabel = reportTypeLabels[targetType] || '举报处理'
  if (targetType === 'ORDER' || targetType === 'TRADE_ORDER') {
    return {
      targetLabel,
      traceHint: '先打开订单追溯，核对买卖双方、订单状态、售后状态和举报凭证。',
      steps: ['核对订单是否真实存在且与举报人相关', '比对举报凭证、聊天内容和订单履约状态', '处理后在审核备注中写明订单编号、处置依据和是否需要继续售后协调'],
      remarkTemplates: ['订单举报属实，已完成订单链路复核，建议进入售后/风控跟进。', '订单举报证据不足，暂不调整订单状态，保留审计记录。']
    }
  }
  if (targetType === 'AFTER_SALES') {
    return {
      targetLabel,
      traceHint: '先打开售后追溯，再反查关联订单和双方用户。',
      steps: ['核对售后申请人、订单编号、退款金额和上传票据', '确认举报内容是否影响售后审核结论', '处理后在审核备注中写明售后编号、证据结论和后续协调动作'],
      remarkTemplates: ['售后举报属实，已复核售后凭证与订单链路，建议按售后规则继续处理。', '售后举报证据不足，维持当前售后状态并保留记录。']
    }
  }
  if (targetType === 'PRODUCT' || targetType === 'GOODS') {
    return {
      targetLabel,
      traceHint: '优先通过订单追溯查看该商品关联交易；商品本身处置仍以商品审核和真实商品记录为准。',
      steps: ['核对商品编号、标题、描述和举报凭证是否一致', '检查是否已有订单或售后关联，避免误伤已履约交易', '处理后在审核备注中写明商品编号、违规点和是否需要下架/继续观察'],
      remarkTemplates: ['商品举报属实，商品信息与凭证不一致，建议下架或驳回商品审核。', '商品举报证据不足，暂不下架，保留举报记录。']
    }
  }
  if (targetType === 'USER') {
    return {
      targetLabel,
      traceHint: '通过用户关联订单/售后追溯判断是否存在重复风险；用户资料页只展示脱敏信息。',
      steps: ['核对被举报用户的订单、售后和资料状态', '检查是否存在重复举报或异常交易模式', '处理后在审核备注中写明用户编号、风险点和是否需要限制账号'],
      remarkTemplates: ['用户举报属实，已核对关联订单/售后，建议进入账号风控。', '用户举报证据不足，暂不限制账号，保留审计记录。']
    }
  }
  if (targetType === 'CHAT') {
    return {
      targetLabel,
      traceHint: '先打开私聊追溯，核对双方资料、文字消息、语音消息和举报凭证。',
      steps: ['核对举报凭证是否来自真实聊天场景', '通过私聊追溯查看双方用户资料、文字和语音消息', '处理后在审核备注中写明聊天编号、核查依据和是否需要账号风控'],
      remarkTemplates: ['私聊举报属实，聊天凭证存在违规内容，建议进入账号风控。', '私聊举报证据不足，暂不处理账号，保留记录待补充。']
    }
  }
  if (targetType === 'COMMUNITY_POST' || targetType === 'COMMUNITY_COMMENT' || targetType === 'POST' || targetType === 'COMMENT') {
    return {
      targetLabel,
      traceHint: '先打开社区追溯，核对帖子作者、正文、图片、评论和关联商品。',
      steps: ['核对举报目标是否来自真实社区帖子或评论', '通过社区追溯查看帖子内容、评论上下文和上传图片', '处理后在审核备注中写明帖子编号、评论编号、违规点和是否需要账号风控'],
      remarkTemplates: ['社区举报属实，已核对帖子/评论上下文，建议进入内容处置或账号风控。', '社区举报证据不足，暂不处理内容，保留记录待补充。']
    }
  }
  return {
    targetLabel,
    traceHint: '当前目标类型暂无专属追溯页，请基于审核记录、举报凭证和审计日志人工核查。',
    steps: ['确认目标编号来自真实业务记录', '核对举报原因与上传凭证是否一致', '处理后在审核备注中写明目标编号和人工核查结论'],
    remarkTemplates: ['举报已人工核查，证据属实，建议继续处理。', '举报已人工核查，证据不足，保留记录。']
  }
}
