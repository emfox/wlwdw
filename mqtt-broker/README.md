# mqtt-broker —— 自托管 EMQX MQTT 服务（wlwdw 推送通道）

本目录是 **wlwdw 仓库的一部分**，随 `git pull` 分发到服务器，在服务器
`wlwdw/mqtt-broker/` 下直接部署。镜像（`emqx/emqx:5.8.1`）由 docker 从
Docker Hub 自动拉取，无需手动传输；本目录只提供编排与初始化配置。

- 设备端（Android）经 **`wss://mqtt.rpwt.org/mqtt`**（443，复用外部 nginx-proxy + Let's Encrypt）连接；
- wlwdw 后端容器经**共享的 `wlwdw_default` docker 网络**以服务名 `emqx:1883`
  发布消息（本机 `127.0.0.1:1883` 映射仅用于宿主机上的调试）；
- **认证 + 授权都在 wlwdw 的 `/mqtt/auth`** 完成：每次 MQTT CONNECT，EMQX 回调
  `https://wlwdw.rpwt.org/mqtt/auth`，该接口按 EMQX 5 契约返回
  `{"result":"allow","acl":[...]}` / `{"result":"deny"}`：
  - 设备（username = devid，密码 = 共享 secret）→ 允许订阅 `all` + 自身 devid，仅可向自身 devid 发布；
  - 服务账号 `mqtt-server`（独立密码）→ 全部允许；
  - 客户端 ACL 之外的任何操作，由 EMQX 侧 `authorization.no_match=deny` + 空授权链兜底拒绝。

> 设计背景：EMQX 环境变量无法表达 HTTP 认证的请求体模板，file 授权源的
> 环境变量配置有校验问题，且 EMQX 出厂内置的默认 file 授权源末行 `{allow, all}`
> 会放行一切——因此认证/授权统一经 REST API 配置（`bin/provision.sh`），
> 持久化在 `./data`，无需文件 ACL。

## 一、部署流程（按序执行）

### 0. 前提与顺序

1. **DNS**：在 `rpwt.org` 加一条 `mqtt → 服务器公网IP`（A 记录）。
2. **先部署 wlwdw 到最新 master**（含提供本文所依赖 JSON 契约的
   `MqttAuthController`），并验证其线上可用（必须返回 JSON `result`，而不是空
   body）：
   ```bash
   curl -s -X POST https://wlwdw.rpwt.org/mqtt/auth -H 'content-type: application/json' \
     -d '{"username":"probe","password":"x"}'
   # 期望输出 {"result":"deny"}
   ```
   > 顺序很重要：broker 的认证器一启动就会回调这个端点，因此必须先确保该
   > 端点按本文契约返回 JSON `result`，否则所有设备将无法连接。
3. 确认外部 nginx-proxy 网络存在（wlwdw 已在用它，一般无需创建）：
   ```bash
   docker network ls | grep nginx-proxy   # 没有则: docker network create nginx-proxy_default
   ```
4. **wlwdw 项目需先运行过** `docker compose up -d`：broker 容器要加入 wlwdw
   的 `wlwdw_default` 网络（作为 external 网络），该网络由 wlwdw 的 compose
   创建。若 wlwdw 从未启动，先执行：
   ```bash
   cd .. && docker compose up -d && cd mqtt-broker
   ```

### 1. 拉取工程

```bash
cd <服务器上的 wlwdw 目录>
git pull            # 获得 mqtt-broker/
cd mqtt-broker
```

### 2. 生成配置（本地敏感文件，均已 gitignore）

```bash
cp .env.example .env
# 编辑 .env：EMQX_ADMIN_PASSWORD=$(openssl rand -hex 16)
cp etc/api_keys.example etc/api_keys
# 编辑 etc/api_keys：把 change_this_to_a_random_secret 换成 $(openssl rand -hex 16)
```

