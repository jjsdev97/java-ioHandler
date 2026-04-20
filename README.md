# IOHandler 래퍼??

`BufferedReader` / `BufferedWriter`를 감싸 `readLine()`, `write()`, `writeLn()`, `validateBuffer()` 같은 메서드를 가진 `IOHandler` 클래스를 만들어 봤습니다. 만들고 나서 "이게 정말 괜찮은 접근일까?" 싶어 찾아보고 AI에게도 물어봤는데, 생각보다 걸리는 부분이 여럿 있었습니다. 기록 삼아 정리해 둡니다.

---

## 0. 왜 만들려고 했는가

동기는 크게 두 가지였습니다.

### (1) 초기화 코드가 장황하게 느껴졌습니다

Java에서 콘솔 입력을 받으려면 보통 이렇게 씁니다.

```java
BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
```

`System.in` → `InputStreamReader` → `BufferedReader` 세 단계를 매번 중첩해서 쓰는 게 번거로웠고, 의도("표준 입력에서 한 줄 읽기")에 비해 선언이 너무 길어 보였습니다. 그래서 "이걸 한 줄로 줄이는 클래스를 만들면 깔끔하지 않을까?" 하는 생각에서 출발했습니다.

### (2) PHP의 습관

동시에, PHP에서 I/O 핸들러 클래스를 만들어 쓰던 습관이 그대로 넘어온 측면도 있습니다. PHP에서는 이런 래퍼가 실제로 꽤 쓸모가 있었습니다.

- 기본 I/O가 절차형 함수 뭉치입니다. `fopen`, `fgets`, `fwrite`, `fclose`, `file_get_contents`, `STDIN` / `STDOUT` … 시그니처가 제각각이고, 언어 기본 레벨에서 제공하는 클래스 기반 추상화가 약합니다.
- 파일 I/O 함수 다수가 실패 시 `false`를 반환하는 컨벤션을 유지합니다. PHP 8에서 타입·인자 오류가 `TypeError` / `ValueError`로 바뀌긴 했지만, `fopen` 실패 같은 건 여전히 `false` 반환이라 호출부마다 체크가 필요합니다. 래퍼가 이걸 예외로 바꿔주면 호출부가 정리됩니다.
- SPL에 `SplFileObject` / `SplFileInfo`가 있긴 하지만, Java의 `BufferedReader`만큼 표준적으로 쓰이지는 않습니다. 실전 코드는 여전히 `fgets` + `feof` 루프로 직접 돌리거나, 프레임워크가 감싼 것을 씁니다.
- 웹 요청/응답 맥락에서는 `header()`, `echo`, `ob_start()` 같은 절차형 API가 기본이라 보일러플레이트가 많습니다. PSR-7이나 Symfony HttpFoundation / Laravel의 `Request`·`Response`처럼 프레임워크 차원에서 감싸주는 것이 관례입니다.

요컨대 PHP에서는 언어 기본 I/O가 절차형이고 파편화되어 있어서, 프로젝트나 프레임워크 차원에서 래퍼를 만드는 흐름이 자연스럽게 자리 잡았습니다.

### Java는 상황이 좀 다릅니다

Java 쪽은 `BufferedReader` / `BufferedWriter` / `Scanner` / `InputStreamReader`가 이미 잘 설계된 클래스 추상화 그 자체입니다.

- 메서드 시그니처가 일관되고, 에러 처리도 예외 기반으로 통일돼 있습니다.
- `AutoCloseable` 계약이 표준에 들어가 있습니다.
- 데코레이터 패턴(`new BufferedReader(new InputStreamReader(System.in))`)으로 이미 조합이 됩니다.

그래서 PHP에서 래퍼가 메웠던 자리가 Java에서는 애초에 비어있지 않습니다. 같은 감각으로 래퍼를 얹으면 잘 된 것 위에 얇은 한 겹을 더 씌우는 꼴이 되고, 아래 1~5 같은 문제들이 따라붙는다는 걸 나중에 알게 됐습니다.

---

## 문제점 — 찾아보고 알게 된 것들

만들고 나서 검색과 AI 대화를 통해 정리된 지적들입니다. 직접 겪은 것도 있고, 앞으로 겪을 수 있었던 것도 있습니다.

### 1. 표준 스트림을 close 하는 부작용

