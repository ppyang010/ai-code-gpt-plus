package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.modules.chat.service.WebSearchIntentResolution;
import com.example.aichat.modules.chat.service.WebSearchIntentResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DefaultWebSearchIntentResolver implements WebSearchIntentResolver {

    private static final String MODE_DISABLED = "disabled";
    private static final String MODE_ENABLED = "enabled";
    private static final String MODE_AUTO = "auto";
    private static final String ACTION_ENABLE = "enable";
    private static final String ACTION_DISABLE = "disable";
    private static final List<WebSearchIntentRule> AUTO_RULES = buildAutoRules();

    @Override
    public WebSearchIntentResolution resolve(String webSearchMode, Boolean enableWebSearch, String content) {
        String normalizedMode = normalizeMode(webSearchMode, enableWebSearch);
        if (MODE_ENABLED.equals(normalizedMode)) {
            return new WebSearchIntentResolution(
                    normalizedMode,
                    true,
                    "manual_enabled",
                    "手动开启",
                    ACTION_ENABLE
            );
        }
        if (MODE_DISABLED.equals(normalizedMode)) {
            return new WebSearchIntentResolution(
                    normalizedMode,
                    false,
                    "manual_disabled",
                    "手动关闭",
                    ACTION_DISABLE
            );
        }

        WebSearchIntentRule matchedRule = matchAutoRule(content);
        if (matchedRule == null) {
            return new WebSearchIntentResolution(normalizedMode, false, null, null, null);
        }
        return new WebSearchIntentResolution(
                normalizedMode,
                ACTION_ENABLE.equals(matchedRule.getAction()),
                matchedRule.getId(),
                matchedRule.getLabel(),
                matchedRule.getAction()
        );
    }

    private String normalizeMode(String webSearchMode, Boolean enableWebSearch) {
        if (StringUtils.hasText(webSearchMode)) {
            String normalizedMode = webSearchMode.trim().toLowerCase(Locale.ROOT);
            if (MODE_DISABLED.equals(normalizedMode) || MODE_ENABLED.equals(normalizedMode) || MODE_AUTO.equals(normalizedMode)) {
                return normalizedMode;
            }
            throw new BizException(ErrorCode.INVALID_PARAM, "webSearchMode 仅支持 disabled / enabled / auto");
        }
        if (Boolean.TRUE.equals(enableWebSearch)) {
            return MODE_ENABLED;
        }
        return MODE_DISABLED;
    }

    private WebSearchIntentRule matchAutoRule(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String normalizedContent = content.trim();
        for (WebSearchIntentRule rule : AUTO_RULES) {
            if (rule.matches(normalizedContent)) {
                return rule;
            }
        }
        return null;
    }

    private static List<WebSearchIntentRule> buildAutoRules() {
        List<WebSearchIntentRule> rules = new ArrayList<>();
        rules.add(new WebSearchIntentRule(
                "explicit_web_lookup",
                "显式查询/联网请求",
                ACTION_ENABLE,
                100,
                toPatterns(
                        "(联网|搜索|搜一下|查一下|帮我查|查查|检索)",
                        "(official site|official docs|look up|search for)"
                )
        ));
        rules.add(new WebSearchIntentRule(
                "fresh_information",
                "时效性信息",
                ACTION_ENABLE,
                90,
                toPatterns(
                        "(最新|最近|近期|刚刚|当前|现在|今天|昨日|本周|本月|今年|实时|即时|目前)",
                        "\\b(latest|recent|current|today|now|live|breaking)\\b"
                )
        ));
        rules.add(new WebSearchIntentRule(
                "market_weather_schedule",
                "行情/天气/赛程",
                ACTION_ENABLE,
                80,
                toPatterns(
                        "(价格|股价|汇率|天气|温度|航班|机票|赛程|比分|票房|财报)",
                        "\\b(price|stock|exchange rate|weather|temperature|flight|schedule|score|earnings)\\b"
                )
        ));
        rules.add(new WebSearchIntentRule(
                "official_reference",
                "官网/官方文档",
                ACTION_ENABLE,
                70,
                toPatterns(
                        "(官网|官方|官方文档|接口文档|发布说明|更新日志|兼容性)",
                        "\\b(docs|documentation|release notes|changelog|compatibility)\\b"
                )
        ));
        rules.add(new WebSearchIntentRule(
                "writing_and_polish",
                "写作/润色",
                ACTION_DISABLE,
                40,
                toPatterns(
                        "(帮我写|写一段|润色|改写|续写|起草|文案|邮件|周报|演讲稿)",
                        "\\b(write|rewrite|polish|draft)\\b"
                )
        ));
        rules.add(new WebSearchIntentRule(
                "summary_and_translation",
                "总结/翻译",
                ACTION_DISABLE,
                30,
                toPatterns(
                        "(总结|摘要|提炼|翻译|概括|整理成)",
                        "\\b(summarize|summary|translate)\\b"
                )
        ));
        rules.add(new WebSearchIntentRule(
                "reasoning_and_explanation",
                "解释/推理",
                ACTION_DISABLE,
                20,
                toPatterns(
                        "(为什么|原理|解释|分析|推导|证明|计算|怎么理解|对比)",
                        "\\b(why|explain|analysis|reasoning|prove|calculate|compare)\\b"
                )
        ));
        Collections.sort(rules, (left, right) -> Integer.compare(right.getPriority(), left.getPriority()));
        return Collections.unmodifiableList(rules);
    }

    private static List<Pattern> toPatterns(String... expressions) {
        List<Pattern> patterns = new ArrayList<>();
        for (String expression : expressions) {
            patterns.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    private static class WebSearchIntentRule {

        private final String id;
        private final String label;
        private final String action;
        private final int priority;
        private final List<Pattern> patterns;

        private WebSearchIntentRule(String id, String label, String action, int priority, List<Pattern> patterns) {
            this.id = id;
            this.label = label;
            this.action = action;
            this.priority = priority;
            this.patterns = patterns;
        }

        private String getId() {
            return id;
        }

        private String getLabel() {
            return label;
        }

        private String getAction() {
            return action;
        }

        private int getPriority() {
            return priority;
        }

        private boolean matches(String content) {
            for (Pattern pattern : patterns) {
                if (pattern.matcher(content).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
