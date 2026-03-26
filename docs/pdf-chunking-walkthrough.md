# PDF Chunking Walkthrough

이 문서는 현재 구현된 PDF chunking 흐름이 실제로 어떻게 동작하는지 예시 중심으로 설명한다.
기준이 되는 현재 파이프라인은 다음과 같다.

- PDF 업로드
- 페이지 단위 `SourceUnit` 추출
- `SourceUnit` 기반 `Section` 구성
- 전략에 따라 최종 `Chunk` 생성

현재 기본 전략은 `AUTO -> SECTION`이다.
즉, 기본 동작은 페이지를 그대로 최종 chunk로 쓰는 것이 아니라, 먼저 `Section`을 만들고 그 `Section`을 최종 `Chunk`로 변환하는 흐름이다.

## 1. 전체 흐름 한눈에 보기

```text
PDF
-> SourceUnit(PAGE 1, PAGE 2, PAGE 3, ...)
-> Section(의미 단위로 여러 페이지를 묶음)
-> Chunk(최종 출력 단위)
```

정리하면 각 계층의 의미는 다음과 같다.

- `SourceUnit`: 물리적 원문 단위
- `Section`: 의미 구조를 반영한 중간 단위
- `Chunk`: API 응답과 이후 검색/생성에 사용할 최종 단위

## 2. 예시 A: 기본 전략 `AUTO -> SECTION`

### 2.1 예시 PDF 본문
아래와 같은 3페이지짜리 강의자료 PDF를 가정한다.

```text
[1페이지]
1. RAG Overview
RAG는 검색 증강 생성이다.

검색 단계가 필요하다.

[2페이지]
임베딩은 문서를 벡터로 변환한다.

유사도 검색에 사용된다.

[3페이지]
2. Embedding Pipeline
청킹 후 임베딩을 생성한다.
```

### 2.2 1단계: PDF에서 `SourceUnit` 추출
현재 PDF extractor는 페이지마다 하나의 `SourceUnit`을 만든다.
이 단계에서는 아직 의미 단위로 합치지 않는다.

```json
[
  {
    "index": 1,
    "heading": "Page 1",
    "text": "1. RAG Overview\nRAG는 검색 증강 생성이다.\n\n검색 단계가 필요하다."
  },
  {
    "index": 2,
    "heading": "Page 2",
    "text": "임베딩은 문서를 벡터로 변환한다.\n\n유사도 검색에 사용된다."
  },
  {
    "index": 3,
    "heading": "Page 3",
    "text": "2. Embedding Pipeline\n청킹 후 임베딩을 생성한다."
  }
]
```

핵심은 다음이다.

- PDF는 무조건 페이지 기준으로 먼저 나뉜다.
- 이 시점의 `heading`은 기본적으로 `Page N` 형태다.
- 아직 최종 chunk가 아니라 원문 추적용 물리 계층이다.

### 2.3 2단계: `SourceUnit`에서 `Section` 만들기
그 다음 `SectionBuilder`가 각 페이지의 앞부분을 보고 구조적 제목을 찾는다.
이 예시에서는 1페이지의 `1. RAG Overview`, 3페이지의 `2. Embedding Pipeline`을 section 제목으로 해석한다.

그래서 결과는 다음처럼 묶인다.

```json
[
  {
    "order": 1,
    "heading": "1. RAG Overview",
    "sourceType": "PAGE",
    "sourceStartIndex": 1,
    "sourceEndIndex": 2,
    "sourceIndices": [1, 2],
    "text": "RAG는 검색 증강 생성이다.\n\n검색 단계가 필요하다.\n\n임베딩은 문서를 벡터로 변환한다.\n\n유사도 검색에 사용된다."
  },
  {
    "order": 2,
    "heading": "2. Embedding Pipeline",
    "sourceType": "PAGE",
    "sourceStartIndex": 3,
    "sourceEndIndex": 3,
    "sourceIndices": [3],
    "text": "청킹 후 임베딩을 생성한다."
  }
]
```

여기서 중요한 점은 다음이다.

- 1페이지와 2페이지는 하나의 같은 주제로 이어진다고 판단되면 같은 `Section`이 된다.
- `Section`은 여러 페이지를 묶을 수 있다.
- 하지만 원래 어느 페이지에서 왔는지는 `sourceIndices`로 계속 유지된다.

### 2.4 3단계: `Section`에서 최종 `Chunk` 만들기
기본 전략 `AUTO`는 내부적으로 `SECTION`으로 해석된다.
따라서 위 `Section`들이 최종 `Chunk`의 기준이 된다.

이 예시에서는 section 길이가 짧아서 추가 분할 없이 그대로 chunk가 된다.

```json
{
  "originalFilename": "lecture.pdf",
  "contentType": "application/pdf",
  "appliedChunkStrategy": "SECTION",
  "sourceUnitCount": 3,
  "sectionCount": 2,
  "chunkCount": 2,
  "chunks": [
    {
      "order": 1,
      "heading": "1. RAG Overview",
      "sourceType": "PAGE",
      "sourceStartIndex": 1,
      "sourceEndIndex": 2,
      "sourceIndices": [1, 2],
      "text": "RAG는 검색 증강 생성이다.\n\n검색 단계가 필요하다.\n\n임베딩은 문서를 벡터로 변환한다.\n\n유사도 검색에 사용된다."
    },
    {
      "order": 2,
      "heading": "2. Embedding Pipeline",
      "sourceType": "PAGE",
      "sourceStartIndex": 3,
      "sourceEndIndex": 3,
      "sourceIndices": [3],
      "text": "청킹 후 임베딩을 생성한다."
    }
  ]
}
```