```java
try (IOHandler ioHandler = new IOHandler()) {
    ioHandler.readLine();
    ioHandler.write();
}
```

`try-with-resources`로 감싸면 `IOHandler.close()` → 내부 `BufferedWriter.close()` → 결국 `System.out`까지 닫힌다고 합니다.
한 번 닫힌 `System.out`은 프로세스가 살아있는 동안 되살릴 방법이 없고, 이후로는 어떤 출력도 나가지 않습니다.

`System.in` / `System.out`을 AutoCloseable로 감싸는 건 거의 안티패턴에 가깝다는 지적이 많았습니다.

---

### 2. 예외 의미가 어긋납니다

`validateBuffer()`는 입력이 null이거나 비어있으면 `IOException`을 던지는데, 찾아보니 이 부분이 어긋나 있었습니다. "빈 줄을 읽었다"는 건 I/O가 성공적으로 빈 문자열을 돌려준 상태지 I/O 실패가 아닙니다.

- `IOException`은 스트림 끊김, 파일 접근 실패처럼 I/O 자체의 실패를 위한 예외입니다.
- 빈 입력을 허용할지 말지는 비즈니스 규칙이라, 호출자가 판단할 영역에 가깝습니다.

검증 실패에 `IOException`을 재사용하면 예외 타입이 말하고 싶은 의미가 흐려집니다. 필요하면 `IllegalArgumentException` 정도로 호출자 쪽에서 던지는 편이 자연스럽습니다.

---

### 3. 내부 상태 필드가 재사용성을 깎습니다

현재 구조를 풀어 쓰면 이렇습니다.

```
readLine() → 읽은 값을 필드에 저장
write()   → 필드에 저장된 값을 출력
```

이건 "읽은 것을 그대로 다시 쓴다"는 특수 케이스에 맞춘 모양이라는 걸 검색하면서 알게 됐습니다. 실제 프로그램에선 읽기와 쓰기 사이에 로직이 끼는 경우가 훨씬 많습니다.

```java
String line = br.readLine();
String result = process(line);
bw.write(result);
```

반환값으로 주고받는 쪽이 범용적이고, 테스트하기도 편하고, 동시성 관점에서도 덜 위험합니다. 상태를 필드에 걸어두면 인스턴스를 여러 곳에서 돌려쓸 때 값이 덮어써지는 식의 문제가 생긴다고 합니다.

---

### 4. 원본 API의 부분집합만 노출됩니다

래퍼는 `readLine` / `write`만 뚫어주는데, 찾아보니 실제로 쓰다 보면 이런 것들이 필요해진다고 합니다.

- `ready()` — 읽을 데이터가 있는지 확인
- `read(char[])` — 라인 단위가 아니라 버퍼 단위로 읽기
- `flush()` 타이밍 제어
- `Charset` / `Locale` 지정
- `mark()` / `reset()`

그때마다 래퍼에 구멍을 하나씩 더 뚫게 됩니다. 래퍼를 유지하는 비용이 곧 원본 API를 계속 재노출하는 보일러플레이트가 되는 셈입니다.

---

### 5. 이미 표준 라이브러리로 충분합니다

정리해 놓은 글들을 읽어보니, 단순 콘솔 I/O 정도면 이렇게 쓰는 것이 보통이었습니다.

```java
Scanner sc = new Scanner(System.in);
String line = sc.nextLine();
System.out.println(line);
```

성능이 필요한 쪽이면:

```java
BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
String line = br.readLine();
System.out.println(line);
```

둘 다 그 자리에서 직접 쓰는 쪽이 의도가 더 잘 드러난다는 의견이 많았습니다. 얇은 래퍼가 한 겹 끼면, 읽는 사람은 "이게 뭘 더 해주는 거지?" 하고 한 번 멈춰 서게 되고, 답이 마땅치 않으면 래퍼는 잡음에 가까워집니다.

### 장황함이 정말 거슬린다면

초기화 한 줄이 길다는 것 자체는 실제 문제가 맞습니다. 다만 해법이 꼭 "상태를 가진 클래스"일 필요는 없다는 점을 찾으면서 알게 됐습니다.

