const fileInput = document.querySelector("#document-upload");
const fileName = document.querySelector("#selected-file-name");
const uploadDropzone = document.querySelector(".upload-dropzone");
const promptInput = document.querySelector("#prompt-input");
const generateButton = document.querySelector("#generate-button");
const outputWindow = document.querySelector("#output-window");
const resetButton = document.querySelector("#reset-button");
const sampleButton = document.querySelector("#sample-button");
const copyButton = document.querySelector("#copy-button");
const questionType = document.querySelector("#question-type");
const difficulty = document.querySelector("#difficulty");
const questionCount = document.querySelector("#question-count");
const customQuestionCount = document.querySelector("#custom-question-count");
const jobStatusMessage = document.querySelector("#job-status-message");
const resultCards = document.querySelector("#result-cards");
const heroStatusText = document.querySelector("#hero-status-text");
const heroEditTitleButton = document.querySelector("#hero-edit-title");
const uploadStatus = document.querySelector("#upload-status");
const uploadStatusText = document.querySelector("#upload-status-text");
const uploadedFilesList = document.querySelector("#uploaded-files-list");
const documentUploadPanel = document.querySelector(".document-upload-panel");
const uploadedFilesPanel = document.querySelector(".uploaded-files-panel");
const historyList = document.querySelector("#history-list");
const leftMenuToggle = document.querySelector("#left-menu-toggle");
const leftMenuDrawer = document.querySelector("#left-menu-drawer");
const settingButtons = document.querySelectorAll("[data-setting-target]");
const quizSettingsToggle = document.querySelector("#quiz-settings-toggle");
const quizSettingsModal = document.querySelector("#quiz-settings-modal");
const quizSettingsConfirm = document.querySelector("#quiz-settings-confirm");
const notebookNewButton = document.querySelector("#notebook-new");
const notebookSaveButton = document.querySelector("#notebook-save");
const notebookSelect = document.querySelector("#notebook-select");
const notebookLoadButton = document.querySelector("#notebook-load");
const notebookDeleteButton = document.querySelector("#notebook-delete");
const notebookStatus = document.querySelector("#notebook-status");
const notebookTitle = heroStatusText;
const welcomeMessage = document.querySelector("#welcome-message");
const loginLink = document.querySelector("#login-link");
const logoutButton = document.querySelector("#logout-button");

const NOTEBOOK_STORAGE_KEY = "snow.notebooks";
const USER_NAME_STORAGE_KEY = "snow.userName";
const ACCESS_TOKEN_STORAGE_KEY = "snow.accessToken";
const USER_EMAIL_STORAGE_KEY = "snow.userEmail";
const API_BASE_URL = "http://3.37.6.60:8080";
const AUTH_HEADER_NAME = "Authorization";
const DOCUMENT_STATUS_POLL_INTERVAL_MS = 3000;
const DOCUMENT_STATUS_MAX_ATTEMPTS = 100;
const defaultOutput = "아직 생성된 결과가 없습니다. 아래 입력창에 요청을 작성하고 실행해보세요.";
const samplePrompt =
  "운영체제 교착상태 개념 위주로 객관식 5문항을 생성하고, 각 문항마다 정답과 짧은 해설을 함께 보여줘.";

let uploadedDocumentNames = [];
let uploadedDocuments = [];
const cancelledDocumentAnalysisIds = new Set();
let uploadAnalysisTimerId = null;
let promptHistory = [];
let currentNotebookId = createNotebookId();

function isAllowedDocumentFile(file) {
  return /\.(pdf|ppt|pptx)$/i.test(file.name);
}

function renderWelcomeMessage() {
  if (!welcomeMessage) {
    return;
  }

  // BACKEND_AUTH_HOOK: 로그인 API가 사용자 프로필을 반환하면 localStorage 대신 서버 세션/JWT 검증 결과의 name 값을 사용하세요.
  const accessToken = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)?.trim();
  const userName = localStorage.getItem(USER_NAME_STORAGE_KEY)?.trim();
  const userEmail = localStorage.getItem(USER_EMAIL_STORAGE_KEY)?.trim();

  if (!accessToken) {
    welcomeMessage.hidden = true;
    welcomeMessage.textContent = "";
    if (loginLink) {
      loginLink.hidden = false;
    }
    if (logoutButton) {
      logoutButton.hidden = true;
    }
    return;
  }

  welcomeMessage.textContent = `${userName || userEmail || "User"}님 환영합니다`;
  welcomeMessage.hidden = false;
  if (loginLink) {
    loginLink.hidden = true;
  }
  if (logoutButton) {
    logoutButton.hidden = false;
  }
}

function clearAuthSession() {
  localStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY);
  localStorage.removeItem(USER_EMAIL_STORAGE_KEY);
  localStorage.removeItem(USER_NAME_STORAGE_KEY);
  renderWelcomeMessage();
}