즉, 사용자가 기본 전략으로 PDF를 올리면 실제 결과는 대체로 다음 느낌이다.

- 페이지별로 바로 끊기지 않는다.
- 같은 주제로 이어지는 페이지들은 하나의 chunk가 될 수 있다.
- 최종 chunk는 여러 페이지를 포함할 수 있지만, 원문 페이지 범위는 메타데이터로 남는다.

## 3. 예시 B: 같은 PDF를 `PAGE` 전략으로 요청한 경우

같은 PDF라도 `chunkStrategy=PAGE`를 주면 `Section`을 최종 chunk 기준으로 사용하지 않고, 페이지 `SourceUnit`을 그대로 최종 chunk로 만든다.

결과는 아래와 비슷하다.

```json
{
  "appliedChunkStrategy": "PAGE",
  "sourceUnitCount": 3,
  "sectionCount": 2,
  "chunkCount": 3,
  "chunks": [
    {
      "heading": "Page 1",
      "sourceType": "PAGE",
      "sourceStartIndex": 1,
      "sourceEndIndex": 1,
      "sourceIndices": [1],
      "text": "1. RAG Overview\nRAG는 검색 증강 생성이다.\n\n검색 단계가 필요하다."
    },
    {
      "heading": "Page 2",
      "sourceType": "PAGE",
      "sourceStartIndex": 2,
      "sourceEndIndex": 2,
      "sourceIndices": [2],
      "text": "임베딩은 문서를 벡터로 변환한다.\n\n유사도 검색에 사용된다."
    },
    {
      "heading": "Page 3",
      "sourceType": "PAGE",
      "sourceStartIndex": 3,
      "sourceEndIndex": 3,
      "sourceIndices": [3],
      "text": "2. Embedding Pipeline\n청킹 후 임베딩을 생성한다."
    }
  ]
}
```

차이는 분명하다.

- `SECTION`: 의미 단위 중심
- `PAGE`: 물리 페이지 중심

현재 기본값은 `SECTION`이고, `PAGE`는 비교 실험이나 물리 단위 확인 용도로 유지된다고 보면 된다.

## 4. 예시 C: 하나의 section이 너무 길어서 여러 chunk로 나뉘는 경우

이번에는 첫 번째 주제가 2페이지에 걸쳐 아주 길게 이어지는 PDF를 가정한다.

```text
[1페이지]
1. RAG Overview
(매우 긴 설명 1)

(매우 긴 설명 2)

[2페이지]
(매우 긴 설명 3)
```

이 경우 `Section`은 하나로 묶일 수 있다.

```json
{
  "heading": "1. RAG Overview",
  "sourceIndices": [1, 2],
  "text": "매우 긴 설명 전체 ..."
}
```

하지만 최종 `Chunk`를 만들 때 길이 제한을 넘으면 같은 section 안에서도 여러 chunk로 나뉜다.

```json
[
  {
    "heading": "1. RAG Overview (Part 1)",
    "sourceIndices": [1, 2],
    "text": "앞부분 설명 ..."
  },
  {
    "heading": "1. RAG Overview (Part 2)",
    "sourceIndices": [1, 2],
    "text": "중간 설명 ..."
  },
  {
    "heading": "1. RAG Overview (Part 3)",
    "sourceIndices": [1, 2],
    "text": "뒷부분 설명 ..."
  }
]
```

여기서 중요한 점은 다음이다.

- 최종 chunk가 여러 개로 쪼개져도 원래 section 제목은 유지된다.
- 제목 뒤에 `(Part N)`이 붙는다.
- 각 chunk는 여전히 `sourceIndices: [1, 2]`를 유지하므로 원문 페이지 범위를 잃지 않는다.

## 5. 실제로 이해해야 할 핵심

현재 PDF chunking을 이해할 때 중요한 포인트는 아래 네 가지다.

- PDF는 먼저 페이지 단위 `SourceUnit`으로 추출된다.
- 기본 전략에서는 페이지들이 그대로 최종 chunk가 되지 않는다.
- 현재 기본 최종 chunk는 `Section` 기준으로 만들어진다.
- 최종 chunk는 의미 단위를 중심으로 만들되, 원문 페이지 메타데이터를 계속 들고 간다.

즉, 지금 구조는 `페이지를 버리고 의미 단위만 쓰는 구조`도 아니고, `페이지만 그대로 쓰는 구조`도 아니다.
현재 구현은 `페이지를 보존한 상태에서 section 기반 final chunk를 만드는 구조`라고 이해하면 된다.

## 6. 언제 이 문서를 봐야 하나
이 문서는 아래 상황에서 참고하면 된다.

- 프론트나 API 사용자가 PDF 업로드 결과를 예상하고 싶을 때
- `sourceUnitCount`, `sectionCount`, `chunkCount` 차이를 이해하고 싶을 때
- 왜 기본 전략에서 한 chunk가 여러 페이지를 포함할 수 있는지 확인하고 싶을 때
- `SECTION`과 `PAGE` 전략 차이를 비교하고 싶을 때

관련 배경 설명은 [chunking-strategy-issue.md](./chunking-strategy-issue.md)를 참고하면 된다.
