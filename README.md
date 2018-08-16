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

## 사용방법
(1) ~/app/src/main/proto/ 에 사용할 프로토버퍼 메시지가 정의된 `.proto` 파일 추가
- http://works.ijoon.net/Developer/SCNet-pb/ 참고

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

        setMainServerInfo("192.168.1.50", 9190, 1000); // 반드시 super.onCreate(); 이전에 호출해야 함
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
