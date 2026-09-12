# redis-all-pack

Redis 올패키지 학습 프로젝트. **클러스터 환경**에서 분산락 · 핫키 · 성능 개선 · 캐시 스탬피드를 직접 재현하고 측정한다.

읽어서 아는 것과 재현해서 아는 것은 다르다. 매 주차는 **깨지는 걸 먼저 보고** 그 다음에 고친다.

## 무엇을 다루나

### 분산락 — 구현 방식별 비교와 성능 측정

`SET NX` 하나로 시작해서, 같은 락을 **Lettuce 스핀락 · Redisson `RLock` · RedLock** 세 가지로 만들어 놓고 나란히 잰다. 셋은 대기하는 방식이 다르다 — 스핀은 CPU 를 쓰고, `RLock` 은 pub/sub 으로 눕고, RedLock 은 여러 노드에서 과반을 모은다. 그 차이가 처리량과 지연에 어떻게 나타나는지 숫자로 본다.

그리고 **왜 이 중 무엇도 완전히 안전하지 않은지**로 간다. GC STW 로 프로세스가 멈추면 TTL 은 이미 지났는데 본인은 락이 있다고 믿는다. 노드마다 시계가 어긋나면 RedLock 의 과반 획득이 무너진다. failover 가 나면 승격된 레플리카에 락이 없다. 결론은 **펜스드 락** — 락만으로는 못 막고 보호 대상 자원까지 협조해야 안전해진다.

### 핫키 — 분배 방식과 슬롯 계산

키 하나에 트래픽이 몰리면 클러스터가 3대여도 **한 대만 탄다.** 먼저 CRC16 으로 슬롯이 어떻게 계산되는지 직접 확인하고(`/api/cluster/locate`), 트래픽을 한 키로 몰아 노드별 지표가 어떻게 갈리는지 본다.

완화 방법을 하나씩 붙여 비교한다 — 로컬 캐시 / 키 샤딩 / 레플리카 읽기. 그리고 **해시태그 `{tag}:key`** 가 양날인 이유를 본다. 묶으면 멀티키 연산이 한 노드에서 끝나지만, 그 슬롯이 곧 핫스팟이 된다.

### RTT 개선 — 왕복과 페이로드

응답 시간을 지배하는 게 Redis 처리 시간이 아니라 **왕복 횟수**인 구간이 있다. 개별 `GET` N 번을 `MGET` 한 번으로 줄이고, 파이프라이닝과 무엇이 다른지 잰다. 클러스터에서는 키가 여러 슬롯에 걸리면 `MGET` 이 `CROSSSLOT` 으로 실패하므로 **슬롯별 분할 병렬 조회**까지 만들어 비교한다.

왕복을 줄였으면 다음은 **한 번에 실려 가는 양**이다. 같은 데이터를 JSON 과 **Smile**(바이너리) 로 넣어 크기 · 직렬화 시간 · 메모리를 잰다. 작은 객체에서는 오히려 JSON 이 빠를 수 있어서, *"바이너리가 항상 낫다"* 를 숫자로 확인하거나 반증하는 게 목적이다.

### 캐시 스탬피드 — 지터 · softTTL · PER

인기 키의 TTL 이 만료되는 순간 수천 요청이 동시에 DB 로 쏟아진다. 세 가지 방법을 같은 부하로 비교한다.

| 방법 | 하는 일 | 목적 |
|---|---|---|
| **지터(jitter)** | TTL 에 무작위 폭을 준다 | 만료 시점을 **흩는다** |
| **soft TTL** | 논리 만료와 실제 만료를 분리, 한 스레드만 갱신 | 원본으로 **한 명만 보낸다** |
| **PER** | soft TTL 을 확률로 바꾸고 재계산 비용을 가중치로 | **비싼 키를 먼저** 보낸다 |

셋은 대안이 아니라 목적이 다르다. 원본 호출 횟수 · p99 · 낡은 데이터 노출 시간을 각각 재서 **언제 무엇을 쓰는지** 정리한다.

