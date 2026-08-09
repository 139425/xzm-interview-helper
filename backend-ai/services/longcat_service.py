"""兼容层：保留旧模块路径，内部已切换到智谱服务实现。"""

from services.zhipu_service import ZhipuService, get_zhipu_service

# 为避免现有导入路径失效，临时保留旧名称别名。
LongCatService = ZhipuService
get_longcat_service = get_zhipu_service
