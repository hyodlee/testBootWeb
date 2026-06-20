The OpenShift `jbossews` cartridge documentation can be found at:

http://openshift.github.io/documentation/oo_cartridge_guide.html#tomcat

## ResourceWarningFilter 테스트 방법

`ResourceWarningFilter`는 HTML/JSP 응답에 시스템 자원 경고 배너를 삽입합니다. 운영 기본 임계치는 `90%`이지만, 실제 테스트 중에 서버 자원을 90%까지 올리기는 어렵기 때문에 `src/main/webapp/WEB-INF/web.xml`의 `resourceWarningThresholdPercent` 값을 임시로 낮춰서 확인할 수 있습니다.

예를 들어 로컬 테스트 시 아래처럼 값을 `1`로 낮추면, JVM 힙/시스템 메모리/시스템 CPU 중 하나라도 1% 이상이면 배너가 표시됩니다.

```xml
<init-param>
    <param-name>resourceWarningThresholdPercent</param-name>
    <param-value>1</param-value>
</init-param>
```

확인 절차:

1. `resourceWarningThresholdPercent` 값을 `1`처럼 낮은 값으로 변경합니다.
2. 애플리케이션을 빌드/배포합니다.
3. `/`, `/index.jsp`, `/help.html` 같은 HTML/JSP URL을 엽니다.
4. 화면 상단에 `resource-warning` 배너가 삽입되는지 확인합니다.
5. 테스트 후에는 값을 운영 기본값인 `90`으로 되돌립니다.

HTML이 아닌 요청은 필터가 응답 본문을 수정하지 않으므로, 테스트는 JSP/HTML URL로 진행해야 합니다.