실험 대상은 **선착순 쿠폰 발급** 하나로 고정한다. 주차마다 다른 예제를 쓰면 비교가 안 된다.

---

## 시나리오

> **초당 1,000 요청.** 선착순 쿠폰 **3만 장**, 오픈 후 **30초**면 소진된다.
> 발급 요청 뒤로 잔여 수량 조회가 **10배** 더 들어온다 — 사람들이 새로고침을 누르기 때문이다.

```
                         발급  1,000 req/s   ─┐
오픈 30초 · 총 33만 요청                        ├─▶  쿠폰 하나 (키 하나)
                         조회 10,000 req/s  ─┘
```

이 상황이 어려운 이유는 셋이 겹치기 때문이다.

| | |
|---|---|
| **정확해야 한다** | 3만 장인데 3만 900장이 나가면 사고다. 되돌릴 수 없다 |
| **한 곳으로 몰린다** | 쿠폰이 하나라 **키도 하나**다. 클러스터가 3대여도 트래픽은 한 노드로 간다 |
| **원본이 약하다** | 캐시가 비는 순간 7만 요청이 MySQL 로 직행한다. 커넥션 풀은 20개다 |

**락을 걸면 정확해지지만 느려지고, 빼면 빨라지지만 틀린다.** 이 사이에서 무엇을 얼마나 포기할지 정하는 게 6주 동안의 과제다.

### 주차별로 무엇을 푸는가

| 증상 | 무엇이 문제인가 | 주차 |
|---|---|---|
| 3만 장인데 **3만 900장 발급** | 동시 차감. 읽고-쓰는 사이가 비어 있다 | **1** |
| 락을 걸었더니 **처리량이 7K → 400/s** | 경합 대기. 스핀·pub/sub·과반 획득의 비용이 다르다 | **2** |
| 락이 있는데도 **가끔 초과 발급** | GC STW · 클럭 드리프트 · failover 로 락이 증발한다 | **3** |
| 노드 1은 CPU 100%, **2·3은 놀고 있다** | 키가 하나라 슬롯도 하나. 클러스터가 무의미해진다 | **4** |
| 조회 한 번에 **GET 50번** | 왕복 횟수와 페이로드 크기 | **5** |
| 캐시 만료 순간 **커넥션 풀 고갈** | 7만 요청이 동시에 같은 캐시 미스를 본다 | **6** |

1~3주차는 **정확성**, 4~6주차는 **처리량**이다. 순서가 그렇게 잡힌 이유는 — 틀린 결과를 빠르게 내는 건 의미가 없기 때문이다.


---

## 환경

| | |
|---|---|
| Kotlin 2.0.20 · Spring Boot 3.4.4 · Java 21 | 멀티모듈 (`apps` / `modules` / `supports`) |
| **Redis 7.0 클러스터** | 마스터 3 (7001~7003) · 레플리카는 3주차에만 |
| 클라이언트 | Lettuce · Jedis · Redisson **3종 동시** |
| 직렬화 | Jackson JSON · **Smile**(바이너리) |
| 저장소 · 이벤트 | MySQL 8.0 · Kafka (KRaft) |
| 모니터링 | Prometheus + Grafana + redis_exporter |
| 부하 | k6 |

평소엔 Redis 컨테이너 **3개**만 띄운다. 마스터 3개보다 적게는 못 만든다 — Redis Cluster 는 failover 투표에 마스터 과반이 필요해서 `--cluster create` 자체가 거부된다.

레플리카는 **3주차에만** 켠다. 마스터가 락을 받고 레플리카에 전파되기 전에 죽으면 승격된 레플리카에 그 락이 없는데, 이걸 재현하려면 failover 가 실제로 일어나야 한다.

## 시작

```bash
# 1. Redis 클러스터 — up 한 번으로 클러스터 구성까지 끝난다
docker compose -f docker/redis-cluster-compose.yml up -d

# 2. MySQL + Kafka
docker compose -f docker/infra-compose.yml up -d

# 3. 모니터링 (http://localhost:3000 · admin/admin)
docker compose -f docker/monitoring-compose.yml up -d

# 4. 앱
./gradlew :apps:redis-all-pack:bootRun
```

