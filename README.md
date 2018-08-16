# SCNet-Android
안드로이드(Java)용 네트워크 송수신 프레임워크 라이브러리

## 개요
C++, Swift, Java(Android), C# 에서 공용 헤더 사용으로 네트워크 송수신 과정 통합
액티비티 상태에 따라 서버와의 커넥션을 자동으로 유지

## 기능
- MSB Varint32 Encoding된 헤더 사용
	- 암호화 기능
	- 패킷 타입 지정 가능 (`protobuf`, flatBuffer, native byte array, utf8 string, ...)
- 서버와의 연결상태에 따른 라이프사이클 콜백
- 비동기 응답 콜백

## 라이브러리 연동 방법
(1) ~/app/src/main/proto/ 에 사용할 프로토버퍼 메시지가 정의된 `.proto` 파일 추가
- 프로젝트 루트에서 app/src/main/proto 폴더로 SCNet-pb 프로젝트를 복제(clone)
- SCNet-pb 프로젝트 주소: http://works.ijoon.net/Developer/SCNet-pb/

(2) Project 레벨의 build.gradle의 depenencies에 다음과 같이 추가
```gradle
...
dependencies {
    ...
    classpath 'com.google.protobuf:protobuf-gradle-plugin:0.8.6'
    ...
}
```

(3) 현재 프로젝트(SCNet-Android)를 모듈로 임포트
New - Import Module에서 현재 프로젝트 폴더 선택

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
        Registry.registMessage("packet_1", 0);
        Registry.registMessage("packet_2", 1);
        Registry.registMessage("packet_3", 2);
        Registry.registMessage("packet_4", 3);

        setMainServerInfo("<Your Server IP Address>", <Your Server Port>, <Socket Timeout>); // 반드시 super.onCreate(); 이전에 호출해야 함
        super.onCreate();
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

    @Override
    public void onResponse(int packetType, int cryptType, byte[] bodyBuf) {
        Log.d("logdinobei", "onResponse, packetType: " + packetType);
        switch (packetType) {
            case 0:
                try {
                    ProtoMessage.packet_1 packet1 = ProtoMessage.packet_1.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet1.getNumber(): " + packet1.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case 1:
                try {
                    ProtoMessage.packet_2 packet2 = ProtoMessage.packet_2.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet2.getNumber(): " + packet2.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case 2:
                try {
                    ProtoMessage.packet_3 packet3 = ProtoMessage.packet_3.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet3.getNumber(): " + packet3.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case 3:
                try {
                    ProtoMessage.packet_4 packet4 = ProtoMessage.packet_4.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet4.getNumber(): " + packet4.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}
```
- 메시지 전송 예제 
```java
ProtoMessage.packet_1 packet1 = ProtoMessage.packet_1
                                    .newBuilder()         
                                    .setNumber(<Number You Want To Send>)
                                    .build();
mMyApplication.request(packet1);
```

## ServerManager를 이용한 외부서버(카메라 등) 연동
- 원하는 액티비티에서 ServerManagerCallback 인터페이스 구현
```java
class MainActivity extends SCNetAppCompatActivity implement ServerManagerCallback {
    @Override
    public void onAttaching(int sessionIndex) {
        Log.d("logdinobei", "onAttaching, sessionIndex: " + sessionIndex);
    }

    @Override
    public void onAttached(int sessionIndex) {
        Log.d("logdinobei", "onAttached, sessionIndex: " + sessionIndex);
    }

    @Override
    public void onAttachFailed(int sessionIndex) {
        Log.d("logdinobei", "onAttachFailed, sessionIndex: " + sessionIndex);
    }

    @Override
    public void onDetach(int sessionIndex) {
        Log.d("logdinobei", "onDetach, sessionIndex: " + sessionIndex);
    }

    @Override
    public void onResponse(int sessionIndex, int packetType, int cryptType, byte[] bodyBuf) {
        Log.d("logdinobei", "onResponse, sessionIndex: " + sessionIndex);

        switch (packetType) {
            case 0:
                try {
                    ProtoMessage.packet_1 packet1 = ProtoMessage.packet_1.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet1.getNumber(): " + packet1.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case 1:
                try {
                    ProtoMessage.packet_2 packet2 = ProtoMessage.packet_2.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet2.getNumber(): " + packet2.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case 2:
                try {
                    ProtoMessage.packet_3 packet3 = ProtoMessage.packet_3.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet3.getNumber(): " + packet3.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case 3:
                try {
                    ProtoMessage.packet_4 packet4 = ProtoMessage.packet_4.parseFrom(bodyBuf);
                    Log.d("logdinobei", "packet4.getNumber(): " + packet4.getNumber());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
        }
    }
}
```

- 외부 서버에 연결
```java
int sessionIndex = ServerManager.getInstance().attach(<External Server IP>, <External Server Port>, <Socket Timeout>, <ServerManagerCallback>);
```

- 외부 서버로 패킷 전송
```java
ProtoMessage.packet_1 p = ProtoMessage.packet_1
        .newBuilder()
        .setNumber(<Number You Want To Send>)
        .build();
ServerManager.getInstance().request(sessionIndex, p);
```
