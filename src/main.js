const fileInput = document.querySelector("#document-upload");
const fileName = document.querySelector("#selected-file-name");
const promptInput = document.querySelector("#prompt-input");
const generateButton = document.querySelector("#generate-button");
const outputWindow = document.querySelector("#output-window");
const resetButton = document.querySelector("#reset-button");
const sampleButton = document.querySelector("#sample-button");
const copyButton = document.querySelector("#copy-button");
const questionType = document.querySelector("#question-type");
const difficulty = document.querySelector("#difficulty");
const questionCount = document.querySelector("#question-count");
const jobStatusMessage = document.querySelector("#job-status-message");
const resultCards = document.querySelector("#result-cards");
const heroStatusText = document.querySelector("#hero-status-text");
const uploadStatus = document.querySelector("#upload-status");
const uploadStatusText = document.querySelector("#upload-status-text");
const uploadedFilesList = document.querySelector("#uploaded-files-list");
const historyList = document.querySelector("#history-list");
let uploadedDocumentNames = [];
let uploadAnalysisTimerId = null;
let promptHistory = [];

function formatUploadedFileLabel() {
  if (uploadedDocumentNames.length === 0) {
    return "PDF, PPT, PPTX 여러 개 업로드";
  }

  if (uploadedDocumentNames.length === 1) {
    return `${uploadedDocumentNames[0]} 업로드 완료`;
  }

  return `${uploadedDocumentNames.length}개 문서 업로드 완료`;
}

function renderHistory() {
  if (!historyList) {
    return;
  }

  if (promptHistory.length === 0) {
    historyList.innerHTML =
      '<p class="history-empty">아직 저장된 요청 기록이 없습니다.</p>';
    return;
  }

  historyList.innerHTML = promptHistory
    .map(
      (entry, index) => `
        <button
          type="button"
          class="history-item"
          data-history-index="${index}"
          aria-label="이전 요청 불러오기"
        >
          <span class="history-time">${entry.time}</span>
          <strong class="history-title">${entry.type} · ${entry.count}문항</strong>
          <span class="history-text">${entry.prompt}</span>
        </button>
      `
    )
    .join("");

  historyList.querySelectorAll(".history-item").forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.historyIndex);
      const entry = promptHistory[index];

      if (!entry) {
        return;
      }

      promptInput.value = entry.prompt;
      questionType.value = entry.type;
      difficulty.value = entry.level;
      questionCount.value = String(entry.count);
      outputWindow.textContent =
        "이전 요청을 입력창으로 불러왔습니다. 실행 버튼을 눌러 다시 결과를 확인할 수 있습니다.";
    });
  });
}

function addHistoryEntry(prompt, type, level, count) {
  const now = new Date();
  const time = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  promptHistory = [
    {
      prompt,
      type,
      level,
      count,
      time
    },
    ...promptHistory
  ].slice(0, 8);

  renderHistory();
}

function renderUploadedFiles() {
  if (!uploadedFilesList) {
    return;
  }

  if (uploadedDocumentNames.length === 0) {
    uploadedFilesList.innerHTML =
      '<p class="uploaded-file-empty">아직 업로드된 문서가 없습니다.</p>';
    return;
  }

  uploadedFilesList.innerHTML = uploadedDocumentNames
    .map(
      (name, index) => `
        <article class="uploaded-file-item">
          <div class="uploaded-file-main">
            <span class="uploaded-file-index">${index + 1}</span>
            <span class="uploaded-file-name">${name}</span>
          </div>
          <button
            type="button"
            class="uploaded-file-remove"
            data-file-index="${index}"
            aria-label="${name} 삭제"
            title="삭제"
          >
            x
          </button>
        </article>
      `
    )
    .join("");

  uploadedFilesList.querySelectorAll(".uploaded-file-remove").forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.fileIndex);
      removeUploadedFile(index);
    });
  });
}

function updateEmptyUploadState() {
  uploadedDocumentNames = [];
  fileName.textContent = "PDF, PPT, PPTX 여러 개 업로드";
  setUploadState("idle", "대기 중");
  heroStatusText.textContent = "No document uploaded";
  jobStatusMessage.textContent =
    "문서를 업로드하고 분석이 끝나야 실행 버튼이 활성화됩니다.";
  generateButton.disabled = true;
  setTimeline("queued");
}

