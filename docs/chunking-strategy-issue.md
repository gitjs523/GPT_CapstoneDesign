# Chunk 전략 재정의 및 현재 구현 정리

## 1. 문서 작성 목적
이 문서는 현재 문서 처리 파이프라인에서 사용 중인 Chunk 전략의 구현 상태와 한계를 정리하고, 앞으로 어떤 방향으로 개선할지 공유하기 위해 작성한다.

본 프로젝트는 사용자가 업로드한 문서(`PDF`, `PPT`, `PPTX` 등)를 바탕으로 요약, 문제 생성, 정답, 해설 같은 후속 기능으로 확장되는 것을 목표로 한다.
이 과정에서 문서를 어떤 단위로 분리하느냐는 검색 품질, 생성 품질, 원문 추적 가능성에 직접 영향을 준다.

## 2. 왜 Chunk가 필요한가
문서 전체를 하나의 큰 텍스트로 처리하면 다음 문제가 생긴다.

- 문서가 길수록 임베딩 품질이 떨어질 수 있다.
- 여러 개념이 한 번에 섞여 검색 정확도가 낮아질 수 있다.
- 생성 모델이 필요한 근거만 선택적으로 읽기 어렵다.
- 문제, 정답, 해설 생성 시 불필요한 문맥이 함께 들어가 품질이 흔들릴 수 있다.
- 원문에서 어떤 위치를 근거로 썼는지 추적하기 어려워진다.

따라서 문서는 이후 단계에서 다루기 적절한 크기와 의미를 가진 단위로 나뉘어야 한다.

## 3. Chunk가 해야 하는 역할
현재 기준에서 Chunk는 단순 분할 단위가 아니라 다음 역할을 가져야 한다.

- 임베딩 모델이 비교적 명확한 의미 단위로 받아들일 수 있어야 한다.
- 검색 단계에서 특정 주제와 관련된 내용만 찾기 쉬워야 한다.
- 생성 모델이 문제, 정답, 해설을 만들 때 충분한 문맥을 제공해야 한다.
- 원문 기준 위치를 다시 추적할 수 있어야 한다.
- 이후 요약, 문제 생성, 정답 생성, 해설 생성 등에 재사용 가능해야 한다.

즉, Chunk는 `검색을 위한 최소 의미 단위`이면서 동시에 `원문 추적이 가능한 데이터 단위`여야 한다.

## 4. 현재 구현 방식
1. `SourceUnit` 추출 (`Load Phase`)
PDF: `PyMuPDFLoader`나 `UnstructuredPDFLoader` 등을 사용해 `Page` 단위로 `Document` 객체를 생성한다.

PPT/PPTX: UnstructuredPowerPointLoader 등을 사용해 Slide 단위로 텍스트와 메타데이터를 분리한다.

2. 전처리 및 섹션 구성 (`Transform Phase`)
`LangChain`의 `MarkdownHeaderTextSplitter`나 `HTMLHeaderTextSplitter`와 유사한 논리를 적용하는 단계

구조 파악: 텍스트에서 `#`, `##` 또는 `1.`, `가.` 같은 패턴을 감지한다.

`Context Enrichment`: 단순히 텍스트만 가져오는 게 아니라, 해당 텍스트가 속한 **슬라이드 제목(Slide Title)**이나 문서 제목을 메타데이터(Metadata) 영역에 합친다.

3. 최종 청킹 (`Split Phase`)
이제 위에서 정의된 `Section`을 바탕으로 모델의 컨텍스트 윈도우(Context Window)에 최적화된 크기로 자른다.

`RecursiveCharacterTextSplitter`: 섹션 내에서 의미가 끊기지 않도록 \n\n, \n,   순으로 재귀적으로 분할한다.

`Parent-Child Retrieval`: 이때 `LangChain`의 `ParentDocumentRetriever`를 사용하면, 검색은 작은 **Chunk(Child)**로 하고, 모델에게 전달할 때는 그 청크가 포함된 `Section(Parent)` 전체를 전달하여 문맥 이해도를 극대화할 수 있다. /26.04.03

## 5. 현재 지원하는 Chunk 전략

1. `SourceUnit` 추출 (`Loader` 단계)
`LangChain`의 `Unstructured` 파트너 패키지를 사용. 이 라이브러리는 문서의 레이아웃을 분석하는 데 특화되어 있다.

구현 방법: `UnstructuredPDFLoader`나 `UnstructuredPowerPointLoader`를 사용하면서 `mode="elements"` 옵션을 준다.

