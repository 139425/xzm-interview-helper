package com.xzm.xzm_interview_helper.recruitment;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RecruitmentClassifier {
    private static final Map<String, String[]> INDUSTRY_RULES = new LinkedHashMap<>();
    private static final Map<String, String[]> JOB_TRACK_RULES = new LinkedHashMap<>();

    static {
        INDUSTRY_RULES.put("游戏", new String[]{"游戏", "电竞", "game"});
        INDUSTRY_RULES.put("硬件/半导体", new String[]{"芯片", "半导体", "集成电路", "硬件", "电子", "通信", "光电", "fpga"});
        INDUSTRY_RULES.put("汽车/自动驾驶", new String[]{"汽车", "自动驾驶", "新能源车", "智能驾驶", "车联网"});
        INDUSTRY_RULES.put("机械/制造业", new String[]{"机械", "制造", "工业", "装备", "机器人", "航空", "航天", "家电"});
        INDUSTRY_RULES.put("金融行业", new String[]{"银行", "证券", "保险", "基金", "金融", "投资", "期货", "信托"});
        INDUSTRY_RULES.put("消费生活", new String[]{"零售", "电商", "快消", "食品", "饮料", "美妆", "服饰", "餐饮", "旅游", "潮玩"});
        INDUSTRY_RULES.put("医疗健康", new String[]{"医疗", "医药", "生物", "制药", "健康", "医院", "生命科学"});
        INDUSTRY_RULES.put("政府/事业单位", new String[]{"政府", "事业单位", "研究院", "研究所", "公务员", "高校"});
        INDUSTRY_RULES.put("国企央企", new String[]{"央企", "国企", "国家电网", "中石油", "中石化", "中国移动", "中国电信", "中国联通"});
        INDUSTRY_RULES.put("广告传媒", new String[]{"广告", "传媒", "出版", "影视", "内容", "媒体", "创意"});
        INDUSTRY_RULES.put("建筑/房地产", new String[]{"建筑", "地产", "房地产", "工程", "设计院"});
        INDUSTRY_RULES.put("材料/能源/化工", new String[]{"材料", "能源", "化工", "电力", "石油", "矿业", "新能源", "光伏", "储能"});
        INDUSTRY_RULES.put("物流/交通运输", new String[]{"物流", "运输", "航运", "航空公司", "铁路", "快递", "供应链"});
        INDUSTRY_RULES.put("咨询/专业服务", new String[]{"咨询", "会计", "审计", "律所", "人力资源", "法律服务"});
        INDUSTRY_RULES.put("IT/互联网", new String[]{"互联网", "软件", "算法", "开发", "产品", "ai", "人工智能", "云计算", "大数据", "网络安全"});

        JOB_TRACK_RULES.put("AI应用/Agent", new String[]{
                "agent", "智能体", "rag", "大模型应用", "llm应用", "ai应用", "prompt", "function calling",
                "tool use", "知识库", "langchain", "langgraph", "模型应用", "大模型开发", "大模型工程",
                "ai开发", "ai工程", "ai全栈", "aigc", "mcp", "模型接入", "模型服务", "模型平台",
                "工作流编排", "模型编排", "提示词工程", "copilot"
        });
        JOB_TRACK_RULES.put("算法/模型", new String[]{
                "算法", "机器学习", "深度学习", "自然语言处理", "计算机视觉", "多模态", "推荐", "搜索",
                "预训练", "微调", "强化学习", "模型训练", "推理优化"
        });
        JOB_TRACK_RULES.put("数据", new String[]{
                "数据开发", "数据分析", "数据工程", "数据科学", "数据治理", "商业分析", "bi工程"
        });
        JOB_TRACK_RULES.put("硬件/芯片", new String[]{
                "硬件", "芯片", "嵌入式", "fpga", "ic设计", "数字前端", "验证工程师", "射频"
        });
        JOB_TRACK_RULES.put("产品/运营", new String[]{
                "产品经理", "产品运营", "用户运营", "内容运营", "增长运营", "市场运营"
        });
        JOB_TRACK_RULES.put("软件研发", new String[]{
                "开发工程师", "研发工程师", "后端", "前端", "客户端", "java", "python", "golang", "c++",
                "backend", "frontend", "full stack", "fullstack", "developer",
                "测试开发", "云原生", "基础架构", "软件工程"
        });
        JOB_TRACK_RULES.put("职能", new String[]{
                "人力资源", "财务", "法务", "行政", "采购", "销售", "管培生"
        });
    }

    private RecruitmentClassifier() {
    }

    public static String industry(String... values) {
        StringBuilder combined = new StringBuilder();
        for (String value : values) {
            if (value != null) combined.append(value).append(' ');
        }
        String normalized = combined.toString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : INDUSTRY_RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalized.contains(keyword)) return entry.getKey();
            }
        }
        return "其他行业";
    }

    public static String jobTrack(String... values) {
        StringBuilder combined = new StringBuilder();
        for (String value : values) {
            if (value != null) combined.append(value).append(' ');
        }
        String normalized = combined.toString().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String[]> entry : JOB_TRACK_RULES.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (matchesKeyword(normalized, keyword)) return entry.getKey();
            }
        }
        return "综合岗位";
    }

    private static boolean matchesKeyword(String text, String keyword) {
        if (!keyword.chars().allMatch(RecruitmentClassifier::isAsciiWordOrSpace)) {
            return text.contains(keyword);
        }
        int fromIndex = 0;
        while (fromIndex < text.length()) {
            int index = text.indexOf(keyword, fromIndex);
            if (index < 0) return false;
            int end = index + keyword.length();
            boolean leftBoundary = index == 0 || !isAsciiWord(text.charAt(index - 1));
            boolean rightBoundary = end == text.length() || !isAsciiWord(text.charAt(end));
            if (leftBoundary && rightBoundary) return true;
            fromIndex = index + 1;
        }
        return false;
    }

    private static boolean isAsciiWordOrSpace(int character) {
        return character == ' ' || isAsciiWord((char) character);
    }

    private static boolean isAsciiWord(char character) {
        return character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '_';
    }
}