function buildAuthHeaders(headers = {}, accessToken = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)) {
  const nextHeaders = new Headers(headers);

  if (accessToken) {
    nextHeaders.set(AUTH_HEADER_NAME, `Bearer ${accessToken}`);
  }

  return nextHeaders;
}

async function refreshAccessToken() {
  const accessToken = localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY);

  if (!accessToken) {
    throw new Error("로그인이 필요합니다.");
  }

  const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
    method: "POST",
    headers: buildAuthHeaders({}, accessToken)
  });

  if (!response.ok) {
    clearAuthSession();
    throw new Error("로그인 시간이 만료되었습니다. 다시 로그인하세요.");
  }

  const data = await response.json();
  if (!data?.accessToken) {
    clearAuthSession();
    throw new Error("새 accessToken을 받지 못했습니다.");
  }

  localStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, data.accessToken);
  return data.accessToken;
}

async function authFetch(path, options = {}) {
  const requestUrl = path.startsWith("http") ? path : `${API_BASE_URL}${path}`;
  const requestOptions = {
    ...options,
    headers: buildAuthHeaders(options.headers)
  };

  let response = await fetch(requestUrl, requestOptions);

  if (response.status !== 401) {
    return response;
  }

  const refreshedAccessToken = await refreshAccessToken();

  response = await fetch(requestUrl, {
    ...options,
    headers: buildAuthHeaders(options.headers, refreshedAccessToken)
  });

  if (response.status === 401) {
    clearAuthSession();
  }

  return response;
}

async function logoutUser() {
  // BACKEND_AUTH_HOOK: 실제 로그아웃 연동 시 POST /api/auth/logout 호출 후 서버 세션/토큰을 폐기하세요.
  // BACKEND_AUTH_HOOK: JWT를 localStorage/sessionStorage에 저장한다면 여기서 토큰도 함께 삭제하세요.
  try {
    await authFetch("/api/auth/logout", {
      method: "POST"
    });
  } catch (error) {
    console.warn("Logout request failed.", error);
  } finally {
    clearAuthSession();
  }
}

function setLeftMenuOpen(isOpen) {
  if (!leftMenuToggle || !leftMenuDrawer) {
    return;
  }

  document.body.classList.toggle("left-menu-open", isOpen);
  leftMenuToggle.setAttribute("aria-expanded", String(isOpen));
  leftMenuDrawer.setAttribute("aria-hidden", String(!isOpen));
  leftMenuDrawer.inert = !isOpen;
  leftMenuDrawer.classList.toggle("is-open", isOpen);
}

