# zrlog-plugin-statistics

ZrLog 访问统计。记录文章访问数据，提供访问趋势、来源、设备和明细统计。

## 功能

- 记录访问路径、来源、IP、访客标识、设备和浏览器信息
- 展示最近访问明细和趋势
- 按天汇总 PV、UV、Session、IP、热门文章、来源和设备分布
- 可配置日报通知渠道
- 提供站点统计脚本片段

## 构建

```shell
export JAVA_HOME=${HOME}/dev/graalvm-jdk-latest
export PATH=${JAVA_HOME}/bin:$PATH
```

## 原生制品发布

Linux amd64/arm64 制品在上传前会调用 `zrlog-artifact-service`，通过与 `plugin-core`
相同的固定版本 `process-artifact` Action 完成压缩和 SHA-256、文件大小校验。
处理成功后才会生成最终制品的 MD5 并上传；处理失败会停止该平台的发布。
服务接收的版本号使用 `bin/build-info.sh` 生成的实际插件版本。

发布前需要配置 Actions Secret `ARTIFACT_SERVICE_TOKEN`，可在仓库中单独设置，
或授权该仓库使用同名组织 Secret。服务地址为 `https://webdav.zrlog.com/artifact`。
