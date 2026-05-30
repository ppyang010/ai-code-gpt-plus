package com.example.aichat.infrastructure.search;

import com.example.aichat.modules.chat.service.WebSearchResultItem;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索供应商返回后的标准化响应，供聊天领域服务继续裁剪和拼 prompt。
 */
public class WebSearchClientResponse {

    /** 供应商返回的整体答案摘要，可能为空。 */
    private String answer;
    /** 标准化后的搜索结果列表，按供应商相关性顺序保留。 */
    private List<WebSearchResultItem> results = new ArrayList<>();

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<WebSearchResultItem> getResults() {
        return results;
    }

    public void setResults(List<WebSearchResultItem> results) {
        this.results = results;
    }
}
