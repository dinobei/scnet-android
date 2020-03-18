# SCNet-Android
안드로이드(Java)용 네트워크 송수신 라이브러리

## 개요
C++, Swift, Java(Android), Golang, C# 에서 패킷별 공용 헤더 사용으로 네트워크 송수신 과정 통합
액티비티 상태에 따라 서버와의 커넥션을 자동으로 유지

## 기능
- MSB Varint32 Encoding된 헤더 사용
	- 암호화 기능
	- 메시지 타입 (`protobuf`, flatBuffer, `native byte array`, ...)
	- 사용자 정의 패킷 타입 (ex: login:0, logout:1, ...)
- 서버와의 연결상태에 따른 라이프사이클 콜백
- 비동기 응답 콜백
- 랑데뷰 클라이언트
	- 사설IP를 사용하는 디바이스(스마트폰, 카메라 등)와의 통신을 위해 모든 NAT 타입을 지원하는 연결 프로토콜 구현체 (RendezvousClient)

## 라이브러리 연동 방법
(1) protobuf message 서브모듈 추가
- ~/app/src/main/proto/ 에 사용할 프로토버퍼 메시지가 정의된 `.proto` 파일 추가하거나
- 프로젝트 루트에서 app/src/main/proto 폴더로 SCNet-pb 프로젝트를 submodule로 추가
    - submodule로 추가를 원치 않을 경우 `.proto` 파일을 직접 추가
- SCNet-pb 프로젝트 주소: http://works.ijoon.net/Developer/SCNet-pb/

```
$ cd ~/app/src/main
$ git submodule add http://works.ijoon.net/developer/scnet-pb app/src/main/proto
```

(2) Project 레벨의 build.gradle의 depenencies에 다음과 같이 추가
```gradle
...
dependencies {
    ...
    classpath 'com.google.protobuf:protobuf-gradle-plugin:0.8.8'
    ...
}
```

(3) SCNet-Android 프로젝트를 개발중인 앱에 모듈로 임포트
아래 명령어를 이용해서 SCNet-Android를 git 서브모듈로 추가

```
$ cd MyApp // 내 앱 최상위 경로로 이동
$ git submodule add http://works.ijoon.net/developer/scnet-android scnet-android
```

settings.gradle 파일을 다음과 같이 수정한 뒤 sync now 버튼 클릭
```
include ':app', ':scnet-android'
project(':scnet-android').projectDir = new File('scnet-android')
```

(4) 모듈 app의 build.gradle에 아래와 같이 구문 추가
```gradle
apply plugin: 'com.google.protobuf'

android {

    ...

    protobuf {
        protoc {
            artifact = 'com.google.protobuf:protoc:3.4.0'
        }
        plugins {
            javalite {
                artifact = 'com.google.protobuf:protoc-gen-javalite:3.0.0'
            }
        }
        generateProtoTasks {
            all().each { task ->
                task.builtins {
                    remove java
                }
                task.plugins {
                    javalite {}
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    ...
    implementation project(':scnet-android')
    implementation 'com.google.protobuf:protobuf-lite:3.0.1'
    ...
}
```


(5) 커스텀 Application을 다음과 같이 구현 (Application 대신 GlobalApplication을 상속 받도록)

```java
public class CustomApplication extends GlobalApplication {
    @Override
    public void onCreate() {
        super.onCreate();

        Registry.registMessage(Packet.Packet1.getDefaultInstance(),
                PacketType2.PacketType.packetType1_VALUE,
                (MessageLite message) -> {
                    Packet.Packet1 pkt1 = (Packet.Packet1)message;
                    Log.d("log", "number: " + pkt1.getNumber());
                });
        Registry.registMessage(Packet.Packet2.getDefaultInstance(),
                PacketType2.PacketType.packetType2_VALUE,
                (MessageLite message) -> {
                    Packet.Packet2 pkt2 = (Packet.Packet2)message;
                    Log.d("log", "str: " + pkt2.getStr());
                });
        Registry.registMessage(Packet.Packet3.getDefaultInstance(),
                PacketType2.PacketType.packetType3_VALUE,
                (MessageLite message) -> {
                    Packet.Packet3 pkt3 = (Packet.Packet3)message;
                    Log.d("log", "bool: " + pkt3.getBoolValue());
                });
        Registry.registMessage(Packet.Packet4.getDefaultInstance(),
                PacketType2.PacketType.packetType4_VALUE,
                (MessageLite message) -> {
                    Packet.Packet4 pkt4 = (Packet.Packet4)message;
                    Log.d("log", "double: " + pkt4.getDoubleValue() + ", float: " + pkt4.getFloatValue());
                });
        Registry.registMessage(0,
                (int packetType, byte[] message, int length) -> {
                    Log.d("log", "rawbyte_1 received: " + new String(message));
                });
        Registry.registMessage(1,
                (int packetType, byte[] message, int length) -> {
                    Log.d("log", "rawbyte_2 received: " + new String(message));
                });

        ServerSession session = new ServerSession("192.168.0.100", 9190, 1000, new SCNetClientCallback() {
            @Override
            public void onAttaching() {
                Log.d("log", "onAttaching");
            }

            @Override
            public void onAttached() {
                Log.d("log", "onAttached");
            }

            @Override
            public void onAttachFailed() {
                Log.d("log", "onAttachFailed");
            }

            @Override
            public void onDetach() {
                Log.d("log", "onDetach");
            }

            @Override
            public void onTimedOut() {
                Log.d("log", "onTimedOut");
            }
        });
        session.start();
    }
}

```

(6) manifests에 Application 클래스 등록

```gradle
<application
    ...
    android:name=".CustomApplication"
    tools:replace="android:name">
```

## 라이브러리 사용방법
- 콜백 수신을 위한 함수 등록
	- 액티비티 부모클래스를 AppCompatActivity 대신 SCNetAppCompatActivity로 변경
	- SCNetClientCallback 인터페이스에 정의된 함수 오버라이딩 (onAttaching, onAttached, onAttachFailed, onDetach, onResponse)

```java
class MainActivity extends SCNetAppCompatActivity {
	@Override
    public void onAttached() {
        Log.d("logdinobei", "onAttached");
    }

    @Override
    public void onAttachFailed() {
        Log.d("logdinobei", "onAttachFailed");
    }

    @Override
    public void onDetach() {
        Log.d("logdinobei", "onDetach");
    }

    @Override
    public void onAttaching() {
        Log.d("logdinobei", "onAttaching");
    }
}
```

- 메시지 전송 예제

```java
Packet.Packet1 packet1 = Packet.Packet1.newBuilder()
                            .setNumber(<Number You Want To Send>)
                            .build();
serverSession.request(packet1);
```