function createNotebookId() {
  return `notebook-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function isServerNotebookId(notebookId) {
  return /^\d+$/.test(String(notebookId));
}

function normalizeServerNotebook(notebook, cachedNotebook = {}) {
  const id = String(notebook.notebookId);

  return {
    ...cachedNotebook,
    id,
    notebookId: notebook.notebookId,
    title: notebook.title || cachedNotebook.title || "Untitled Project",
    createdAt: notebook.createdAt || cachedNotebook.createdAt,
    updatedAt: notebook.updatedAt || cachedNotebook.updatedAt || new Date().toISOString()
  };
}

function normalizeServerDocument(document) {
  return {
    documentId: document.documentId,
    notebookId: document.notebookId,
    originalFileName: document.originalFileName,
    fileType: document.fileType,
    fileSize: document.fileSize,
    pageCount: document.pageCount,
    analysisStatus: document.analysisStatus,
    uploadedAt: document.uploadedAt
  };
}

function syncUploadedDocumentNames() {
  uploadedDocumentNames = uploadedDocuments
    .map((document) => document.originalFileName)
    .filter(Boolean);
}

function getDocumentStatusLabel(document) {
  if (!document) {
    return "";
  }

  if (document.analysisStatus === "COMPLETED") {
    return "분석 완료";
  }

  if (document.analysisStatus === "FAILED") {
    return "분석 실패";
  }

  if (document.analysisStatus === "CANCELLED") {
    return "문서 분석이 중단되었습니다.";
  }

  if (document.analysisStatus === "UPLOADED") {
    return "분석 대기";
  }

  if (document.analysisStatus === "ANALYZING") {
    return `분석 중 ${document.analysisProgress ?? 1}%`;
  }

  return "";
}

function getDocumentStatusClass(document) {
  if (!document?.analysisStatus) {
    return "";
  }

  return `is-${document.analysisStatus.toLowerCase()}`;
}

function wait(ms) {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function createAnalysisCancelledError(fileLabel) {
  const error = new Error(`${fileLabel} 문서 분석이 중단되었습니다.`);
  error.name = "AnalysisCancelledError";
  return error;
}

function getSavedNotebooks() {
  try {
    // TODO(back-end): GET /api/notebooks 응답으로 Notebook 목록을 가져오면 localStorage 저장소를 대체하세요.
    return JSON.parse(localStorage.getItem(NOTEBOOK_STORAGE_KEY) ?? "[]");
  } catch (error) {
    return [];
  }
}

function setSavedNotebooks(notebooks) {
  // TODO(back-end): POST/PUT/DELETE /api/notebooks 연동 후에는 이 localStorage 임시 저장을 제거하세요.
  localStorage.setItem(NOTEBOOK_STORAGE_KEY, JSON.stringify(notebooks));
}

function upsertSavedNotebook(notebook) {
  const notebooks = getSavedNotebooks();
  const existingIndex = notebooks.findIndex((item) => item.id === notebook.id);

  if (existingIndex >= 0) {
    notebooks[existingIndex] = {
      ...notebooks[existingIndex],
      ...notebook
    };
  } else {
    notebooks.unshift(notebook);
  }

  setSavedNotebooks(notebooks);
  return notebooks;
}

async function syncNotebookList() {
  if (!localStorage.getItem(ACCESS_TOKEN_STORAGE_KEY)) {
    renderNotebookOptions();
    return;
  }

  try {
    const response = await authFetch("/api/notebooks");

    if (!response.ok) {
      throw new Error("Notebook 목록을 불러오지 못했습니다.");
    }

    const serverNotebooks = await response.json();
    const cachedNotebooks = getSavedNotebooks();
    const serverNotebookIds = new Set(serverNotebooks.map((serverNotebook) => String(serverNotebook.notebookId)));
    const syncedNotebooks = serverNotebooks.map((serverNotebook) => {
      const cachedNotebook = cachedNotebooks.find((item) => item.id === String(serverNotebook.notebookId));
      return normalizeServerNotebook(serverNotebook, cachedNotebook);
    });
    const localOnlyNotebooks = cachedNotebooks.filter((notebook) => !serverNotebookIds.has(String(notebook.id)));
    const notebooks = [...syncedNotebooks, ...localOnlyNotebooks];

    setSavedNotebooks(notebooks);

    if (!notebooks.some((notebook) => notebook.id === currentNotebookId) && notebooks.length > 0) {
      currentNotebookId = notebooks[0].id;
    }

    renderNotebookOptions();
  } catch (error) {
    console.warn("Notebook sync failed.", error);
    renderNotebookOptions();
    setNotebookStatus(error.message || "Notebook 목록 동기화에 실패했습니다.");
  }
}

function setNotebookStatus(message) {
  if (notebookStatus) {
    notebookStatus.textContent = message;
  }
}

function getNotebookDisplayTitle() {
  return notebookTitle?.textContent.trim() || "";
}

function focusNotebookTitle() {
  if (!notebookTitle) {
    return;
  }

  notebookTitle.focus();
  const selection = window.getSelection();

  if (!selection) {
    return;
  }

  const range = document.createRange();
  range.selectNodeContents(notebookTitle);
  selection.removeAllRanges();
  selection.addRange(range);
}

function syncSettingButtons() {
  settingButtons.forEach((button) => {
    const target = document.querySelector(`#${button.dataset.settingTarget}`);

    if (!target) {
      return;
    }

    button.classList.toggle("is-active", target.value === button.dataset.settingValue);
  });

  if (customQuestionCount && questionCount) {
    customQuestionCount.value = questionCount.value || "5";
  }
}

function normalizeQuestionCount(value) {
  const count = Math.floor(Number(value));

  if (!Number.isFinite(count)) {
    return 5;
  }

  return Math.min(Math.max(count, 1), 50);
}

function renderNotebookOptions() {
  if (!notebookSelect) {
    return;
  }

  const notebooks = getSavedNotebooks();

  if (notebooks.length === 0) {
    notebookSelect.innerHTML = '<option value="">저장된 Notebook 없음</option>';
    return;
  }

  notebookSelect.innerHTML = notebooks
    .map((notebook) => `<option value="${notebook.id}">${notebook.title}</option>`)
    .join("");
  notebookSelect.value = notebooks.some((notebook) => notebook.id === currentNotebookId)
    ? currentNotebookId
    : notebooks[0].id;
}

function getDefaultResultCardMarkup() {
  return `
    <article class="result-card">
      <p class="result-label">Preview</p>
      <h3>생성된 문제와 요약 결과가 이 영역에 표시됩니다.</h3>
      <p>실제 연결 전에는 예시 결과가 나타나고, 이후 정답과 해설 패널을 붙일 수 있습니다.</p>
    </article>
  `;
}

