# 使用 docker-run 在本地部署 Glot

> 访问 [glotcode/**docker-run**](https://github.com/glotcode/docker-run) 仓库

## 主要内容
- [安装并启动 docker-run](#安装并启动-docker-run)
- [填写 DockerConfig 配置](#填写-dockerconfig-配置)

---

## 安装并启动 docker-run
> 此部分参考 [官方文档](https://github.com/glotcode/docker-run/tree/main/docs/install)

### 1. 安装 docker（Ubuntu，如果已安装可跳过）
```bash
apt install docker.io
```
可选：禁用 docker 网络
```bash
echo '{
    "ip-forward": false,
    "iptables": false,
    "ipv6": false,
    "ip-masq": false
}' > /etc/docker/daemon.json

sudo systemctl daemon-reexec
sudo systemctl restart docker
```

### 2. 拉取 docker-run 镜像
```bash
docker pull glot/docker-run:latest
```

### 3. 拉取需要语言对应的镜像
在 [DockerHub](https://hub.docker.com/u/glot) 或 [glot-images](https://github.com/glotcode/glot-languages) 查看可用语言的镜像
```bash
docker pull glot/python:latest
docker pull glot/clang:latest
docker pull glot/rust:latest
# ...
```

### 4. 启动 docker-run 容器
使用下方命令启动 docker-run 容器：
```bash
docker run -d --restart=always \
  -p 8088:8088 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e API_ACCESS_TOKEN=my-token \
  glot/docker-run:latest
```
#### 可选：配置启动时的环境变量
全部环境变量介绍可在 [Environment variables](https://github.com/glotcode/docker-run?tab=readme-ov-file#environment-variables) 中查看
```bash
docker run -d --restart=always \
  -p 8088:8088 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e SERVER_WORKER_THREADS=4 \
  -e API_ACCESS_TOKEN=my-token \
  -e DOCKER_CONTAINER_NETWORK_DISABLED=false \
  -e DOCKER_CONTAINER_MEMORY=67108864 \
  -e RUN_MAX_EXECUTION_TIME=15 \
  -e RUN_MAX_OUTPUT_SIZE=10485760 \
  glot/docker-run:latest
```

### 5. 检查部署结果
使用下方命令测试部署结果：
```bash
# 打印 docker-run 版本
curl http://localhost:8088

# 打印 docker 版本和信息
curl --header 'X-Access-Token: my-token' http://localhost:8088/version

# 运行测试 python 代码（需先拉取 glot/python 镜像）
curl --request POST \
     --header 'X-Access-Token: my-token' \
     --header 'Content-type: application/json' \
     --data '{"image": "glot/python:latest", "payload": {"language": "python", "files": [{"name": "main.py", "content": "print(42)"}]}}' \
     --url 'http://localhost:8088/run'
```

---

## 填写 DockerConfig 配置
当上方部署已完成且测试均无问题时，再填写 `DockerConfig` 配置
```yml
# docker已经部署的语言
supportedLanguages: []
# docker请求地址
requestUrl: 'http://localhost:8088/run'
# docker请求token
token: ''
```
填写配置后使用 `/pb reload` 命令重载配置文件，然后即可开始正常使用

### 如果在部署、调试、运行时遇到任何问题，欢迎通过 [反馈](../README.md#反馈) 中的方法提交