function removeUploadedFile(index) {
  if (Number.isNaN(index) || index < 0 || index >= uploadedDocumentNames.length) {
    return;
  }

  uploadedDocumentNames.splice(index, 1);
  renderUploadedFiles();

  if (uploadedDocumentNames.length === 0) {
    if (uploadAnalysisTimerId) {
      window.clearTimeout(uploadAnalysisTimerId);
      uploadAnalysisTimerId = null;
    }
    updateEmptyUploadState();
    return;
  }

  fileName.textContent = formatUploadedFileLabel();
  setUploadState("done", `${uploadedDocumentNames.length}개 문서 분석 완료`);
  heroStatusText.textContent =
    uploadedDocumentNames.length === 1
      ? `${uploadedDocumentNames[0]} ready`
      : `${uploadedDocumentNames.length} documents ready`;
  jobStatusMessage.textContent =
    "업로드 문서 목록을 갱신했습니다. 현재 남아 있는 문서로 실행할 수 있습니다.";
  generateButton.disabled = false;
}

const samplePrompt =
  "운영체제 핵심 개념 위주로 객관식 5문항을 생성하고, 각 문제마다 정답과 짧은 해설을 함께 보여줘.";

const defaultOutput =
  "아직 생성된 결과가 없습니다. 아래 입력창에서 요청을 작성한 뒤 실행해보세요.";

function setUploadState(state, text) {
  if (!uploadStatus || !uploadStatusText) {
    return;
  }

  uploadStatus.classList.remove("idle", "loading", "done");
  uploadStatus.classList.add(state);
  uploadStatusText.textContent = text;
}

function setTimeline(stepName) {
  const steps = document.querySelectorAll(".timeline-step");
  const order = ["queued", "running", "completed"];
  const activeIndex = order.indexOf(stepName);

  steps.forEach((step) => {
    const stepIndex = order.indexOf(step.dataset.step);
    step.classList.toggle("is-active", stepIndex <= activeIndex && activeIndex >= 0);
  });
}

function renderResultCards(type, count, level) {
  const difficultyLabel =
    level === "easy" ? "쉬움" : level === "hard" ? "어려움" : "보통";

  resultCards.innerHTML = "";

  for (let index = 1; index <= count; index += 1) {
    const card = document.createElement("article");
    card.className = "result-card";
    card.innerHTML = `
      <p class="result-label">${type} · ${difficultyLabel}</p>
      <h3>${index}. 생성 결과 예시 문제입니다.</h3>
      <p>정답, 해설, 참고 Section은 이후 실제 기능 연결 단계에서 추가될 예정입니다.</p>
      <div class="result-actions">
        <button type="button" class="ghost-button small-button">정답 보기</button>
        <button type="button" class="ghost-button small-button">해설 보기</button>
        <button type="button" class="ghost-button small-button">참고 Section</button>
      </div>
    `;
    resultCards.appendChild(card);
  }
}

function runGeneration() {
  const prompt = promptInput.value.trim();
  const hasUploadedFile = uploadedDocumentNames.length > 0;

  if (!hasUploadedFile) {
    outputWindow.textContent =
      "문서를 먼저 업로드한 뒤 실행하세요.";
    jobStatusMessage.textContent =
      "실행 실패: 업로드된 문서가 없습니다.";
    setTimeline("queued");
    return;
  }

  if (!prompt) {
    outputWindow.textContent =
      "출제 범위 또는 요청 문구를 먼저 입력하세요.";
    jobStatusMessage.textContent =
      "실행 대기 중: 요청 문구가 비어 있습니다.";
    setTimeline("queued");
    return;
  }

  const type = questionType.value;
  const level = difficulty.value;
  const count = Number(questionCount.value);
  const selectedName = uploadedDocumentNames.join(", ");

  setTimeline("running");
  jobStatusMessage.textContent =
    "RUNNING: 업로드된 문서를 바탕으로 결과를 생성 중입니다.";
  outputWindow.textContent =
    "실행 중입니다. 관련 문서를 검색하고 결과를 정리하고 있습니다.";

  window.setTimeout(() => {
    setTimeline("completed");
    jobStatusMessage.textContent =
      `COMPLETED: ${count}개의 ${type} 결과가 준비되었습니다.`;
    outputWindow.textContent = [
      "[ 생성 결과 요약 ]",
      "",
      `입력 요청: ${prompt}`,
      `문제 유형: ${type}`,
      `난이도: ${level}`,
      `문제 수: ${count}`,
      "",
      `검색 대상 문서: ${selectedName}`,
      "상태: COMPLETED"
    ].join("\n");
    renderResultCards(type, count, level);
    addHistoryEntry(prompt, type, level, count);
  }, 700);
}