function renderHistory() {
  if (!historyList) {
    return;
  }

  if (promptHistory.length === 0) {
    historyList.innerHTML = '<p class="history-empty">아직 저장된 요청 기록이 없습니다.</p>';
    return;
  }

  historyList.innerHTML = promptHistory
    .map(
      (entry, index) => `
        <button type="button" class="history-item" data-history-index="${index}" aria-label="이전 요청 불러오기">
          <span class="history-time">${entry.time}</span>
          <strong class="history-title">${entry.type} · ${entry.count}문항</strong>
          <span class="history-text">${entry.prompt}</span>
        </button>
      `
    )
    .join("");

  historyList.querySelectorAll(".history-item").forEach((button) => {
    button.addEventListener("click", () => {
      const entry = promptHistory[Number(button.dataset.historyIndex)];

      if (!entry) {
        return;
      }

      promptInput.value = entry.prompt;
      questionType.value = entry.type;
      difficulty.value = entry.level;
      questionCount.value = String(normalizeQuestionCount(entry.count));
      syncSettingButtons();
      outputWindow.textContent =
        "이전 요청을 입력창으로 불러왔습니다. 실행 버튼을 눌러 다시 결과를 확인할 수 있습니다.";
    });
  });
}

function addHistoryEntry(prompt, type, level, count) {
  const now = new Date();
  const time = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  promptHistory = [{ prompt, type, level, count, time }, ...promptHistory].slice(0, 8);
  renderHistory();
}

function setUploadState(state, text) {
  if (!uploadStatus || !uploadStatusText) {
    return;
  }

  uploadStatus.classList.remove("idle", "loading", "done");
  uploadStatus.classList.add(state);
  uploadStatusText.textContent = text;
}

function formatUploadedFileLabel() {
  if (uploadedDocumentNames.length === 0) {
    return "PDF, PPT, PPTX 여러 개 업로드";
  }

  if (uploadedDocumentNames.length === 1) {
    return `${uploadedDocumentNames[0]} 업로드 완료`;
  }

  return `${uploadedDocumentNames.length}개 문서 업로드 완료`;
}

function renderUploadedFiles() {
  if (!uploadedFilesList) {
    return;
  }

  const hasUploadedFiles = uploadedDocumentNames.length > 0;
  document.body.classList.toggle("has-uploaded-files", hasUploadedFiles);
  documentUploadPanel?.classList.toggle("has-uploaded-files", hasUploadedFiles);
  uploadedFilesPanel?.classList.toggle("has-uploaded-files", hasUploadedFiles);

  if (uploadedDocumentNames.length === 0) {
    uploadedFilesList.innerHTML = '<p class="uploaded-file-empty">아직 업로드된 문서가 없습니다.</p>';
    return;
  }

  uploadedFilesList.innerHTML = uploadedDocumentNames
    .map(
      (name, index) => {
        const document = uploadedDocuments[index];
        const statusLabel = getDocumentStatusLabel(document);
        const statusClass = getDocumentStatusClass(document);

        return `
        <article class="uploaded-file-item">
          <div class="uploaded-file-main">
            <span class="uploaded-file-index">${index + 1}</span>
            <span class="uploaded-file-name">${name}</span>
            ${statusLabel ? `<span class="uploaded-file-status ${statusClass}">${statusLabel}</span>` : ""}
          </div>
          <button type="button" class="uploaded-file-remove" data-file-index="${index}" aria-label="${name} 삭제" title="삭제">x</button>
        </article>
      `;
      }
    )
    .join("");

  uploadedFilesList.querySelectorAll(".uploaded-file-remove").forEach((button) => {
    button.addEventListener("click", () => removeUploadedFile(Number(button.dataset.fileIndex)));
  });
}

function updateUploadedDocumentStatus(documentId, statusData) {
  const documentIndex = uploadedDocuments.findIndex((document) => document.documentId === documentId);

  if (documentIndex < 0) {
    return;
  }

  uploadedDocuments[documentIndex] = {
    ...uploadedDocuments[documentIndex],
    analysisStatus: statusData.analysisStatus,
    analysisProgress: statusData.analysisStatus === "COMPLETED"
      ? 100
      : uploadedDocuments[documentIndex].analysisProgress,
    summaryText: statusData.summaryText ?? uploadedDocuments[documentIndex].summaryText
  };

  renderUploadedFiles();
  upsertSavedNotebook(collectNotebookState());
}

