package com.example.aichat.modules.chat.service;

/**
 * 联网搜索意图解析结果，后续搜索执行、日志和 metadata 都以该对象为依据。
 */
public class WebSearchIntentResolution {

    /** 归一化后的搜索模式：disabled / enabled / auto。 */
    private final String mode;
    /** 本次消息最终是否需要联网搜索。 */
    private final boolean enabled;
    /** 命中的规则 ID，手动模式也会填充固定规则 ID。 */
    private final String matchedRuleId;
    /** 命中的规则展示名。 */
    private final String matchedRuleLabel;
    /** 命中规则动作：enable / disable。 */
    private final String matchedRuleAction;

    /**
     * 构造一次意图解析结果，字段保持不可变，避免发送链路中被误改。
     */
    public WebSearchIntentResolution(
            String mode,
            boolean enabled,
            String matchedRuleId,
            String matchedRuleLabel,
            String matchedRuleAction
    ) {
        this.mode = mode;
        this.enabled = enabled;
        this.matchedRuleId = matchedRuleId;
        this.matchedRuleLabel = matchedRuleLabel;
        this.matchedRuleAction = matchedRuleAction;
    }

    public String getMode() {
        return mode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMatchedRuleId() {
        return matchedRuleId;
    }

    public String getMatchedRuleLabel() {
        return matchedRuleLabel;
    }

    public String getMatchedRuleAction() {
        return matchedRuleAction;
    }
}
