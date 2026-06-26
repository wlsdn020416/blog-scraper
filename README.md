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
NAVER_CLIENT_ID=your-client-id
NAVER_CLIENT_SECRET=your-client-secret
PORT=8080
```

`PORT`는 생략하면 `8080`을 사용합니다. `KEY = value`처럼 `=` 앞뒤에 공백이 있어도 애플리케이션이 직접 읽을 수 있습니다.

## Run

```bash
javac -d out $(find src -name '*.java')
java -cp out oop.blog.presentation.BlogApiServer
```

또는 스크립트로 실행할 수 있습니다.

```bash
./scripts/build.sh
./scripts/start.sh
```

브라우저에서 `http://localhost:8080/`로 접속하면 키워드 입력칸과 조회 버튼을 사용할 수 있습니다.

## Deploy

### Docker

```bash
docker build -t naver-blog-scraper .
docker run --rm -p 8080:8080 \
  -e NAVER_CLIENT_ID="your-client-id" \
  -e NAVER_CLIENT_SECRET="your-client-secret" \
  naver-blog-scraper
```

### Render

1. GitHub 저장소를 Render Web Service로 연결합니다.
2. Runtime은 Docker를 사용합니다.
3. 환경변수에 `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`을 등록합니다.
4. `render.yaml`을 사용하면 서비스 이름과 Dockerfile 경로가 자동으로 잡힙니다.

`.env`는 로컬 개발용 파일이며 배포 서버에는 올리지 않습니다.

## API

### Health Check

```http
GET /health
```

### Refresh Blog Posts

버튼을 눌렀을 때 이 API를 호출하면 네이버 API에서 그 시점의 검색 결과를 다시 가져옵니다.

```http
POST /api/blogs/refresh?query=제주도&limit=10&start=1&sort=date
```

Query parameters:

- `query`: 검색어
- `limit`: 1부터 100 사이의 검색 건수
- `start`: 검색 시작 위치, 생략하면 `1`
- `sort`: `date` 또는 `sim`, 생략하면 `date`

Response example:

```json
{
  "query": "제주도",
  "sort": "DATE",
  "start": 1,
  "limit": 10,
  "nextStart": 11,
  "count": 1,
  "items": [
    {
      "title": "제주도 여행",
      "link": "https://blog.naver.com/example/123",
      "description": "본문 요약",
      "bloggerName": "블로거",
      "bloggerLink": "https://blog.naver.com/example",
      "postDate": "20260626",
      "imageUrl": "https://blogthumb.pstatic.net/example.jpg"
    }
  ]
}
```

`imageUrl`은 각 블로그 글의 대표 이미지입니다. 글에 대표 이미지가 없거나 페이지 접근이 실패하면 빈 문자열로 내려갑니다.
프론트에서는 원본 이미지를 직접 불러오지 않고 `/api/images?url=...` 프록시를 통해 표시합니다.