if (fileInput && fileName) {
  fileInput.addEventListener("change", (event) => {
    const selectedFiles = [...(event.target.files ?? [])];

    if (selectedFiles.length === 0) {
      updateEmptyUploadState();
      renderUploadedFiles();
      return;
    }

    const nextNames = selectedFiles.map((file) => file.name);
    const mergedNames = [...uploadedDocumentNames];

    nextNames.forEach((name) => {
      if (!mergedNames.includes(name)) {
        mergedNames.push(name);
      }
    });

    uploadedDocumentNames = mergedNames;
    fileName.textContent = formatUploadedFileLabel();
    renderUploadedFiles();
    setUploadState("loading", `${uploadedDocumentNames.length}개 문서 분석 중`);
    heroStatusText.textContent =
      uploadedDocumentNames.length === 1
        ? `${uploadedDocumentNames[0]} analyzing`
        : `${uploadedDocumentNames.length} documents analyzing`;
    jobStatusMessage.textContent =
      "문서를 업로드했습니다. 분석이 완료되면 실행할 수 있습니다.";
    generateButton.disabled = true;
    setTimeline("queued");
    fileInput.value = "";

    if (uploadAnalysisTimerId) {
      window.clearTimeout(uploadAnalysisTimerId);
    }

    uploadAnalysisTimerId = window.setTimeout(() => {
      setUploadState("done", `${uploadedDocumentNames.length}개 문서 분석 완료`);
      heroStatusText.textContent =
        uploadedDocumentNames.length === 1
          ? `${uploadedDocumentNames[0]} ready`
          : `${uploadedDocumentNames.length} documents ready`;
      jobStatusMessage.textContent =
        "문서 분석이 완료되었습니다. 이제 실행 버튼을 사용할 수 있습니다.";
      generateButton.disabled = false;
      uploadAnalysisTimerId = null;
    }, 1200);
  });
}

if (sampleButton && promptInput) {
  sampleButton.addEventListener("click", () => {
    promptInput.value = samplePrompt;
    outputWindow.textContent =
      "샘플 요청이 입력되었습니다. 실행 버튼을 눌러 결과 흐름을 확인해보세요.";
  });
}

if (generateButton) {
  generateButton.addEventListener("click", runGeneration);
}

if (copyButton && outputWindow) {
  copyButton.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(outputWindow.textContent);
      jobStatusMessage.textContent =
        "현재 출력 내용을 클립보드에 복사했습니다.";
    } catch (error) {
      jobStatusMessage.textContent =
        "브라우저 권한 문제로 복사에 실패했습니다.";
    }
  });
}

if (resetButton) {
  resetButton.addEventListener("click", () => {
    promptInput.value = "";
    questionType.value = "객관식";
    difficulty.value = "medium";
    questionCount.value = "5";
    fileInput.value = "";
    if (uploadAnalysisTimerId) {
      window.clearTimeout(uploadAnalysisTimerId);
      uploadAnalysisTimerId = null;
    }
    updateEmptyUploadState();
    renderUploadedFiles();
    outputWindow.textContent = defaultOutput;
    resultCards.innerHTML = `
      <article class="result-card">
        <p class="result-label">미리보기</p>
        <h3>생성된 문제와 요약 결과가 이 영역에 표시됩니다.</h3>
        <p>실제 연결 전에는 더미 결과가 나타나고, 이후 정답과 해설 패널을 붙일 수 있습니다.</p>
      </article>
    `;
  });
}

updateEmptyUploadState();
renderUploadedFiles();
renderHistory();
