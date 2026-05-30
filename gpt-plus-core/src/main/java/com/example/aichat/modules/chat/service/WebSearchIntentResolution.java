package com.example.aichat.modules.chat.service;

public class WebSearchIntentResolution {

    private final String mode;
    private final boolean enabled;
    private final String matchedRuleId;
    private final String matchedRuleLabel;
    private final String matchedRuleAction;

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
