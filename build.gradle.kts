// 루트 빌드: 공통 설정. 각 모듈이 자체 build 파일에서 플러그인을 적용한다.
allprojects {
    group = "io.github.wantaekchoi"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
