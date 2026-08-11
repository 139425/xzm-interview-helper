package com.xzm.xzm_interview_helper.recruitment;

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class OfficialCampusRecruitmentSource implements RecruitmentSource {
    private record OfficialSite(String company, String url, String companyType, String industry, String locations) {
    }

    private static final List<OfficialSite> SITES = List.of(
            site("腾讯", "https://join.qq.com/post.html?query=p_1", "民企", "IT/互联网", "深圳、北京、上海、广州、成都"),
            site("字节跳动", "https://jobs.bytedance.com/campus", "民企", "IT/互联网", "北京、上海、深圳、杭州、全国"),
            site("阿里巴巴", "https://talent.alibaba.com/campus/position-list", "民企", "IT/互联网", "杭州、北京、上海、全国"),
            site("蚂蚁集团", "https://talent.antgroup.com/campus", "民企", "IT/互联网", "杭州、上海、北京、深圳、全国"),
            site("美团", "https://zhaopin.meituan.com/web/position?hiringType=1", "民企", "IT/互联网", "北京、上海、深圳、成都、全国"),
            site("京东", "https://campus.jd.com/", "民企", "IT/互联网", "北京、上海、深圳、全国"),
            site("拼多多集团", "https://careers.pddglobalhr.com/campus/", "民企", "IT/互联网", "上海、深圳、全国"),
            site("百度", "https://talent.baidu.com/jobs/list?type=1", "民企", "IT/互联网", "北京、上海、深圳、全国"),
            site("网易", "https://campus.163.com/", "民企", "IT/互联网", "杭州、广州、上海、北京"),
            site("快手", "https://zhaopin.kuaishou.cn/recruit/e/#/official/index/", "民企", "IT/互联网", "北京、杭州、深圳、全国"),
            site("哔哩哔哩", "https://jobs.bilibili.com/campus/positions", "民企", "IT/互联网", "上海、北京、全国"),
            site("滴滴", "https://talent.didiglobal.com/campus", "民企", "IT/互联网", "北京、杭州、上海、全国"),
            site("携程集团", "https://campus.ctrip.com/", "民企", "IT/互联网", "上海、全国"),
            site("小红书", "https://job.xiaohongshu.com/campus", "民企", "IT/互联网", "上海、北京、武汉"),
            site("米哈游", "https://jobs.mihoyo.com/", "民企", "游戏", "上海、北京、深圳、济南"),
            site("莉莉丝游戏", "https://lilithgames.jobs.feishu.cn/campus", "民企", "游戏", "上海、北京"),
            site("完美世界", "https://recruit.wanmei.com/campus", "民企", "游戏", "北京、上海、成都"),
            site("华为", "https://career.huawei.com/reccampportal/portal5/campus-recruitment.html", "民企", "硬件/半导体", "深圳、上海、北京、杭州、全国"),
            site("中兴通讯", "https://job.zte.com.cn/campus-recruitment", "民企", "硬件/半导体", "深圳、南京、西安、全国"),
            site("小米集团", "https://hr.xiaomi.com/campus", "民企", "硬件/半导体", "北京、武汉、南京、深圳、全国"),
            site("OPPO", "https://careers.oppo.com/campus", "民企", "硬件/半导体", "深圳、东莞、成都、全国"),
            site("vivo", "https://hr.vivo.com/wt/vivo/web/index/CompvivoPagerecruit_Campus", "民企", "硬件/半导体", "东莞、深圳、杭州、南京"),
            site("荣耀", "https://career.hihonor.com/SU60ee16d90dcad25d0f6b13a5/pb/school.html", "民企", "硬件/半导体", "深圳、北京、西安、南京、全国"),
            site("大疆", "https://we.dji.com/zh-CN/campus", "民企", "硬件/半导体", "深圳、上海、北京"),
            site("联想", "https://talent.lenovo.com.cn/campus", "民企", "硬件/半导体", "北京、深圳、上海、武汉、全国"),
            site("京东方", "https://campus.boe.com/", "民企", "硬件/半导体", "北京、成都、重庆、合肥、全国"),
            site("中芯国际", "https://career.smics.com/campus", "民企", "硬件/半导体", "上海、北京、深圳、天津"),
            site("兆易创新", "https://gigadevice.zhiye.com/campus", "民企", "硬件/半导体", "北京、上海、深圳、合肥、苏州"),
            site("寒武纪", "https://career.cambricon.com/campus", "民企", "硬件/半导体", "北京、上海、南京、合肥"),
            site("比亚迪", "https://job.byd.com/", "民企", "汽车/自动驾驶", "深圳、西安、长沙、全国"),
            site("宁德时代", "https://catl.zhiye.com/campus", "民企", "汽车/自动驾驶", "宁德、上海、苏州、全国"),
            site("理想汽车", "https://www.lixiang.com/employ/campus.html", "民企", "汽车/自动驾驶", "北京、上海、常州"),
            site("蔚来", "https://nio.jobs.feishu.cn/campus", "民企", "汽车/自动驾驶", "上海、合肥、北京、全国"),
            site("小鹏汽车", "https://campus.xiaopeng.com/", "民企", "汽车/自动驾驶", "广州、北京、上海、武汉"),
            site("吉利汽车", "https://campus.geely.com/", "民企", "汽车/自动驾驶", "杭州、宁波、全国"),
            site("广汽集团", "https://campus.gac.com.cn/", "国企", "汽车/自动驾驶", "广州、全国"),
            site("上汽集团", "https://saicmotor.zhiye.com/campus", "国企", "汽车/自动驾驶", "上海、全国"),
            site("博世中国", "https://app.mokahr.com/campus-recruitment/bosch", "外企", "汽车/自动驾驶", "上海、苏州、无锡、全国"),
            site("中国移动", "https://job.10086.cn/", "央企", "国企央企", "全国"),
            site("中国电信", "https://job.chinatelecom.com.cn/", "央企", "国企央企", "全国"),
            site("中国联通", "https://zglt2025.zhaopin.com/", "央企", "国企央企", "全国"),
            site("国家电网", "https://zhaopin.sgcc.com.cn/", "央企", "国企央企", "全国"),
            site("中国石油", "https://zhaopin.cnpc.com.cn/", "央企", "国企央企", "全国"),
            site("中国石化", "https://job.sinopec.com/", "央企", "国企央企", "全国"),
            site("中国海油", "https://cnooc.zhaopin.com/", "央企", "国企央企", "全国"),
            site("中国建筑", "https://cscec.zhiye.com/campus", "央企", "国企央企", "全国"),
            site("中国中车", "https://www.crrcgc.cc/rczp", "央企", "机械/制造业", "全国"),
            site("中国商飞", "https://comac.zhiye.com/campus", "央企", "机械/制造业", "上海、北京、全国"),
            site("三一集团", "https://sany.zhiye.com/campus", "民企", "机械/制造业", "长沙、北京、上海、全国"),
            site("美的集团", "https://careers.midea.com/schoolOut/home", "民企", "机械/制造业", "佛山、上海、无锡、全国"),
            site("海尔智家", "https://maker.haier.net/client/campus", "民企", "机械/制造业", "青岛、上海、全国"),
            site("中国工商银行", "https://job.icbc.com.cn/", "央企", "金融行业", "全国"),
            site("中国建设银行", "https://job.ccb.com/", "央企", "金融行业", "全国"),
            site("中国农业银行", "https://career.abchina.com/", "央企", "金融行业", "全国"),
            site("中国银行", "https://campus.chinahr.com/pages/2026-boc", "央企", "金融行业", "全国"),
            site("招商银行", "https://career.cmbchina.com/", "股份制", "金融行业", "全国"),
            site("中信证券", "https://job.citics.com/", "央企", "金融行业", "北京、上海、深圳、全国"),
            site("宝洁中国", "https://www.pgcareers.com/cn/zh/campus", "外企", "消费生活", "广州、北京、上海、全国"),
            site("联合利华", "https://careers.unilever.com/china-early-careers", "外企", "消费生活", "上海、合肥、天津、全国"),
            site("欧莱雅中国", "https://careers.loreal.com/zh_CN/jobs", "外企", "消费生活", "上海、广州、苏州、全国"),
            site("雀巢中国", "https://job.nestle.com.cn/", "外企", "消费生活", "北京、上海、广州、全国"),
            site("耐克中国", "https://jobs.nike.com/zh-CHN/students", "外企", "消费生活", "上海、全国"),
            site("普华永道", "https://jobs.pwccn.com/zh_CN/careers/SearchJobs/", "外企", "咨询/专业服务", "北京、上海、广州、深圳、全国"),
            site("德勤", "https://www2.deloitte.com/cn/zh/careers/students.html", "外企", "咨询/专业服务", "北京、上海、广州、深圳、全国"),
            site("安永", "https://www.ey.com/zh_cn/careers/students", "外企", "咨询/专业服务", "北京、上海、广州、深圳、全国"),
            site("毕马威", "https://kpmg.com/cn/zh/home/careers/graduates.html", "外企", "咨询/专业服务", "北京、上海、广州、深圳、全国"),
            site("埃森哲", "https://www.accenture.cn/cn-zh/careers/local/students", "外企", "咨询/专业服务", "北京、上海、深圳、大连、全国"),
            site("迈瑞医疗", "https://career.mindray.com/campus", "民企", "医疗健康", "深圳、武汉、南京、全国"),
            site("阿斯利康", "https://careers.astrazeneca.com.cn/early-talent", "外企", "医疗健康", "上海、北京、无锡、全国"),
            site("罗氏中国", "https://careers.roche.com/global/en/china", "外企", "医疗健康", "上海、苏州、全国")
    );

    private final RecruitmentHttpClient httpClient;
    private final int graduateYear;

    public OfficialCampusRecruitmentSource(
            RecruitmentHttpClient httpClient,
            @Value("${recruitment.crawler.graduate-year:0}") int configuredGraduateYear
    ) {
        this.httpClient = httpClient;
        this.graduateYear = configuredGraduateYear > 0 ? configuredGraduateYear : Year.now().getValue() + 1;
    }

    @Override
    public String sourceName() {
        return "企业招聘官网";
    }

    @Override
    public List<RecruitmentCandidate> fetch() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<CompletableFuture<RecruitmentCandidate>> futures = SITES.stream()
                    .map(site -> CompletableFuture.supplyAsync(() -> fetchSite(site), executor))
                    .toList();
            List<RecruitmentCandidate> result = new ArrayList<>();
            futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).forEach(result::add);
            return result;
        } finally {
            executor.shutdownNow();
        }
    }

    private RecruitmentCandidate fetchSite(OfficialSite site) {
        try {
            String text = RecruitmentText.clean(Jsoup.parse(httpClient.get(site.url())).text(), 25_000);
            boolean hasGraduateYear = text.contains(String.valueOf(graduateYear)) || text.contains((graduateYear % 100) + "届");
            boolean hasCampusSignal = List.of("校园招聘", "校招", "应届生", "毕业生", "实习生").stream().anyMatch(text::contains);
            if (!hasGraduateYear || !hasCampusSignal) return null;
            String type = text.contains("提前批") ? "秋招提前批" : text.contains("实习") ? "实习" : "校园招聘";
            return RecruitmentCandidate.builder()
                    .externalId(site.url())
                    .company(site.company())
                    .title(site.company() + " " + graduateYear + "届" + type)
                    .companyType(site.companyType())
                    .industry(site.industry())
                    .locations(site.locations())
                    .positions("开放岗位与具体城市请查看企业招聘官网")
                    .recruitmentType(type)
                    .targetGraduates(graduateYear + "届")
                    .deadline("以官网为准")
                    .applyUrl(site.url())
                    .announcementUrl(site.url())
                    .sourceName(site.company() + "招聘官网")
                    .sourceUrl(site.url())
                    .sourceKind("OFFICIAL")
                    .sourcePriority(100)
                    .build();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static OfficialSite site(String company, String url, String companyType, String industry, String locations) {
        return new OfficialSite(company, url, companyType, industry, locations);
    }
}
