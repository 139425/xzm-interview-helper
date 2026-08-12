package com.xzm.xzm_interview_helper.recruitment;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RecruitmentClassifier {
    private static final Map<String, String[]> INDUSTRY_RULES = new LinkedHashMap<>();

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
}
