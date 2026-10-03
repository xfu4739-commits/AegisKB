FROM elasticsearch:8.5.2

# application.yaml 使用 ik_max_word / ik_smart，必须预装 IK。
# 官方下载站证书已过期，改为构建时安装仓库内插件包。
COPY plugins/elasticsearch-analysis-ik-8.5.2.zip /tmp/elasticsearch-analysis-ik-8.5.2.zip
RUN elasticsearch-plugin install --batch file:///tmp/elasticsearch-analysis-ik-8.5.2.zip
