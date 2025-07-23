# Deploy Glot locally using docker-run

> Visit the [glotcode/**docker-run**](https://github.com/glotcode/docker-run) repository

## Table of Contents
- [Install and Start docker-run](#install-and-start-docker-run)
- [Fill in the DockerConfig](#fill-in-the-dockerconfig)

---

## Install and Start docker-run
> This section refers to the [official documentation](https://github.com/glotcode/docker-run/tree/main/docs/install)

### 1. Install Docker (Ubuntu, if installed, skip this step)
```bash
apt install docker.io
```
Disable docker networking (optional)
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

### 2. Pull the docker-run image
```bash
docker pull glot/docker-run:latest
```

### 3. Pull images for the languages you want
Check available language images on [DockerHub](https://hub.docker.com/u/glot) or [glot-images](https://github.com/glotcode/glot-languages)
```bash
docker pull glot/python:latest
docker pull glot/clang:latest
docker pull glot/rust:latest
# ...
```

### 4. Start the docker-run container
Start the docker-run container using the following command:
```bash
docker run -d --restart=always \
  -p 8088:8088 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e API_ACCESS_TOKEN=my-token \
  glot/docker-run:latest
```
#### Optional: Configure Environment Variables at Startup
A complete description of all environment variables is available in the [Environment variables](https://github.com/glotcode/docker-run?tab=readme-ov-file#environment-variables) section.
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

### 5. Verify the Deployment
Use the following command to test the deployment result:
```bash
# Print docker-run version
curl http://localhost:8088

# Print docker version, etc
curl --header 'X-Access-Token: my-token' http://localhost:8088/version

# Run python code
curl --request POST \
     --header 'X-Access-Token: my-token' \
     --header 'Content-type: application/json' \
     --data '{"image": "glot/python:latest", "payload": {"language": "python", "files": [{"name": "main.py", "content": "print(42)"}]}}' \
     --url 'http://localhost:8088/run'
```

---

## Fill in the DockerConfig
Once the deployment above is completed and all tests pass, proceed to fill in the `DockerConfig` configuration:
```yml
# Languages already deployed via Docker
supportedLanguages: []
# Docker request URL
requestUrl: 'http://localhost:8088/run'
# Docker request token
token: ''
```
After filling in the config, use the `/pb reload` command to reload the config file, then you can start using it normally.

### If you encounter any issues during deployment, debugging, or operation, please feel free to submit them via the methods described in [Feedback](../../README_en.md#feedback).