`cluster-init` 서비스가 노드가 healthy 해질 때까지 기다렸다가 클러스터를 만들고 빠진다. 이미 구성돼 있으면 아무것도 안 하므로 `up` 을 여러 번 해도 안전하다.

**3주차에만** 레플리카를 붙인다. Redis 컨테이너가 3개에서 6개로 늘어난다.

```bash
docker compose -f docker/redis-cluster-compose.yml --profile replica down -v
docker compose -f docker/redis-cluster-compose.yml --profile replica up -d
```

`nodes.conf` 가 컨테이너에 남아 있으면 재생성이 실패한다. **`-v` 로 볼륨까지 내려야** 한다.

> ⚠️ `docker/redis-cluster-compose.yml` 의 `x-host-lan-ip` 를 **본인 LAN IP** 로 맞춰야 한다 (`ipconfig getifaddr en0`).
> `127.0.0.1` 로 두면 각 노드가 자기를 `127.0.0.1` 로 알리는데, 그 주소는 다른 노드 컨테이너 안에서 전혀 다른 루프백을 가리켜 노드 간 gossip 이 통째로 깨진다.
> Wi-Fi 가 바뀌면 IP 도 바뀐다. 그때는 `down -v` 후 다시 올린다.

### 동작 확인

```bash
# 쿠폰 등록
curl -XPOST localhost:8080/api/v1/coupons -H 'Content-Type: application/json' \
  -d '{"couponId":"WELCOME","name":"신규가입","totalQuantity":100}'

# 발급
curl -XPOST localhost:8080/api/v1/coupons/WELCOME/issue \
  -H 'Content-Type: application/json' -d '{"userId":"u1"}'

# 잔여 수량 (캐시 여부가 응답에 나온다)
curl localhost:8080/api/v1/coupons/WELCOME/remaining

# 클러스터 관찰
curl localhost:8080/api/cluster/topology
curl 'localhost:8080/api/cluster/locate?key=coupon:remaining:WELCOME'
curl 'localhost:8080/api/cluster/distribution?prefix=coupon:remaining:&count=1000'
```

**락이 없는 상태로 시작한다.** 부하를 때리면 발급 수량이 총량을 넘는다.

```bash
k6 run k6/coupon-issue.js
curl localhost:8080/api/v1/coupons/WELCOME/remaining   # 음수가 나오는지 확인
```

---

## 학습 내용

위 시나리오를 한 주에 하나씩 걷어낸다. 락 세 주차가 이어지도록 순서를 잡았다 — 1주차에서 만든 락이 왜 불안전한지가 3주차에서 결론 나기 때문에 중간에 다른 주제를 끼우면 흐름이 끊긴다.

### 1주차 — 분산락 기초

> **증상**: 3만 장 쿠폰인데 3만 900장이 발급됐다.

락이 **왜 필요한지**를 깨진 상태로 먼저 본다.

- 락 없이 동시 발급 → **발급 수량이 총량을 초과**하는 걸 재현
- `SET NX EX` 로 만드는 가장 단순한 락
- **TTL 이 짧으면**: 작업 중 락이 풀려 두 스레드가 동시에 임계구역에 들어간다
- **TTL 이 길면**: 죽은 프로세스의 락이 남아 아무도 못 들어간다
- 남의 락을 푸는 사고 → **소유자 토큰**과 Lua 로 원자적 해제

> 핵심 질문: TTL 을 얼마로 둬야 하나? **답이 없다는 게 답**이고, 그래서 3주차의 펜스드 락이 나온다.

### 2주차 — 락 구현 3종 비교

> **증상**: 락을 걸었더니 초과 발급은 사라졌는데 처리량이 1,000/s 에서 400/s 로 떨어졌다.

같은 락을 세 가지로 만들어 놓고 **무엇이 다른지** 본다.

