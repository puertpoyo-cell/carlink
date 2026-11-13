# CarLink (가제)

경적 대신 **정제된 한 문장**을 앞차·뒤차에 건네는 Pleos Connect 차량용 앱 프로토타입입니다.
현대자동차그룹 차량용 앱 개발 공모전(Code your App into Pleos App Market) 제출용으로 개발 중입니다.

## 앱 소개

- **정제된 메시지**: 미리 정해 둔 30개 문장 중에서만 고를 수 있습니다. 앱은 문장 본문이 아니라 문장 번호만 주고받으므로, 욕설이나 혐오 표현을 보낼 방법이 없습니다.
- **방향만 알려 주는 익명 전달**: 받는 사람은 "앞차에서" 또는 "뒤차에서" 왔다는 것만 알고, 보낸 사람이 누구인지는 알 수 없습니다.
- **수락 후 표시**: 감사·사과·차량 상태 안내 같은 일반 메시지는 받는 사람이 확인을 눌러야 본문이 보입니다.
- **긴급 안전 알림**: 구급차 접근, 전방 사고, 앞차 급정거 등 6개 문장은 수락 절차 없이 화면 최상단에 즉시 표시되고 음성으로 안내됩니다. 알림에는 근거 등급(차량 감지 / 운전자 신고)이 함께 표시됩니다.
- **차량 신호 교차검증**: "트렁크가 열려 있어요", "문이 덜 닫혔어요"를 받으면 Pleos Vehicle SDK 로 내 차량의 도어 상태를 읽어, 실제로 열려 있으면 '확인됨'으로 표시합니다.
- **브랜드별 말투**: 같은 문장을 현대·기아 말투와 제네시스 말투로 바꿔 볼 수 있습니다.

### 현재 구현 범위

| 기능 | 상태 |
| --- | --- |
| 문장 카탈로그(일반 24 + 긴급 6)와 발신 화면 | 구현 |
| 수신 카드(수락·거절·빠른 답장), 긴급 알림 배너와 음성 안내 | 구현 |
| Vehicle SDK 도어 신호 읽기와 교차검증 | 구현 (에뮬레이터 확인 중) |
| 서버 중계(Supabase), 에뮬레이터 2대 간 송수신 | 구현 (에뮬레이터 확인 중) |
| 급감속·비상등 감지에 의한 긴급 알림 자동 발신 | 수동 버튼으로 동작 확인, 차량 신호 연결은 진행 중 |
| 음성 입력(SpeechToText), 위치 기반 앞·뒤 차량 판정(FusedLocation·NaviHelper) | 예정. 현재는 차량 순서를 직접 지정 |

## 실행 방법

1. Android Studio 에 Pleos Connect Emulator 2.1.0 을 설치합니다. ([공식 안내](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator))
2. 이 저장소를 받아 Android Studio 로 엽니다.
   ```bash
   git clone https://github.com/puertpoyo-cell/carlink.git
   ```
3. Pleos 에뮬레이터를 실행한 뒤 `app` 을 Run 합니다. 명령줄에서는 다음과 같습니다.
   ```bash
   ./gradlew installDebug
   ```
4. 서버 없이도 **로컬 데모 모드**로 실행됩니다. 화면 오른쪽 위 `설정·진단` 에서 수신 테스트 버튼을 누르면 일반 메시지와 긴급 알림 화면을 확인할 수 있습니다.

### 에뮬레이터 2대로 송수신하기

1. Supabase 프로젝트를 만들고 SQL Editor 에서 [`server/schema.sql`](server/schema.sql) 을 실행합니다.
2. `local.properties.example` 을 `local.properties` 로 복사해 프로젝트 URL 과 anon 키를 넣고 다시 빌드합니다.
3. 에뮬레이터 2대에 앱을 설치하고, `설정·진단` 에서 한 대는 `CAR-A (순서 3)`, 다른 한 대는 `CAR-B (순서 2)` 로 지정합니다. 숫자가 큰 쪽이 앞차입니다.
4. CAR-B 에서 받는 차량을 `앞차` 로 두고 문장을 누르면 CAR-A 에 "뒤차에서" 메시지가 도착합니다.

### 차량 신호 바꾸기 (교차검증 확인)

```bash
adb forward tcp:8088 tcp:8088
# 브라우저에서 http://127.0.0.1:8088/ 을 열어 Pleos Connect Virtual Console 에서 도어를 엽니다.
```

도어를 연 상태에서 "문이 덜 닫혔어요"를 받아 확인을 누르면 '확인됨' 배지가 표시됩니다.

## 테스트 환경

- Pleos Connect Emulator 2.1.0 (Android 14, API 34, Automotive)
- Pleos Vehicle SDK 2.3.2
- Android Studio, JDK 17, Gradle 8.10.2, Kotlin 2.0.0
- minSdk 28, targetSdk 35

## APK 위치

`apk/` 폴더에 릴리스 APK 를 둡니다. 직접 만들려면 다음 명령을 실행합니다.

```bash
./gradlew assembleRelease
# 결과물: app/build/outputs/apk/release/app-release.apk
```

## 폴더 구성

```
app/src/main/java/kr/carlink/app/
  data/Catalog.kt        문장 카탈로그와 등급
  data/Models.kt         메시지·수신 카드 모델, 앞·뒤 차량 판정
  data/RelayClient.kt    Supabase 중계
  vehicle/VehicleGateway.kt  Pleos Vehicle SDK 연결
  CarLinkViewModel.kt    발신·수신·교차검증·음성 안내
  ui/                    화면
server/schema.sql        중계 테이블
```