async function pollDocumentAnalysisStatus(notebookId, documentId, fileLabel) {
  for (let attempt = 0; attempt < DOCUMENT_STATUS_MAX_ATTEMPTS; attempt += 1) {
    if (cancelledDocumentAnalysisIds.has(documentId)) {
      throw createAnalysisCancelledError(fileLabel);
    }

    await wait(DOCUMENT_STATUS_POLL_INTERVAL_MS);

    if (cancelledDocumentAnalysisIds.has(documentId)) {
      throw createAnalysisCancelledError(fileLabel);
    }

    const response = await authFetch(`/api/notebooks/${notebookId}/documents/${documentId}/status`);

    if (!response.ok) {
      throw new Error(`${fileLabel} 분석 상태를 확인하지 못했습니다.`);
    }

    const statusData = await response.json();
    updateUploadedDocumentStatus(documentId, statusData);

    if (statusData.analysisStatus === "COMPLETED") {
      return statusData;
    }

    if (statusData.analysisStatus === "FAILED") {
      uploadedDocuments = uploadedDocuments.filter((document) => document.documentId !== documentId);
      syncUploadedDocumentNames();
      renderUploadedFiles();
      upsertSavedNotebook(collectNotebookState());
      throw new Error(`${fileLabel} 문서 분석에 실패했습니다.`);
    }

    const progressPercent = Math.min(
      99,
      Math.round(((attempt + 1) / DOCUMENT_STATUS_MAX_ATTEMPTS) * 100)
    );
    const documentIndex = uploadedDocuments.findIndex((document) => document.documentId === documentId);
    if (documentIndex >= 0) {
      uploadedDocuments[documentIndex] = {
        ...uploadedDocuments[documentIndex],
        analysisStatus: "ANALYZING",
        analysisProgress: progressPercent
      };
      renderUploadedFiles();
      upsertSavedNotebook(collectNotebookState());
    }
    setUploadState("loading", `${fileLabel} 분석 중 ${progressPercent}%`);
  }

  throw new Error(`${fileLabel} 분석 시간이 초과되었습니다.`);
}

function updateEmptyUploadState() {
  uploadedDocumentNames = [];
  uploadedDocuments = [];
  if (fileName) {
    fileName.textContent = "PDF, PPT, PPTX 여러 개 업로드";
  }
  setUploadState("idle", "대기 중");
  if (jobStatusMessage) {
    jobStatusMessage.textContent = "문서를 업로드하면 실행 버튼이 활성화됩니다.";
  }
  if (generateButton) {
    generateButton.disabled = true;
  }
}

async function removeUploadedFile(index) {
  if (Number.isNaN(index) || index < 0 || index >= uploadedDocumentNames.length) {
    return;
  }

  const document = uploadedDocuments[index];
  const removedFileName = uploadedDocumentNames[index];

  if (document?.documentId) {
    cancelledDocumentAnalysisIds.add(document.documentId);
  }

  if (document) {
    uploadedDocuments[index] = {
      ...document,
      analysisStatus: "CANCELLED",
      analysisProgress: null
    };
  }
  renderUploadedFiles();
  upsertSavedNotebook(collectNotebookState());

  if (document?.documentId && isServerNotebookId(currentNotebookId)) {
    try {
      const response = await authFetch(`/api/notebooks/${currentNotebookId}/documents/${document.documentId}`, {
        method: "DELETE"
      });

      if (!response.ok) {
        throw new Error(`${removedFileName} 서버 삭제에 실패했습니다.`);
      }

      jobStatusMessage.textContent = `${removedFileName} 문서 분석을 중단하고 삭제했습니다.`;
    } catch (error) {
      jobStatusMessage.textContent = error.message || `${removedFileName} 삭제에 실패했습니다. 서버 분석이 계속될 수 있습니다.`;
    }
  } else {
    jobStatusMessage.textContent = `${removedFileName} 문서를 목록에서 제거했습니다.`;
  }

  if (uploadedDocumentNames.length > 0) {
    fileName.textContent = formatUploadedFileLabel();
    setUploadState("done", `${uploadedDocumentNames.length}개 문서`);
    generateButton.disabled = false;
  }
}

