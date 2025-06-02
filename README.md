# wifi-collector-unisem

## 프로젝트 개요
이 프로젝트는 Scala/Akka를 이용하여 Kafka로부터 WiFi 센서가 수집한 정보를 수집하고 처리하는 시스템입니다. 터키 이스탄불 메트로버스 시스템에서 승객 이동 패턴을 분석하기 위해 개발되었습니다.

## 시스템 아키텍처

### 주요 기술 스택
- **Scala 2.13.8**: 함수형 프로그래밍 언어
- **Akka 2.5.11**: 액터 기반 동시성 프레임워크
- **Akka HTTP 10.1.0**: HTTP 서버/클라이언트
- **Apache Kafka 1.0.0**: 분산 스트리밍 플랫폼
- **Redis**: 인메모리 데이터 구조 저장소
- **MariaDB/MySQL**: 관계형 데이터베이스
- **Circe 0.9.3**: JSON 파싱 라이브러리

### 핵심 구성 요소

#### 1. 애플리케이션 진입점
- **MetrobusCollectorMain**: 시스템의 메인 클래스
  - Akka ActorSystem 초기화
  - 인스턴스 중복 실행 방지 (파일 락)
  - ConsumerContainer 시작

#### 2. 컨테이너 및 액터 관리
- **ConsumerContainer**: 모든 액터들의 생명주기를 관리하는 컨테이너
  - Kafka Consumer 액터들
  - Producer 액터들
  - 역 정보 업데이트 액터
  - 승객 수 집계 액터들
  - Graceful shutdown 처리

#### 3. 핵심 액터들
- **ConsumerActor**: Kafka로부터 WiFi 센서 데이터를 소비
  - 여러 센서 타입 지원 (S200-S)
  - WiFi 디바이스 MAC 주소, RSSI, 상태 정보 처리
  - 센서-역 매핑 정보 관리
  - JSON 메시지 파싱 (Circe 사용)

- **ProducerActor**: 처리된 데이터를 분석기로 전송
  
- **StationInfoUpdateActor**: 역 정보 및 센서 매핑 정보 업데이트
  
- **StationPassengerCounterActor**: 플랫폼/게이트 센서 데이터 기반 승객 수 계산
  
- **PassengerCountAggregateActor**: 승객 수 집계 처리

#### 4. 데이터 모델
- **DeviceRecord**: WiFi 디바이스 정보를 담는 케이스 클래스
  - sensorID, sensorType, stationID, stationName
  - deviceID (MAC 주소), power (RSSI), dsStatus
  - datetime, 유효성 검증 메서드들

- **WifiRecordEx**: 센서로부터 받는 JSON 메시지 구조
  - 센서 ID, 타임스탬프, 디바이스 목록, 센서 버전

#### 5. Redis 연동
- **RedisPool**: Redis 연결 풀 관리
- **DoubleRedisHandler**: 중복 방문 처리
- **PassengerCounterRedisHandler**: 승객 수 캐싱

#### 6. 데이터베이스 연동
- **DBDataSource**: HikariCP를 사용한 커넥션 풀 관리
- MariaDB/MySQL 지원

## 주요 기능

### 1. 실시간 WiFi 데이터 수집
- Kafka 토픽에서 WiFi 센서 데이터 실시간 수신
- 멀티 컨슈머 지원 (라운드 로빈 풀링)
- JSON 메시지 파싱 및 검증

### 2. 센서 데이터 처리
- MAC 주소 기반 디바이스 식별
- RSSI 값을 통한 신호 강도 측정
- 디바이스 상태 (dsStatus) 필터링
- 시간대 보정 (터키 → 한국 시간)

### 3. 역-센서 매핑 관리
- API를 통한 역 정보 및 센서 매핑 정보 갱신
- 인메모리 캐싱으로 빠른 조회
- 10분마다 자동 갱신

### 4. 승객 수 집계
- 플랫폼/게이트 센서 데이터 기반 승객 수 계산
- 시간대별 집계 (30초, 1분, 5분)
- Redis를 통한 실시간 캐싱

### 5. 데이터 전송
- 처리된 데이터를 분석기 Kafka 토픽으로 전송
- 디바이스 로그 별도 토픽으로 전송

## 설정 파일

### application.conf 주요 설정
- **Kafka 설정**: 브로커 주소, 토픽명, 그룹 ID
- **Redis 설정**: 서버 IP
- **데이터베이스 설정**: MariaDB 연결 정보
- **API 서비스 설정**: 역 정보 API 엔드포인트
- **시간대 설정**: 센서 시간 보정 값

## 빌드 및 실행

### 빌드
```bash
sbt compile
sbt assembly
```

### 실행
```bash
java -jar target/scala-2.13/metrobus-collector-stations.jar
```

## 모니터링 및 로깅
- Logback을 통한 구조화된 로깅
- Akka 액터 라이프사이클 로깅
- Kafka 컨슈머/프로듀서 상태 모니터링
- Redis 연결 상태 확인

## 에러 처리
- Kafka 연결 끊김 시 자동 재연결
- JSON 파싱 실패 시 로그 기록 후 무시
- DB/Redis 연결 실패 시 graceful degradation
- 액터 실패 시 복구 메커니즘

## 확장성
- 액터 기반 아키텍처로 높은 동시성 지원
- 라운드 로빈 풀을 통한 로드 밸런싱
- 수평적 확장 가능한 Kafka 파티셔닝
- Redis 클러스터링 지원 준비