| 방식 | 특징 | 볼 것 |
|---|---|---|
| **Lettuce 스핀락** | 직접 루프 돌며 재시도 | 스핀 간격에 따른 CPU · 지연 트레이드오프. 백오프 없으면 어떻게 되나 |
| **Redisson `RLock`** | pub/sub 으로 대기, 워치독이 TTL 연장 | 워치독이 실제로 연장하는지 관찰. 재진입 동작 |
| **RedLock** (`RedissonMultiLock`) | 다수 노드 과반 획득 | 정말 안전한가 — Kleppmann 의 비판과 함께 |

측정: 발급 성공률 · 대기 시간 p99 · 경합 시 CPU (`k6/coupon-issue.js`)

> RedLock 은 **쓰라고 넣는 게 아니라 한계를 보라고** 넣는다. 과반을 얻어도 클럭이 어긋나면 안전하지 않다는 게 3주차로 이어진다.

### 3주차 — 클럭 드리프트와 펜스드 락

> **증상**: 락이 분명히 있는데 하루에 한두 번 초과 발급이 난다.

1·2주차 락이 **왜 근본적으로 불안전한지**. 락 이야기의 결론.

- **GC STW / 프로세스 정지**: 락을 쥔 채 멈췄다 깨어나면 TTL 은 이미 지났다. 본인은 아직 락이 있다고 믿는다
- **클럭 드리프트**: 노드마다 시계가 다르면 만료 시점이 어긋난다. RedLock 의 과반 획득이 무너지는 지점
- **failover 로 락 소실 재현** — 마스터가 전파 전에 죽으면 승격된 레플리카에 락이 없다 (레플리카 프로파일 필요)
- **펜스드 락(fencing token)**: `INCR` 로 단조 증가 토큰을 발급하고, **저장소가 낮은 토큰을 거부**한다
- 락만으로는 못 막고 **보호 대상 자원까지 협조해야** 안전해진다는 결론

> Redis 락은 "대부분의 경우 안전"이지 "항상 안전"이 아니다. 정확성이 필요하면 펜싱이 필요하다.

### 4주차 — 클러스터: 핫키와 해시 슬롯

> **증상**: 노드 1은 CPU 100%, 노드 2·3 은 거의 놀고 있다. 클러스터를 왜 썼나 싶다.

여기서부터 성능. 락에서 클러스터로 무대가 바뀐다.

- CRC16 → 16384 슬롯. 키가 어느 노드로 가는지 계산해보기
- **핫키 재현**: 트래픽의 90%를 인기 쿠폰 하나로 몰고 노드별 지표가 어떻게 갈리는지 (`k6/hotkey.js`)
- 핫키 완화 — 로컬 캐시 / 키 샤딩(`key:1`~`key:N`) / 레플리카 읽기
- **해시태그** `{tag}:key` 로 같은 슬롯에 묶기
- 묶어서 얻는 것(멀티키 연산이 한 노드에서 끝남)과 잃는 것(**쏠림**)
- `CROSSSLOT` 에러를 직접 만나보기

> 해시태그는 양날이다. 묶으면 멀티키 연산이 되지만 그 슬롯이 곧 핫스팟이 된다. 이 트레이드오프가 5주차에서 숫자로 나온다.

### 5주차 — MGET 과 직렬화

> **증상**: 쿠폰 목록 한 번 그리는 데 `GET` 이 50번 나간다. RTT 가 응답 시간을 지배한다.

왕복 횟수를 줄이고, 그 다음 **한 번에 실려 가는 양**을 줄인다.

**멀티키 조회** — `GET /api/v1/coupons/remainings?couponIds=a,b,c`

- N+1 개별 `GET` → `MGET` 한 번
- 클러스터에서 `MGET` 은 **키가 여러 슬롯에 걸리면 실패한다** (`CROSSSLOT`)
- 슬롯별로 키를 나눠 병렬 `MGET` 하는 전략
- 파이프라이닝과의 차이 — RTT 를 줄이는 방식이 다르다
- 측정: 개별 GET · 파이프라인 · MGET · 슬롯 분할 MGET

**직렬화 — JSON vs Smile**