function renderResultCards(type, count, level) {
  const difficultyLabel = level === "easy" ? "쉬움" : level === "hard" ? "어려움" : "보통";
  resultCards.innerHTML = "";

  for (let index = 1; index <= count; index += 1) {
    const card = document.createElement("article");
    card.className = "result-card";
    card.innerHTML = `
      <p class="result-label">${type} · ${difficultyLabel}</p>
      <h3>${index}. 생성 결과 예시 문제입니다.</h3>
      <p>정답, 해설, 참고 섹션은 이후 실제 기능 연결 단계에서 추가할 예정입니다.</p>
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
    outputWindow.textContent = "문서를 먼저 업로드한 뒤 실행하세요.";
    jobStatusMessage.textContent = "실행 실패: 업로드된 문서가 없습니다.";
    return;
  }

  if (!prompt) {
    outputWindow.textContent = "출제 범위 또는 요청 문구를 먼저 입력하세요.";
    jobStatusMessage.textContent = "실행 대기 중: 요청 문구가 비어 있습니다.";
    return;
  }

  const type = questionType.value;
  const level = difficulty.value;
  const count = normalizeQuestionCount(questionCount.value);
  questionCount.value = String(count);
  syncSettingButtons();
  const selectedName = uploadedDocumentNames.join(", ");

  jobStatusMessage.textContent = "RUNNING: 업로드된 문서를 바탕으로 결과를 생성 중입니다.";
  outputWindow.textContent = "실행 중입니다. 관련 문서를 검색하고 결과를 정리하고 있습니다.";

  // TODO(back-end): POST /api/quizzes/generate 같은 엔드포인트로 prompt, type, level, count, documentIds를 전송하세요.
  // TODO(back-end): 아래 setTimeout 목업을 실제 응답 처리로 교체하고 resultCards에는 서버가 반환한 문제/정답/해설을 렌더링하세요.
  window.setTimeout(() => {
    jobStatusMessage.textContent = `COMPLETED: ${count}개의 ${type} 결과가 준비되었습니다.`;
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

function collectNotebookState() {
  return {
    id: currentNotebookId,
    title: notebookTitle?.textContent.trim() || "Untitled Project",
    updatedAt: new Date().toISOString(),
    uploadedDocumentNames: [...uploadedDocumentNames],
    uploadedDocuments: [...uploadedDocuments],
    promptHistory: [...promptHistory],
    quizSettings: {
      questionType: questionType.value,
      difficulty: difficulty.value,
      questionCount: questionCount.value
    },
    prompt: promptInput?.value ?? "",
    output: outputWindow?.textContent ?? "",
    resultCardsHtml: resultCards?.innerHTML ?? ""
  };
}

function resetWorkspaceState(title = "") {
  if (uploadAnalysisTimerId) {
    window.clearTimeout(uploadAnalysisTimerId);
    uploadAnalysisTimerId = null;
  }

  currentNotebookId = createNotebookId();
  promptHistory = [];
  uploadedDocuments = [];
  uploadedDocumentNames = [];
  if (notebookTitle) {
    notebookTitle.textContent = title;
  }
  if (promptInput) {
    promptInput.value = "";
  }
  questionType.value = "객관식";
  difficulty.value = "medium";
  questionCount.value = "5";
  syncSettingButtons();
  if (fileInput) {
    fileInput.value = "";
  }
  updateEmptyUploadState();
  renderUploadedFiles();
  renderHistory();
  outputWindow.textContent = defaultOutput;
  resultCards.innerHTML = getDefaultResultCardMarkup();
}

async function saveCurrentNotebook() {
  const notebook = collectNotebookState();
  const hasServerNotebook = isServerNotebookId(notebook.id);
  const requestPath = hasServerNotebook ? `/api/notebooks/${notebook.id}` : "/api/notebooks";
  const requestMethod = hasServerNotebook ? "PATCH" : "POST";

  setNotebookStatus(`"${notebook.title}" Notebook을 저장하는 중입니다.`);

  try {
    const response = await authFetch(requestPath, {
      method: requestMethod,
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ title: notebook.title })
    });

    if (!response.ok) {
      throw new Error("Notebook 저장에 실패했습니다.");
    }

    const serverNotebook = await response.json();
    const savedNotebook = normalizeServerNotebook(serverNotebook, notebook);
    currentNotebookId = savedNotebook.id;
    upsertSavedNotebook(savedNotebook);
    renderNotebookOptions();
    setNotebookStatus(`"${savedNotebook.title}" Notebook을 저장했습니다.`);
    return savedNotebook;
  } catch (error) {
    upsertSavedNotebook(notebook);
    renderNotebookOptions();
    setNotebookStatus(error.message || "Notebook 저장에 실패했습니다. 임시 저장만 완료했습니다.");
    return null;
  }
}

function loadNotebook(notebookId) {
  // TODO(back-end): GET /api/notebooks/{notebookId}로 상세 상태를 받아와 아래 화면 상태 복원에 사용하세요.
  const notebook = getSavedNotebooks().find((item) => item.id === notebookId);

  if (!notebook) {
    setNotebookStatus("불러올 Notebook을 선택하세요.");
    return;
  }

  currentNotebookId = notebook.id;
  uploadedDocuments = [...(notebook.uploadedDocuments ?? [])];
  uploadedDocumentNames = uploadedDocuments.length > 0
    ? uploadedDocuments.map((document) => document.originalFileName).filter(Boolean)
    : [...(notebook.uploadedDocumentNames ?? [])];
  promptHistory = [...(notebook.promptHistory ?? [])];
  notebookTitle.textContent = notebook.title;
  promptInput.value = notebook.prompt ?? "";
  questionType.value = notebook.quizSettings?.questionType ?? "객관식";
  difficulty.value = notebook.quizSettings?.difficulty ?? "medium";
  questionCount.value = String(normalizeQuestionCount(notebook.quizSettings?.questionCount ?? "5"));
  syncSettingButtons();
  renderUploadedFiles();
  renderHistory();

  if (uploadedDocumentNames.length > 0) {
    fileName.textContent = formatUploadedFileLabel();
    setUploadState("done", `${uploadedDocumentNames.length}개 문서 분석 완료`);
    generateButton.disabled = false;
  } else {
    updateEmptyUploadState();
  }

  outputWindow.textContent = notebook.output || defaultOutput;
  resultCards.innerHTML = notebook.resultCardsHtml || getDefaultResultCardMarkup();
  setNotebookStatus(`"${notebook.title}" Notebook을 불러왔습니다.`);
}

async function deleteSelectedNotebook() {
  const notebookId = notebookSelect?.value;

  if (!notebookId) {
    setNotebookStatus("삭제할 Notebook을 선택하세요.");
    return;
  }

  const notebook = getSavedNotebooks().find((item) => item.id === notebookId);

  if (!notebook || !window.confirm(`"${notebook.title}" Notebook을 삭제할까요?`)) {
    return;
  }

  try {
    if (isServerNotebookId(notebookId)) {
      const response = await authFetch(`/api/notebooks/${notebookId}`, {
        method: "DELETE"
      });

      if (!response.ok) {
        throw new Error("Notebook 삭제에 실패했습니다.");
      }
    }

    setSavedNotebooks(getSavedNotebooks().filter((item) => item.id !== notebookId));
    renderNotebookOptions();
    if (currentNotebookId === notebookId) {
      currentNotebookId = createNotebookId();
    }
    setNotebookStatus(`"${notebook.title}" Notebook을 삭제했습니다.`);
  } catch (error) {
    setNotebookStatus(error.message || "Notebook 삭제에 실패했습니다.");
  }
}

function openQuizSettings() {
  if (!quizSettingsToggle || !quizSettingsModal) {
    return;
  }

  quizSettingsToggle.setAttribute("aria-expanded", "true");
  quizSettingsModal.hidden = false;
  quizSettingsModal.querySelector(".setting-button.is-active")?.focus();
}

function closeQuizSettings() {
  if (!quizSettingsToggle || !quizSettingsModal) {
    return;
  }

  quizSettingsToggle.setAttribute("aria-expanded", "false");
  quizSettingsModal.hidden = true;
  quizSettingsToggle.focus();
}

settingButtons.forEach((button) => {
  button.addEventListener("click", () => {
    const target = document.querySelector(`#${button.dataset.settingTarget}`);

    if (!target) {
      return;
    }

    target.value = button.dataset.settingValue;
    target.dispatchEvent(new Event("change", { bubbles: true }));
    syncSettingButtons();
  });
});

