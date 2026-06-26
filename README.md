# Naver Blog Scraper

네이버 블로그 검색 API를 버튼 클릭으로 호출할 수 있게 만든 순수 Java 웹 애플리케이션입니다.

## Architecture

- `domain`: 블로그 게시물과 정렬 옵션 같은 핵심 모델
- `application`: 서비스와 외부 데이터 제공자 인터페이스
- `infrastructure`: 네이버 블로그 API 연동 구현체
- `presentation`: 프론트엔드와 API를 서빙하는 HTTP 서버
- `public`: 검색 화면, 스타일, 브라우저 동작 스크립트

## Environment Variables

```bash
export NAVER_CLIENT_ID="your-client-id"
export NAVER_CLIENT_SECRET="your-client-secret"
export PORT=8080
```

`PORT`는 생략하면 `8080`을 사용합니다.

## Run

```bash
javac -d out $(find src -name '*.java')
java -cp out oop.blog.presentation.BlogApiServer
```

브라우저에서 `http://localhost:8080/`로 접속하면 키워드 입력칸과 조회 버튼을 사용할 수 있습니다.

## API

### Health Check

```http
GET /health
```

### Refresh Blog Posts

버튼을 눌렀을 때 이 API를 호출하면 네이버 API에서 그 시점의 검색 결과를 다시 가져옵니다.

```http
POST /api/blogs/refresh?query=제주도&limit=10&sort=date
```

Query parameters:

- `query`: 검색어
- `limit`: 1부터 100 사이의 검색 건수
- `sort`: `date` 또는 `sim`, 생략하면 `date`

Response example:

```json
{
  "query": "제주도",
  "sort": "DATE",
  "count": 1,
  "items": [
    {
      "title": "제주도 여행",
      "link": "https://blog.naver.com/example/123",
      "description": "본문 요약",
      "bloggerName": "블로거",
      "bloggerLink": "https://blog.naver.com/example",
      "postDate": "20260626"
    }
  ]
}
```