결과: 텍스트를 통으로 긁어오는 게 아니라, `Title`, `NarrativeText`, `ListItem` 등 의미론적 요소(Element) 단위로 쪼개진 `Document` 객체 리스트를 얻게 된다.

2. 전처리 및 섹션(`Section`) 구성 (`Transform` 단계)
이 단계가 가장 중요하며, 직접적인 로직 구현이 필요하다.

헤딩 감지: `elements`에서 `category == "Title"`인 것들을 찾아내어 이를 기준으로 섹션을 구분한다.

슬라이드 제목 전이: PPT의 경우, 각 슬라이드의 첫 번째 'Title' 요소를 추출하여 해당 슬라이드에 속한 모든 텍스트의 메타데이터에 slide_title이라는 키로 저장한다.

3. 최종 청킹 (`Split` 단계)
이제 섹션 정보가 포함된 `Document` 객체들을 모델이 읽기 좋은 크기로 나눈다.

`ParentDocumentRetriever` 활용: `LangChain`에서 이 전략을 가장 잘 지원하는 기능이다.

`Child Chunk`: 검색을 위해 아주 작게 나눈 청크 (벡터 DB에 저장)

`Parent Chunk (Section)`: LLM에게 전달할 실제 문맥 (`Docstore`에 저장)

사용자가 질문을 던지면 시스템은 작은 `Child Chunk`를 찾지만, 실제 모델에게는 그 청크가 속한 `Section` 전체를 던져준다.