customQuestionCount?.addEventListener("input", () => {
  if (!questionCount) {
    return;
  }

  const count = normalizeQuestionCount(customQuestionCount.value);
  questionCount.value = String(count);
  customQuestionCount.value = String(count);
  syncSettingButtons();
});

quizSettingsToggle?.addEventListener("click", openQuizSettings);
quizSettingsConfirm?.addEventListener("click", closeQuizSettings);
quizSettingsModal?.querySelectorAll("[data-close-settings]").forEach((control) => {
  control.addEventListener("click", closeQuizSettings);
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && quizSettingsModal && !quizSettingsModal.hidden) {
    closeQuizSettings();
  }

  if (event.key === "Escape" && leftMenuDrawer?.classList.contains("is-open")) {
    setLeftMenuOpen(false);
    leftMenuToggle?.focus();
  }
});

leftMenuToggle?.addEventListener("click", () => {
  setLeftMenuOpen(leftMenuToggle.getAttribute("aria-expanded") !== "true");
});

async function handleSelectedDocumentFiles(files) {
  const selectedFiles = [...files].filter(isAllowedDocumentFile);

  if (selectedFiles.length === 0) {
    if (uploadedDocumentNames.length === 0) {
      updateEmptyUploadState();
    }
    renderUploadedFiles();
    jobStatusMessage.textContent = "PDF, PPT, PPTX 파일만 업로드할 수 있습니다.";
    return;
  }

  if (!isServerNotebookId(currentNotebookId)) {
    const savedNotebook = await saveCurrentNotebook();

    if (!savedNotebook || !isServerNotebookId(currentNotebookId)) {
      jobStatusMessage.textContent = "문서를 업로드하려면 Notebook을 먼저 저장해야 합니다.";
      setUploadState("idle", "Notebook 저장 필요");
      fileInput.value = "";
      return;
    }
  }

  setUploadState("loading", `${selectedFiles.length}개 문서 업로드 중`);
  jobStatusMessage.textContent = "문서를 서버에 업로드하는 중입니다.";
  generateButton.disabled = true;
  fileInput.value = "";

  if (uploadAnalysisTimerId) {
    window.clearTimeout(uploadAnalysisTimerId);
    uploadAnalysisTimerId = null;
  }

  try {
    for (const file of selectedFiles) {
      const formData = new FormData();
      formData.append("file", file);

      const response = await authFetch(`/api/notebooks/${currentNotebookId}/documents`, {
        method: "POST",
        body: formData
      });

      if (!response.ok) {
        throw new Error(`${file.name} 업로드에 실패했습니다.`);
      }

      const document = normalizeServerDocument(await response.json());
      document.analysisProgress = document.analysisStatus === "COMPLETED" ? 100 : 0;
      const existingIndex = uploadedDocuments.findIndex((item) => item.documentId === document.documentId);

      if (existingIndex >= 0) {
        uploadedDocuments[existingIndex] = document;
      } else {
        uploadedDocuments.push(document);
      }

      syncUploadedDocumentNames();
      fileName.textContent = formatUploadedFileLabel();
      renderUploadedFiles();
      upsertSavedNotebook(collectNotebookState());

      setUploadState("loading", `${document.originalFileName || file.name} 분석 대기 중`);
      jobStatusMessage.textContent = `${document.originalFileName || file.name} 문서 분석 완료를 기다리는 중입니다.`;
      await pollDocumentAnalysisStatus(
        currentNotebookId,
        document.documentId,
        document.originalFileName || file.name
      );
    }

    syncUploadedDocumentNames();
    fileName.textContent = formatUploadedFileLabel();
    renderUploadedFiles();
    upsertSavedNotebook(collectNotebookState());
    setUploadState("done", `${uploadedDocumentNames.length}개 문서 분석 완료`);
    jobStatusMessage.textContent = "문서 분석이 완료되었습니다. 이제 실행 버튼을 사용할 수 있습니다.";
    generateButton.disabled = uploadedDocumentNames.length === 0;
  } catch (error) {
    syncUploadedDocumentNames();
    renderUploadedFiles();
    setUploadState(uploadedDocumentNames.length > 0 ? "done" : "idle", error.message || "문서 업로드에 실패했습니다.");
    jobStatusMessage.textContent = error.message || "문서 업로드에 실패했습니다.";
    generateButton.disabled = uploadedDocumentNames.length === 0;
  }
}

