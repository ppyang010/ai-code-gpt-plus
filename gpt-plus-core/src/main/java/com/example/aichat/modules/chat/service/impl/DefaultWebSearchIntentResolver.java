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

/**
 * 第一版联网搜索意图解析器，使用可扩展的规则表完成自动判断。
 */
@Component
public class DefaultWebSearchIntentResolver implements WebSearchIntentResolver {

    /** 手动关闭联网搜索。 */
    private static final String MODE_DISABLED = "disabled";
    /** 手动开启联网搜索。 */
    private static final String MODE_ENABLED = "enabled";
    /** 由后端根据用户内容自动判断是否联网。 */
    private static final String MODE_AUTO = "auto";
    /** 规则动作：需要联网。 */
    private static final String ACTION_ENABLE = "enable";
    /** 规则动作：不需要联网。 */
    private static final String ACTION_DISABLE = "disable";
    /** 自动判断规则表，按优先级从高到低匹配。 */
    private static final List<WebSearchIntentRule> AUTO_RULES = buildAutoRules();

    /**
     * 解析前端传入模式和兼容布尔字段，返回最终联网搜索决策。
     */
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

        // auto 模式只匹配第一条最高优先级规则，便于后续通过调整规则表扩展行为。
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

    /**
     * 归一化联网模式；旧版布尔字段只作为没有 webSearchMode 时的兼容兜底。
     */
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

    /**
     * 按优先级扫描 auto 规则，命中后立即返回。
     */
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

    /**
     * 构造第一版自动判断规则表，后续扩展只需要新增规则并设置优先级。
     */
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

    /**
     * 把规则表达式编译成大小写不敏感的正则，提高运行时匹配效率。
     */
    private static List<Pattern> toPatterns(String... expressions) {
        List<Pattern> patterns = new ArrayList<>();
        for (String expression : expressions) {
            patterns.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    /**
     * 自动判断规则项，包含规则身份、动作、优先级和匹配表达式。
     */
    private static class WebSearchIntentRule {

        /** 规则唯一标识，写入日志和 metadata。 */
        private final String id;
        /** 规则展示名，用于排障和后续前端提示。 */
        private final String label;
        /** 规则动作：enable 或 disable。 */
        private final String action;
        /** 规则优先级，数值越大越先匹配。 */
        private final int priority;
        /** 规则命中的正则表达式列表。 */
        private final List<Pattern> patterns;

        /**
         * 创建一条自动判断规则。
         */
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

        /**
         * 判断用户内容是否命中当前规则任意表达式。
         */
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