- **한 줄짜리 지역 변수**: `var br = new BufferedReader(new InputStreamReader(System.in));` — `var` 한 번이면 체감 길이가 많이 줄어듭니다 (Java 10+).
- **정적 팩토리 메서드**: `IO.stdin()` / `IO.stdout()` 같은 유틸리티 함수 하나로 생성만 대신합니다. 클래스가 상태를 갖지 않으니 close 부작용·예외 오용·상태 공유 문제가 생기지 않습니다.

  ```java
  public final class IO {
      public static BufferedReader stdin() {
          return new BufferedReader(new InputStreamReader(System.in));
      }
  }

  // 호출부
  BufferedReader br = IO.stdin();
  ```
- **반환 타입은 표준 타입 그대로**: 반환값이 `BufferedReader`라서 필요할 때 `ready()`, `mark()` 등 원본 API를 그대로 쓸 수 있습니다. 부분집합으로 줄어들지 않습니다.
- **close 책임은 호출부에**: 생성만 대신해 주고 `try-with-resources`는 호출부에서 겁니다. 표준 스트림을 감싸는 경우엔 아예 close하지 않는 쪽이 안전합니다.

"장황함을 줄이고 싶다"는 요구에 대해 래퍼 클래스는 과한 해법이고, 정적 팩토리 한 개가 거의 같은 이득을 훨씬 낮은 비용으로 준다는 게 결론이었습니다.

---

## 정리

- `BufferedReader`, `BufferedWriter`, `Scanner`, `System.out` 같은 I/O 프리미티브는 그 자체로 이미 꽤 괜찮은 추상화라는 걸 알게 됐습니다.
- 얇게 한 겹 더 씌우면 기능은 줄고(부분집합만 노출) 위험은 늘어납니다(표준 스트림 close, 예외 오용, 상태 공유).
- 래퍼가 정당화되는 건 도메인 규칙이 붙을 때 정도입니다. 프로토콜 프레이밍, 체크섬, 로깅, 암복호화, 재시도 정책 같은 것들입니다.
- "I/O 코드를 조금 짧게 쓰고 싶다" 정도의 동기라면 클래스 래퍼보다 유틸리티 메서드(정적 함수) 쪽이 가볍고 안전한 선택에 가깝습니다.

PHP에서의 직관이 틀렸다기보다는, 언어마다 표준 라이브러리가 채워주는 범위가 달라서 같은 전략이 같은 효용을 주진 않는다는 이야기입니다.

---

## 그래도 얻은 것

결론은 "만들 필요가 없다"지만, 만들어 보면서 — 그리고 그걸 검증하는 과정에서 — 정리된 것들은 남습니다.

- **Java I/O 설계를 다시 훑게 됐습니다.** `InputStreamReader` → `BufferedReader`로 이어지는 데코레이터 패턴, `AutoCloseable`과 `try-with-resources`의 계약, `IOException`의 의미 범위가 이전보다 또렷해졌습니다.
- **"래퍼의 비용"을 감각으로 익혔습니다.** 처음엔 호출부가 깔끔해 보이지만, 표준 스트림 close 문제·예외 오용·필드 상태·원본 API 재노출 같은 숨은 비용이 어디서 새는지 찾아보면서 구체적으로 보였습니다. 다음에 비슷한 충동이 올 때 훨씬 빨리 판단할 수 있을 것 같습니다.
- **언어별 관습이 왜 다른지 체감했습니다.** PHP에서 자연스러웠던 패턴이 Java에서 왜 어색한지를 "느낌"이 아니라 구체적 이유(스트림 수명, 예외 체계, 표준 라이브러리 범위)로 설명할 수 있게 됐습니다. 다른 언어로 넘어갈 때도 같은 질문을 먼저 던질 수 있습니다 — "이 언어의 표준 라이브러리는 어디까지 채워 주는가?"
- **추상화를 언제 얹어야 하는지 기준이 잡혔습니다.** 래퍼는 도메인 규칙(프로토콜, 체크섬, 로깅, 재시도 등)이 붙을 때 정당화됩니다. 그 외에는 표준 타입을 그대로 쓰는 편이 의도가 잘 드러난다는 기준을 하나 얻었습니다.

요컨대 이 실험의 산출물은 `IOHandler.java`라는 클래스가 아니라, "언제 래퍼를 만들지 않을지 판단하는 기준"이었습니다.