> ⚠️ `etc/api_keys` 文件**不能包含任何注释行**（EMQX 的 bootstrap 会把第一行
> 当 key 条目解析，注释行会令整个文件加载失败、一个 key 都不导入）。该文件
> 应只有一行：`dev_key:你的随机secret`。改完后用 `cat etc/api_keys` 确认。

### 3. 启动 EMQX

```bash
docker compose up -d            # 自动从 Docker Hub 拉取 emqx:5.8.1
docker compose logs -f emqx     # 看到 "EMQX 5.x is running now" 即成功，Ctrl-C 退出
```
> EMQX 只在**全新 `data/`** 启动时导入 `etc/api_keys`，此后不再重试。若 API
> key 未生效（权限不对、或 `etc/api_keys` 在首次启动后才创建），需重置
> `data/` 后再启动：
```bash
docker compose down
rm -rf data && mkdir -p data && sudo chown -R 1000:1000 data
docker compose up -d
```
确认 key 已导入（管理 API 返回 200 而非 401）：
```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  -u "$(grep -v '^#' etc/api_keys | head -1)" \
  http://127.0.0.1:18083/api/v5/authorization/sources
```
日志里如仍出现 `failed_to_load_bootstrap_file`，就是 api_keys 含注释行或有
隐藏字符，用 `cat -A etc/api_keys` 检查。

### 4. 一次性配置认证/授权（幂等，可重跑）

```bash
./bin/provision.sh
# 若上一步没生成 etc/api_keys，会提示先 cp
```
provision 会：移除内置默认 file 授权源 → 加空 built_in_database 授权源 →
创建 HTTP 认证器指向 wlwdw `/mqtt/auth`。配置持久化于 `./data`，重启不丢。
可用 `docker compose exec emqx emqx ctl conf show authorization` 确认
`no_match` 为 `deny`。

### 5. 配置 wlwdw 后端并重启

先到 wlwdw 项目根目录生成两个**互相独立**的随机串并记好：
```bash
cd ..
echo "SERVER_PASS=$(openssl rand -hex 16)"
echo "DEVICE_SECRET=$(openssl rand -hex 16)"
```
在 `.env.local` 中追加（填上面两个值；DEVICE_SECRET 第 6 步要同步给 Android）。
`MQTT_BROKER_HOST` 会被 compose 覆盖为 `emqx`（容器经共享网络按服务名连
broker），此处填 `127.0.0.1` 仅作非 docker 运行的默认值：
```dotenv
MQTT_BROKER_HOST=127.0.0.1
MQTT_BROKER_PORT=1883
MQTT_SERVER_USER=mqtt-server
MQTT_SERVER_PASSWORD=<SERVER_PASS>      # EMQX 认证 mqtt-server 用
MQTT_DEVICE_SHARED_SECRET=<DEVICE_SECRET> # Android 设备连接用（两端必须一致）
```
重启并验证：
```bash
docker compose up -d          # 需先 git pull 拿到共享网络配置（php 经 emqx:1883）

# 后端新认证契约在线上生效（期望 {"result":"deny"}）
curl -s -X POST https://wlwdw.rpwt.org/mqtt/auth -H 'content-type: application/json' \
  -d '{"username":"smoke","password":"wrong"}'

# php 容器能连上 broker（期望输出 broker reachable；如报 Class not found
# 说明宿主导出 vendor 后还没跑 composer install 装 php-mqtt）
docker compose exec php php -r '
  require "vendor/autoload.php";
  $s = new PhpMqtt\Client\ConnectionSettings();
  $c = new PhpMqtt\Client\MqttClient("emqx", 1883, null, PhpMqtt\Client\MqttClient::MQTT_3_1_1);
  try { $c->connect($s, true); echo "broker reachable\n"; $c->disconnect(); }
  catch (Throwable $e) { echo "FAIL: ".$e->getMessage()."\n"; }
'
```

### 6. Android 同步共享 secret