- Smile 은 JSON 과 데이터 모델이 같은 바이너리 포맷이다. 스키마를 따로 안 써도 되고 코드도 그대로다
- 반복되는 키 이름을 백레퍼런스로 공유하므로 **같은 모양의 객체가 많을수록** 이득이 커진다
- 측정: 직렬화 바이트 수 · 직렬화/역직렬화 시간 · Redis 메모리 · MGET p99
- ⚠️ 값이 바이트라 `redis-cli` 로 안 읽힌다. 운영 편의를 얼마나 포기할 가치가 있는지도 같이 본다

> 작은 객체에서는 오히려 JSON 이 빠를 수 있다. **"바이너리가 항상 낫다"를 숫자로 반증하거나 확인하는 게 목적**이다.

### 6주차 — 캐시 스탬피드

> **증상**: 30초마다 한 번씩 응답 시간이 튀고 MySQL 커넥션 풀(20개)이 마른다. 캐시 TTL 과 주기가 정확히 일치한다.

잔여 수량 캐시가 만료되는 순간 **7만 요청이 동시에 DB 로** 쏟아지는 문제.

- 스탬피드 재현 — 만료 직후 MySQL 조회가 몇 번 일어나는지 센다
- **지터(jitter)**: TTL 에 무작위 폭을 줘 만료 시점을 흩는다. 대량 키가 동시에 죽는 걸 막는 가장 싼 방법
- **soft TTL**: 논리적 만료(soft)와 실제 만료(hard)를 분리. soft 를 넘으면 **한 스레드만** 갱신하고 나머지는 옛 값을 그대로 쓴다
- **PER (Probabilistic Early Recomputation)**: soft TTL 을 확률로 바꾼다

  ```
  now - delta * beta * ln(rand()) >= expiry   → 갱신
  ```

  만료가 가까울수록 갱신 확률이 오른다. `delta`(직전 재계산 소요 시간) 가중치가 들어가서 **오래 걸리는 키일수록 더 일찍** 갱신을 시작한다.
- 셋을 같은 부하로 비교 — DB 호출 횟수 · p99 지연 · 낡은 데이터 노출 시간

> 지터는 **분산**시키고, soft TTL 은 **한 명만 보내고**, PER 은 **비싼 키를 먼저 보낸다.** 목적이 서로 다르다.
>
> 1주차의 락으로도 스탬피드를 막을 수 있다. 셋과 비교해서 **락이 왜 여기선 과한 도구인지** 확인한다.

---

## 구조

```
apps/redis-all-pack/
└── src/main/kotlin/com/study/redisallpack/
    ├── interfaces/api/coupon/     CouponV1Controller · Dto
    ├── application/coupon/        CouponFacade · Criteria · Result
    ├── domain/coupon/             Coupon · CouponService · Repository(캐시 포함)
    ├── infrastructure/coupon/     JPA · Redis 구현체
    └── support/                   ClusterInspector · LoadGenerator
modules/
├── redis-cluster/    Lettuce · Jedis · Redisson · 직렬화(JSON/Smile)
├── jpa/              BaseEntity · DataSource — 캐시의 원본 저장소
└── kafka/            KRaft 기반 프로듀서 설정
supports/monitoring/  actuator + prometheus
docker/
├── redis-cluster-compose.yml   마스터 3 + 자동 init (레플리카는 --profile replica)
├── infra-compose.yml           MySQL + Kafka
└── monitoring-compose.yml      Prometheus + Grafana + exporter 3
k6/                             coupon-issue.js · hotkey.js
docs/week1~6.md                 주차별 실험 기록
```

도메인이 `CouponCacheRepository` 인터페이스만 알고 구현은 `infrastructure` 가 한다. **5·6주차에서 구현만 갈아끼워** 지터 · softTTL · PER 을 비교할 수 있게 하기 위해서다.

## 매 주차 규칙

- **깨지는 걸 먼저 재현한다.** 고친 코드부터 쓰면 왜 필요한지 모른 채 지나간다
- 측정 가능한 숫자를 남긴다 (DB 호출 횟수 · p99 · 노드별 부하)
- 안 되는 것도 기록한다. **한계를 아는 것도 결과다**
- 결과는 `docs/weekN.md` 에 적는다