fileInput?.addEventListener("change", (event) => {
  handleSelectedDocumentFiles(event.target.files ?? []);
});

uploadDropzone?.addEventListener("dragover", (event) => {
  event.preventDefault();
  uploadDropzone.classList.add("is-drag-over");
});

uploadDropzone?.addEventListener("dragleave", (event) => {
  if (!uploadDropzone.contains(event.relatedTarget)) {
    uploadDropzone.classList.remove("is-drag-over");
  }
});

uploadDropzone?.addEventListener("drop", (event) => {
  event.preventDefault();
  uploadDropzone.classList.remove("is-drag-over");
  handleSelectedDocumentFiles(event.dataTransfer?.files ?? []);
});

sampleButton?.addEventListener("click", () => {
  promptInput.value = samplePrompt;
  outputWindow.textContent = "샘플 요청을 입력했습니다. 실행 버튼을 눌러 결과 흐름을 확인해보세요.";
});

generateButton?.addEventListener("click", runGeneration);

copyButton?.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(outputWindow.textContent);
    jobStatusMessage.textContent = "현재 출력 내용을 클립보드에 복사했습니다.";
  } catch (error) {
    jobStatusMessage.textContent = "브라우저 권한 문제로 복사에 실패했습니다.";
  }
});

resetButton?.addEventListener("click", () => resetWorkspaceState(getNotebookDisplayTitle()));
notebookNewButton?.addEventListener("click", () => {
  resetWorkspaceState("");
  setNotebookStatus("새 Notebook을 시작했습니다.");
});
notebookSaveButton?.addEventListener("click", saveCurrentNotebook);
notebookLoadButton?.addEventListener("click", () => loadNotebook(notebookSelect?.value));
notebookDeleteButton?.addEventListener("click", deleteSelectedNotebook);
heroEditTitleButton?.addEventListener("click", focusNotebookTitle);
logoutButton?.addEventListener("click", logoutUser);
notebookTitle?.addEventListener("keydown", (event) => {
  if (event.key === "Enter") {
    event.preventDefault();
    saveCurrentNotebook();
    notebookTitle.blur();
  }
});

updateEmptyUploadState();
renderUploadedFiles();
renderHistory();
syncSettingButtons();
renderWelcomeMessage();
syncNotebookList();
setLeftMenuOpen(false);