把 `android/.../MqttConfig.MQTT_DEVICE_SHARED_SECRET` 改为第 5 步
`MQTT_DEVICE_SHARED_SECRET` 的**同一个随机值**，重新构建 APK。
（`MQTT_HOST=mqtt.rpwt.org`、`PORT=443`、`PATH=/mqtt` 无需改动。）

## 二、冒烟验证（服务器本机即可）

订阅端（终端 A，模拟 Android 设备走 wss 443）：
```bash
pip install paho-mqtt
python3 - <<'PY'
import paho.mqtt.client as mqtt
def on(c,u,m): print('RECV',m.topic,m.payload)
c=mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id='dev-test')
c.username_pw_set('demo-smoke','<MQTT_DEVICE_SHARED_SECRET>')
c.on_message=on
c.ws_set_options(path='/mqtt')
c.connect('mqtt.rpwt.org',443,transport='websockets')
c.subscribe('demo-smoke')
c.loop_forever()
PY
```
发布端（终端 B，模拟 wlwdw 后端）：
```bash
python3 - <<'PY'
import paho.mqtt.client as mqtt
c=mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id='srv-test')
c.username_pw_set('mqtt-server','<MQTT_SERVER_PASSWORD>')
c.connect('127.0.0.1',1883)
c.publish('demo-smoke','{"id":1}',qos=1)
c.disconnect()
PY
```
订阅端 5 秒内应收到 `{"id":1}`。**安全边界复查**：把订阅主题改为 `#`，
应收到拒绝（0x80 / 断连）——若反而收到消息说明 provision 未生效，勿上线。

## 三、轮换密钥

- Dashboard / API key：bootstrap 文件只在**首次启动**生效，之后请用
  Dashboard 或 `GET/POST /api/v5/api_key` 管理。
- 认证口令：同时改 wlwdw `.env.local` 的 `MQTT_DEVICE_SHARED_SECRET` /
  `MQTT_SERVER_PASSWORD` 与 Android `MqttConfig` 内建 secret，重启 wlwdw 即可；
  EMQX 侧无需改动（它只回调查证端点）。

## 四、排障

- 连接被拒：`docker compose logs emqx` 看 `authentication`/`timeout` 报错；
  手动验证回调：
  ```bash
  curl -s -X POST https://wlwdw.rpwt.org/mqtt/auth -H 'content-type: application/json' \
    -d '{"username":"dev-test","password":"<secret>"}'
  # 期望 {"result":"allow","acl":[...]}；错误口令返回 {"result":"deny"}
  ```
- 越权放行：确认已跑过 `./bin/provision.sh`。未跑时 EMQX 内置默认 file 授权源
  会 `{allow, all}` 放行一切已认证客户端。
- nginx-proxy 未转发 ws：确认 `VIRTUAL_PORT=8083` 与 `VIRTUAL_HOST` 生效
  （`docker exec <nginx-proxy容器> nginx -T | grep -A5 mqtt`），并确认反代支持
  WebSocket upgrade。
- `bin/provision.sh` 报 `cannot reach the EMQX management API`：确认容器映射了
  `127.0.0.1:18083`（`docker compose ps`）；若 EMQX 在 `etc/api_keys` 就绪前就已
  启动（API key 只在全新 `data/` 时导入），执行
  `docker compose down && rm -rf data && docker compose up -d` 后再 provision。
- php 容器连 broker 报 `Network is unreachable` / connection refused：确认
  wlwdw 的 `compose.yml` 已把 php 加入共享网络并以 `MQTT_BROKER_HOST=emqx`
  覆盖宿主端口；确认 emqx 容器加入了 wlwdw 的 `wlwdw_default` 网络。
  重建两侧：`docker compose up -d`（wlwdw 与 mqtt-broker 各一次）。
- EMQX 认证连接器告警 `resource down / econnrefused`：通常是后端刚重启的
  瞬态，等 EMQX 健康检查恢复即可；部署时遵循"先 wlwdw 后 broker"的顺序可避免。
