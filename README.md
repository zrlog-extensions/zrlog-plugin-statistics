#### zrlog 插件代码模版仓库

> 这是 statistics 插件，主要用于将页面托管到 cdn 后，程序无法获取到用户的浏览信息。

#### 开发环境打包

```shell
export JAVA_HOME=${HOME}/dev/graalvm-jdk-latest
export PATH=${JAVA_HOME}/bin:$PATH
```

#### TODO

- [x] 基础的数据统计
- [ ] 对常规的浏览器 UA 进行检查
- [ ] 通过挂件的方式，植入到后台首页的控制台（数据大盘）
- [ ] 建立对应的统计表，需 plugin-core 支持查询表是否存在