4. 구조적 헤딩 패턴 처리
`LangChain`의 `MarkdownHeaderTextSplitter`를 응용할 수 있다. PDF나 PPT에서 추출한 텍스트를 임시로 마크다운 형식(#, ##)으로 변환한 뒤 이 스플리터를 통과시키면, 헤딩 계층 구조가 자동으로 메타데이터에 기록된다.

## 6. 청킹 수행
```
import os
from langchain_community.document_loaders import UnstructuredFileLoader
from langchain_text_splitters import RecursiveCharacterTextSplitter

# 1. 파일 경로 설정
file_path = "sample.pdf" 

if not os.path.exists(file_path):
    print(f"파일을 찾을 수 없습니다: {file_path}")
else:
    # 2. Unstructured 로더 설정
    loader = UnstructuredFileLoader(
        file_path,
        mode="elements", 
        strategy="fast"
    )
    raw_documents = loader.load()

    # 3. 섹션(Section) 구조화 로직
    structured_docs = []
    current_section_title = "Header/Intro" 

    for doc in raw_documents:
        category = doc.metadata.get("category")
        page_or_slide = doc.metadata.get("page_number") or doc.metadata.get("slide_number")

        if category == "Title":
            current_section_title = doc.page_content
        
        if category in ["NarrativeText", "ListItem", "Table"]:
            doc.metadata["section"] = current_section_title
            doc.metadata["source_unit"] = f"Unit_{page_or_slide}"
            structured_docs.append(doc)

    # 4. 최종 청킹
    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=600,
        chunk_overlap=100
    )
    final_chunks = text_splitter.split_documents(structured_docs)

    # --- 여기서부터 출력부 (else 문 안쪽으로 들여쓰기 됨) ---
    print(f"\n✅ 총 생성된 청크 수: {len(final_chunks)}")
    print("=" * 60)

    for i, chunk in enumerate(final_chunks[:10]): 
        print(f" {i+1}번 청크 정보")
        
        section = chunk.metadata.get('section', 'N/A')
        unit = chunk.metadata.get('source_unit', 'N/A')
        page = chunk.metadata.get('page_number', 'N/A')
        
        print(f"    📍 위치: {unit} (Page {page})")
        print(f"    📂 섹션: {section}")
        print(f"    📝 내용: {chunk.page_content[:150]}...") 
        print("-" * 60)
```
- 코드 입력 후, `python pdf_chunking.py`를 터미널에 입력
- 결과
```
✅ 총 생성된 청크 수: 378
============================================================
 1번 청크 정보
    📍 위치: Unit_2 (Page 2)
    📂 섹션:  핵심 키워드
    📝 내용: 머신 러닝...
------------------------------------------------------------
 2번 청크 정보
    📍 위치: Unit_2 (Page 2)
    📂 섹션:  핵심 키워드
    📝 내용: 지도학습...
------------------------------------------------------------
 3번 청크 정보
    📍 위치: Unit_2 (Page 2)
    📂 섹션:  핵심 키워드
    📝 내용: 비지도학습...
------------------------------------------------------------
 4번 청크 정보
    📍 위치: Unit_2 (Page 2)
    📂 섹션:  핵심 키워드
    📝 내용: 강화학습...
------------------------------------------------------------
 5번 청크 정보
    📍 위치: Unit_2 (Page 2)
    📂 섹션:  핵심 키워드
    📝 내용: 딥 러닝...
------------------------------------------------------------
 6번 청크 정보
    📍 위치: Unit_2 (Page 2)
    📂 섹션:  핵심 키워드
    📝 내용: 과적합...
------------------------------------------------------------
 7번 청크 정보
    📍 위치: Unit_2 (Page 2)
    📂 섹션:  핵심 키워드
    📝 내용: 윤리적 과제...
------------------------------------------------------------
 8번 청크 정보
    📍 위치: Unit_3 (Page 3)
    📂 섹션:  머신러닝 기초
    📝 내용: 머신러닝기계학습, ML; Machine learning...
------------------------------------------------------------
 9번 청크 정보
    📍 위치: Unit_3 (Page 3)
    📂 섹션:  머신러닝 기초
    📝 내용: 데이터를 기반으로 패턴 학습 → 예측이나 분류 작업 수행... 
------------------------------------------------------------
 10번 청크 정보
    📍 위치: Unit_3 (Page 3)
    📂 섹션:  머신러닝 기초
    📝 내용: 학습 방식에 따른 구분...
------------------------------------------------------------
```
## 7. 현재 구조의 한계
현재 구현이 이전보다 나아졌지만, 아직 최종 형태는 아니다.

- `Section` 추출은 여전히 규칙 기반 휴리스틱에 의존한다.
- 모든 문서가 같은 제목 체계나 번호 체계를 쓰지 않기 때문에 오탐/누락 가능성이 있다.
- `chunkStrategy`가 아직 public API에 남아 있어서, 도메인 정책과 실험용 제어값이 완전히 분리되어 있지는 않다.
- `PARAGRAPH`는 section 기반 분할 옵션이지 별도의 독립적 의미 단위 모델은 아니다.
- `SourceUnit`, `Section`, `Chunk`의 영속화는 아직 구현되지 않았다.

즉, 현재 구조는 실용적인 중간 단계이며, 의미 기반 chunking의 기초는 갖췄지만 정책과 저장 구조는 더 정리될 여지가 있다.

## 8. 현재 우리가 실제로 쓰고 있는 방향
현재 구현 기준으로 보면 방향은 이미 꽤 명확하다.

- 물리적 `SourceUnit`은 계속 유지한다.
- 의미 기반 처리를 위해 `Section`을 기본 계층으로 사용한다.
- 기본 최종 `Chunk`는 `SECTION` 기반으로 생성한다.
- 물리적 위치 정보는 최종 `Chunk` 메타데이터에 남긴다.
- `PAGE` / `SLIDE` 전략은 기본 경로라기보다 비교와 실험을 위한 선택지로 유지한다.

정리하면 현재 방향은 다음과 같다.

- 물리적 계층: 출처 추적용
- section 계층: 의미 구조 표현용
- 최종 chunk 계층: 검색/생성 입력용

## 9. 다음 구현 우선순위
현재 구조를 기준으로 보면 다음 우선순위는 아래와 같다.

1. 업로드 요청 매핑에서 웹 DTO 책임을 줄인다.
2. 필요해지면 `client-requested strategy`와 `internally applied strategy`를 분리한다.
3. section 추출 휴리스틱을 문서 유형별로 더 정교하게 다듬는다.
4. `SourceUnit`, `Section`, `Chunk` 영속화 구조를 도입한다.
5. 이후 검색/생성 파이프라인에서 section 기반 chunk를 기본 입력으로 활용한다.

## 10. 결론
현재 구현은 과거의 `페이지/슬라이드 기반 임시 분리` 단계에서 이미 한 단계 나아갔다.
지금은 `SourceUnit -> Section -> Chunk` 구조를 사용하고, 기본 전략도 `AUTO -> SECTION`으로 동작한다.

따라서 현재 프로젝트의 Chunk 전략은 `물리적 단위만 유지하는 구조`가 아니라,
`의미 기반 chunking을 기본으로 삼되 물리 메타데이터를 함께 유지하는 구조`로 이해하는 것이 맞다.

앞으로의 과제는 이 방향을 뒤집는 것이 아니라, section 추출 규칙, 전략 표현 방식, 영속화 구조를 더 정교하게 만드는 것이다.
