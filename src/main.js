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
const withdrawButton = document.querySelector("#withdraw-button");
const quizJobsRefreshButton = document.querySelector("#quiz-jobs-refresh");
const quizJobsList = document.querySelector("#quiz-jobs-list");

const NOTEBOOK_STORAGE_KEY = "snow.notebooks";
const USER_NAME_STORAGE_KEY = "snow.userName";
const USER_EMAIL_STORAGE_KEY = "snow.userEmail";
const API_BASE_URL = "";
const AUTH_HEADER_NAME = "Authorization";
const DOCUMENT_STATUS_POLL_INTERVAL_MS = 3000;
const DOCUMENT_STATUS_MAX_ATTEMPTS = 100;
const QUIZ_JOB_STATUS_POLL_INTERVAL_MS = 3000;
const QUIZ_JOB_STATUS_MAX_ATTEMPTS = 100;
const DEFAULT_QUIZ_DIFFICULTY = "medium";
const defaultOutput = "아직 생성된 결과가 없습니다. 아래 입력창에 요청을 작성하고 실행해보세요.";
const samplePrompt =
  "운영체제 교착상태 개념 위주로 객관식 5문항을 생성하고, 각 문항마다 정답과 짧은 해설을 함께 보여줘.";

let uploadedDocumentNames = [];
let uploadedDocuments = [];
const cancelledDocumentAnalysisIds = new Set();
const activeDocumentAnalysisPolls = new Set();
let uploadAnalysisTimerId = null;
let promptHistory = [];
let currentNotebookId = createNotebookId();
let accessToken = null;
let refreshPromise = null;
let refreshTimerId = null;

localStorage.removeItem("snow.accessToken");

function setJobStatus(message, { loading = false } = {}) {
  if (!jobStatusMessage) {
    return;
  }

  jobStatusMessage.hidden = false;
  jobStatusMessage.textContent = message;
  jobStatusMessage.classList.toggle("is-loading", loading);
}

function isAllowedDocumentFile(file) {
  return /\.(pdf|ppt|pptx)$/i.test(file.name);
}

function renderWelcomeMessage() {
  if (!welcomeMessage) {
    return;
  }

  const userName = localStorage.getItem(USER_NAME_STORAGE_KEY)?.trim();
  const userEmail = localStorage.getItem(USER_EMAIL_STORAGE_KEY)?.trim();

  if (!getAccessToken()) {
    welcomeMessage.hidden = true;
    welcomeMessage.textContent = "";
    if (loginLink) {
      loginLink.hidden = false;
    }
    if (logoutButton) {
      logoutButton.hidden = true;
    }
    if (withdrawButton) {
      withdrawButton.hidden = true;
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
  if (withdrawButton) {
    withdrawButton.hidden = false;
  }
}

function clearAuthSession({ clearNotebooks = false } = {}) {
  setAccessToken(null);
  localStorage.removeItem(USER_EMAIL_STORAGE_KEY);
  localStorage.removeItem(USER_NAME_STORAGE_KEY);
  if (clearNotebooks) {
    localStorage.removeItem(NOTEBOOK_STORAGE_KEY);
  }
  if (withdrawButton) {
    withdrawButton.hidden = true;
  }
  renderWelcomeMessage();
}

function clearWorkspaceAfterSignOut() {
  resetWorkspaceState("");
  currentNotebookId = createNotebookId();
  renderNotebookOptions();
  renderQuizJobs([]);
  setNotebookStatus("로그아웃되었습니다.");
  setJobStatus("로그아웃되어 Notebook과 문서 목록을 비웠습니다.");
}

function getAccessToken() {
  return accessToken;
}

function decodeJwtPayload(token) {
  try {
    const base64Url = token.split(".")[1];
    const base64 = base64Url.replace(/-/g, "+").replace(/_/g, "/");
    const json = decodeURIComponent(
      atob(base64)
        .split("")
        .map((char) => `%${(`00${char.charCodeAt(0).toString(16)}`).slice(-2)}`)
        .join("")
    );
    return JSON.parse(json);
  } catch (error) {
    return null;
  }
}

function scheduleTokenRefresh() {
  window.clearTimeout(refreshTimerId);

  const token = getAccessToken();
  const payload = token ? decodeJwtPayload(token) : null;

  if (!payload?.exp) {
    return;
  }

  const refreshAtMs = (payload.exp * 1000) - 60000;
  const delayMs = Math.max(refreshAtMs - Date.now(), 0);

  refreshTimerId = window.setTimeout(() => {
    refreshAccessToken().catch((error) => {
      console.warn("Scheduled token refresh failed.", error);
      clearAuthSession();
    });
  }, delayMs);
}

function setAccessToken(token) {
  accessToken = token || null;
  window.clearTimeout(refreshTimerId);

  if (accessToken) {
    scheduleTokenRefresh();
  }
}

function buildAuthHeaders(headers = {}, token = getAccessToken()) {
  const nextHeaders = new Headers(headers);

  if (token) {
    nextHeaders.set(AUTH_HEADER_NAME, `Bearer ${token}`);
  }

  return nextHeaders;
}

async function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
        method: "POST",
        credentials: "include"
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

      setAccessToken(data.accessToken);
      return data.accessToken;
    })().finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
}

async function authFetch(path, options = {}) {
  const requestUrl = path.startsWith("http") ? path : `${API_BASE_URL}${path}`;
  const requestOptions = {
    ...options,
    credentials: options.credentials || "include",
    headers: buildAuthHeaders(options.headers)
  };

  let response = await fetch(requestUrl, requestOptions);

  if (response.status !== 401) {
    return response;
  }

  const refreshedAccessToken = await refreshAccessToken();

  response = await fetch(requestUrl, {
    ...options,
    credentials: options.credentials || "include",
    headers: buildAuthHeaders(options.headers, refreshedAccessToken)
  });

  if (response.status === 401) {
    clearAuthSession();
  }

  return response;
}

async function syncCurrentUser() {
  if (!getAccessToken()) {
    renderWelcomeMessage();
    return null;
  }

  try {
    const response = await authFetch("/api/users/me");
    const user = await readResponseJson(response, "사용자 정보를 불러오지 못했습니다.");

    if (user?.email) {
      localStorage.setItem(USER_EMAIL_STORAGE_KEY, user.email);
    }

    const displayName = user?.name || user?.userName || user?.username;
    if (displayName) {
      localStorage.setItem(USER_NAME_STORAGE_KEY, displayName);
    }

    renderWelcomeMessage();
    return user;
  } catch (error) {
    console.warn("Current user sync failed.", error);
    renderWelcomeMessage();
    return null;
  }
}

async function bootstrapAuthSession() {
  try {
    await refreshAccessToken();
    await syncCurrentUser();
    return true;
  } catch (error) {
    console.warn("Session restore failed.", error);
    clearAuthSession();
    return false;
  }
}

async function logoutUser() {
  try {
    await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
      headers: buildAuthHeaders()
    });
  } catch (error) {
    console.warn("Logout request failed.", error);
  } finally {
    clearAuthSession({ clearNotebooks: true });
    clearWorkspaceAfterSignOut();
  }
}

async function withdrawUser() {
  if (!getAccessToken()) {
    setJobStatus("로그인 후 회원 탈퇴를 진행할 수 있습니다.");
    return;
  }

  if (hasDocumentAnalysisInProgress()) {
    setJobStatus("문서 분석 중에는 회원 탈퇴를 진행할 수 없습니다. 분석이 끝난 뒤 다시 시도하세요.");
    return;
  }

  if (!window.confirm("회원 탈퇴를 진행할까요? 이 작업은 되돌릴 수 없습니다.")) {
    return;
  }

  try {
    const response = await authFetch("/api/users/me", {
      method: "DELETE"
    });

    if (!response.ok) {
      let data = null;
      try {
        data = await response.json();
      } catch (error) {
        data = null;
      }
      throw new Error(data?.message || data?.error || "회원 탈퇴에 실패했습니다.");
    }

    clearAuthSession({ clearNotebooks: true });
    clearWorkspaceAfterSignOut();
    setJobStatus("회원 탈퇴가 완료되었습니다.");
    window.location.href = "./login.html";
  } catch (error) {
    setJobStatus(error.message || "회원 탈퇴에 실패했습니다.");
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

function getStatusProgress(statusData, fallbackProgress = null) {
  const progress = statusData?.analysisProgress ?? statusData?.progressPercent ?? statusData?.progress;
  const numericProgress = Number(progress);

  if (Number.isFinite(numericProgress)) {
    return Math.min(Math.max(Math.round(numericProgress), 0), 100);
  }

  return fallbackProgress;
}

function mergeServerDocumentsWithPendingLocalDocuments(serverDocuments) {
  const normalizedServerDocuments = serverDocuments.map((document) => normalizeServerDocument(document));
  const serverDocumentIds = new Set(
    normalizedServerDocuments
      .map((document) => document.documentId)
      .filter(Boolean)
  );
  const locallyTrackedDocuments = uploadedDocuments.filter((document) => (
    document?.documentId
    && !serverDocumentIds.has(document.documentId)
    && (
      isDocumentAnalysisInProgress(document)
      || document.analysisStatus === "FAILED"
    )
  ));

  return [...normalizedServerDocuments, ...locallyTrackedDocuments];
}

function applyUploadedDocuments(documents) {
  const mergedDocuments = mergeServerDocumentsWithPendingLocalDocuments(documents);

  uploadedDocuments = mergedDocuments.map((document) => {
    const normalizedDocument = document;
    const cachedDocument = uploadedDocuments.find((item) => item.documentId === normalizedDocument.documentId);

    return {
      ...normalizedDocument,
      analysisProgress: normalizedDocument.analysisStatus === "COMPLETED"
        ? 100
        : getStatusProgress(normalizedDocument, cachedDocument?.analysisProgress ?? (isDocumentAnalysisInProgress(normalizedDocument) ? 1 : 0))
    };
  });
  syncUploadedDocumentNames();
  renderUploadedFiles();

  if (uploadedDocumentNames.length === 0) {
    updateEmptyUploadState();
    return;
  }

  if (fileName) {
    fileName.textContent = formatUploadedFileLabel();
  }

  const completedCount = uploadedDocuments
    .filter((document) => document.analysisStatus === "COMPLETED")
    .length;
  const analyzingCount = uploadedDocuments
    .filter(isDocumentAnalysisInProgress)
    .length;
  setUploadState(
    analyzingCount > 0 ? "loading" : "done",
    analyzingCount > 0
      ? `${analyzingCount}/${uploadedDocuments.length}개 문서 분석 중`
      : completedCount > 0
      ? `${completedCount}/${uploadedDocuments.length}개 문서 분석 완료`
      : `${uploadedDocuments.length}개 문서 상태 확인 필요`
  );

  setJobStatus(
    analyzingCount > 0
      ? "분석 중인 문서가 있어 상태 확인을 다시 시작했습니다."
      : completedCount > 0
      ? "서버에 저장된 문서 목록을 불러왔습니다."
      : "서버에 저장된 문서 상태를 확인하세요.",
    { loading: analyzingCount > 0 }
  );

  if (generateButton) {
    generateButton.disabled = uploadedDocumentNames.length === 0;
  }
}

function resumeDocumentAnalysisPolling(notebookId) {
  if (!isServerNotebookId(notebookId)) {
    return;
  }

  uploadedDocuments
    .filter((document) => document.documentId && isDocumentAnalysisInProgress(document))
    .forEach((document) => {
      pollDocumentAnalysisStatus(
        notebookId,
        document.documentId,
        document.originalFileName || "문서"
      )
        .then((statusData) => {
          if (statusData?.analysisStatus === "COMPLETED") {
            syncUploadedDocumentNames();
            renderUploadedFiles();
            upsertSavedNotebook(collectNotebookState());
            setJobStatus(`${document.originalFileName || "문서"} 분석이 완료되었습니다.`);
          }
        })
        .catch((error) => {
          if (error.name === "AnalysisCancelledError") {
            return;
          }
          setJobStatus(error.message || `${document.originalFileName || "문서"} 분석 상태를 확인하지 못했습니다.`);
        });
    });
}

async function fetchNotebookDocuments(notebookId) {
  if (!isServerNotebookId(notebookId)) {
    return [];
  }

  const response = await authFetch(`/api/notebooks/${notebookId}/documents`);
  return readResponseJson(response, "Notebook 문서 목록을 불러오지 못했습니다.");
}

async function fetchDocumentDetail(notebookId, documentId) {
  const response = await authFetch(`/api/notebooks/${notebookId}/documents/${documentId}`);
  return readResponseJson(response, "문서 상세 정보를 불러오지 못했습니다.");
}

async function fetchDocumentSections(notebookId, documentId) {
  const response = await authFetch(`/api/notebooks/${notebookId}/documents/${documentId}/sections`);
  return readResponseJson(response, "문서 Section 목록을 불러오지 못했습니다.");
}

async function syncNotebookDocuments(notebookId) {
  try {
    const documents = await fetchNotebookDocuments(notebookId);
    applyUploadedDocuments(documents);
    upsertSavedNotebook(collectNotebookState());
    resumeDocumentAnalysisPolling(notebookId);
  } catch (error) {
    console.warn("Notebook document sync failed.", error);
    setNotebookStatus(error.message || "Notebook 문서 목록 동기화에 실패했습니다.");
  }
}

async function fetchNotebookDetail(notebookId) {
  if (!isServerNotebookId(notebookId)) {
    return null;
  }

  const response = await authFetch(`/api/notebooks/${notebookId}`);
  return readResponseJson(response, "Notebook 상세 정보를 불러오지 못했습니다.");
}

async function syncNotebookDetail(notebookId) {
  try {
    const serverNotebook = await fetchNotebookDetail(notebookId);

    if (!serverNotebook) {
      return null;
    }

    const cachedNotebook = getSavedNotebooks().find((item) => item.id === String(serverNotebook.notebookId));
    const syncedNotebook = normalizeServerNotebook(serverNotebook, cachedNotebook);
    upsertSavedNotebook(syncedNotebook);

    if (String(currentNotebookId) === syncedNotebook.id && notebookTitle) {
      notebookTitle.textContent = syncedNotebook.title;
    }

    renderNotebookOptions();
    return syncedNotebook;
  } catch (error) {
    console.warn("Notebook detail sync failed.", error);
    setNotebookStatus(error.message || "Notebook 상세 정보 동기화에 실패했습니다.");
    return null;
  }
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
    return "분석 중";
  }

  if (isDocumentAnalysisInProgress(document)) {
    return "분석 중";
  }

  return "";
}

function getDocumentStatusClass(document) {
  if (!document?.analysisStatus) {
    return "";
  }

  return `is-${document.analysisStatus.toLowerCase()}`;
}

function isDocumentAnalysisInProgress(document) {
  const status = String(document?.analysisStatus || "").toUpperCase();
  return status.includes("SUMMAR") || [
    "UPLOADED",
    "ANALYZING",
    "PROCESSING",
    "PENDING",
    "RUNNING",
    "SUMMARY_PENDING",
    "SUMMARY_PROCESSING"
  ].includes(status);
}

function hasDocumentAnalysisInProgress() {
  return uploadedDocuments.some(isDocumentAnalysisInProgress);
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

function restoreNotebookStateFromCache(notebook) {
  if (!notebook) {
    return;
  }

  currentNotebookId = notebook.id;
  uploadedDocuments = [...(notebook.uploadedDocuments ?? [])];
  uploadedDocumentNames = uploadedDocuments.length > 0
    ? uploadedDocuments.map((document) => document.originalFileName).filter(Boolean)
    : [...(notebook.uploadedDocumentNames ?? [])];
  promptHistory = [...(notebook.promptHistory ?? [])];

  if (notebookTitle) {
    notebookTitle.textContent = notebook.title || "";
  }
  if (promptInput) {
    promptInput.value = notebook.prompt ?? "";
  }
  if (questionType) {
    questionType.value = notebook.quizSettings?.questionType ?? "객관식";
  }
  if (questionCount) {
    questionCount.value = String(normalizeQuestionCount(notebook.quizSettings?.questionCount ?? "5"));
  }

  syncSettingButtons();
  renderUploadedFiles();
  renderHistory();

  if (uploadedDocumentNames.length > 0) {
    fileName.textContent = formatUploadedFileLabel();
    setUploadState(
      hasDocumentAnalysisInProgress() ? "loading" : "done",
      hasDocumentAnalysisInProgress()
        ? `${uploadedDocumentNames.length}개 문서 분석 중`
        : `${uploadedDocumentNames.length}개 문서 분석 완료`
    );
    generateButton.disabled = false;
  }

  if (outputWindow) {
    outputWindow.textContent = notebook.output || defaultOutput;
  }
  if (resultCards) {
    resultCards.innerHTML = getStoredResultCardsMarkup(notebook.resultCardsHtml);
  }
}

async function syncNotebookList() {
  if (!getAccessToken()) {
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

    let shouldRestoreNotebook = false;
    if (!notebooks.some((notebook) => notebook.id === currentNotebookId) && notebooks.length > 0) {
      currentNotebookId = notebooks[0].id;
      shouldRestoreNotebook = true;
    }

    if (shouldRestoreNotebook) {
      restoreNotebookStateFromCache(notebooks.find((notebook) => notebook.id === currentNotebookId));
    }

    renderNotebookOptions();
    if (isServerNotebookId(currentNotebookId)) {
      await syncNotebookDetail(currentNotebookId);
      await syncNotebookDocuments(currentNotebookId);
      refreshQuizJobs();
    }
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

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

async function readResponseJson(response, fallbackMessage) {
  let data = null;

  try {
    data = await response.json();
  } catch (error) {
    data = null;
  }

  if (!response.ok) {
    throw new Error(data?.message || data?.error || fallbackMessage);
  }

  return data;
}

async function fetchNotebookQuizJobs(notebookId) {
  if (!isServerNotebookId(notebookId)) {
    return [];
  }

  const response = await authFetch(`/api/notebooks/${notebookId}/quiz-jobs`);
  return readResponseJson(response, "퀴즈 생성 작업 목록을 불러오지 못했습니다.");
}

async function fetchQuizDetail(quizId) {
  const response = await authFetch(`/api/quizzes/${quizId}`);
  return readResponseJson(response, "퀴즈 상세 정보를 불러오지 못했습니다.");
}
async function fetchQuizExplanation(quizId) {
  const response = await authFetch(`/api/quizzes/${quizId}/explanation`);
  return readResponseJson(response, "퀴즈 해설을 불러오지 못했습니다.");
}

async function fetchQuizSources(quizId) {
  const response = await authFetch(`/api/quizzes/${quizId}/source`);
  return readResponseJson(response, "퀴즈 출처를 불러오지 못했습니다.");
}
async function fetchQuizQaHistories(quizId) {
  const response = await authFetch(`/api/quizzes/${quizId}/qa`);
  return readResponseJson(response, "퀴즈 Q&A 기록을 불러오지 못했습니다.");
}

async function askQuizQuestion(quizId, question) {
  const response = await authFetch(`/api/quizzes/${quizId}/qa`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ question })
  });
  return readResponseJson(response, "퀴즈 질문에 실패했습니다.");
}
function coerceQuizText(value) {
  if (value === undefined || value === null) {
    return "";
  }

  if (typeof value !== "object") {
    return String(value).trim();
  }

  const objectCandidates = [
    value.text,
    value.content,
    value.label,
    value.value,
    value.optionText,
    value.choiceText,
    value.answer,
    value.correctAnswer,
    value.index,
    value.number,
    value.no
  ];
  const matchedValue = objectCandidates.find((candidate) => candidate !== undefined && candidate !== null && String(candidate).trim() !== "");

  return matchedValue === undefined ? "" : String(matchedValue).trim();
}

function normalizeQuizChoices(choices) {
  if (Array.isArray(choices)) {
    return choices.map(coerceQuizText).filter(Boolean);
  }

  if (typeof choices !== "string") {
    return [];
  }

  const trimmedChoices = choices.trim();

  if (!trimmedChoices) {
    return [];
  }

  try {
    const parsedChoices = JSON.parse(trimmedChoices);
    if (Array.isArray(parsedChoices)) {
      return parsedChoices.map(coerceQuizText).filter(Boolean);
    }
  } catch (error) {
    // Some responses return choices as plain text instead of a JSON array.
  }

  return trimmedChoices
    .split(/\r?\n|(?<!^)\s*\d+[.)]\s+/)
    .map((choice) => choice.replace(/^[-*]\s*/, "").trim())
    .filter(Boolean);
}

function getQuizAnswer(quiz = {}) {
  const answerCandidates = [
    quiz.answer,
    quiz.correctAnswer,
    quiz.correct_answer,
    quiz.correctOption,
    quiz.correct_option,
    quiz.correctChoice,
    quiz.correct_choice,
    quiz.correctIndex,
    quiz.correct_index,
    quiz.correctAnswerIndex,
    quiz.correct_answer_index,
    quiz.correctChoiceIndex,
    quiz.correct_choice_index,
    quiz.correctOptionIndex,
    quiz.correct_option_index,
    quiz.answerIndex,
    quiz.answer_index,
    quiz.answerNumber,
    quiz.answer_number,
    quiz.correctNumber,
    quiz.correct_number,
    quiz.solution
  ];

  const directAnswer = answerCandidates.map(coerceQuizText).find(Boolean);
  if (directAnswer) {
    return directAnswer;
  }

  const fallbackEntry = Object.entries(quiz)
    .find(([key, value]) => /correct|answer/i.test(key) && coerceQuizText(value));

  return fallbackEntry ? coerceQuizText(fallbackEntry[1]) : "";
}

function renderQuizChoices(choices, answer = "") {
  const normalizedChoices = normalizeQuizChoices(choices);

  if (!normalizedChoices.length) {
    return "";
  }

  return `
    <ol class="quiz-choice-list">
      ${normalizedChoices.map((choice, index) => `
        <li>
          <button type="button" class="quiz-choice-button" data-quiz-choice data-choice-index="${index + 1}" data-choice-text="${escapeHtml(choice)}" data-answer="${escapeHtml(answer)}">
            <span class="quiz-choice-number">${index + 1}</span>
            <span class="quiz-choice-text">${escapeHtml(choice)}</span>
          </button>
        </li>
      `).join("")}
    </ol>
  `;
}

function normalizeAnswerText(value) {
  return String(value ?? "")
    .trim()
    .toLowerCase()
    .replace(/^(answer|\uC815\uB2F5)\s*[:\uFF1A]?\s*/i, "")
    .replace(/\s+/g, " ")
    .trim();
}

function stripChoicePrefix(value) {
  return normalizeAnswerText(value)
    .replace(/^[\u2460\u2461\u2462\u2463\u2464\u2465\u2466\u2467\u2468]\s*/, "")
    .replace(/^\(?([0-9]+)\)?\s*(\uBC88|[.)])?\s*/, "")
    .replace(/^[a-i]\s*[.)]?\s*/i, "")
    .trim();
}

function getAnswerIndex(answer, choiceCount = 0) {
  const normalizedAnswer = normalizeAnswerText(answer);
  const circledIndex = "\u2460\u2461\u2462\u2463\u2464\u2465\u2466\u2467\u2468".indexOf(normalizedAnswer[0]);

  if (circledIndex >= 0) {
    return String(circledIndex + 1);
  }

  const letterMatch = normalizedAnswer.match(/^(?:answer\s*)?([a-i])(?:[.)]|\s|$)/i);
  if (letterMatch) {
    return String("abcdefghi".indexOf(letterMatch[1].toLowerCase()) + 1);
  }

  const koreanLabelMatch = normalizedAnswer.match(/^([가나다라마바사아자])(?:[.)]|\s|$)/);
  if (koreanLabelMatch) {
    return String("가나다라마바사아자".indexOf(koreanLabelMatch[1]) + 1);
  }

  const strictNumberedMatch = normalizedAnswer.match(/(?:^|[^0-9])([1-9])\s*(?:\uBC88|[.)]|$)/);
  if (strictNumberedMatch) {
    return strictNumberedMatch[1];
  }

  const exactNumericMatch = normalizedAnswer.match(/^([0-9]+)$/);
  if (exactNumericMatch) {
    const numericAnswer = Number(exactNumericMatch[1]);
    if (numericAnswer === 0 && choiceCount > 0) {
      return "1";
    }
    return String(numericAnswer);
  }

  const looseNumberMatch = normalizedAnswer.match(/(?:^|[^0-9])([1-9])(?=[^0-9]|$)/);
  if (looseNumberMatch) {
    const number = Number(looseNumberMatch[1]);
    if (!choiceCount || (number >= 1 && number <= choiceCount)) {
      return String(number);
    }
  }

  return "";
}

function normalizeComparableText(value) {
  return stripChoicePrefix(value)
    .replace(/[\s\u00A0]+/g, "")
    .replace(/["'`.,:;!?()[\]{}<>\-_/\\|·•]/g, "")
    .trim();
}

function getChoiceItems(card) {
  return [...(card?.querySelectorAll(".quiz-choice-list li") || [])];
}

function getChoiceText(item) {
  return item?.querySelector(".quiz-choice-text")?.textContent.trim()
    || item?.textContent.replace(/^\s*\d+\.?\s*/, "").trim()
    || "";
}

function getAnswerFromResultCard(card) {
  const answerDetail = [...(card?.querySelectorAll("details") || [])]
    .find((detail) => detail.querySelector("summary")?.textContent.includes("\uC815\uB2F5"));

  return answerDetail?.querySelector("p")?.textContent.trim() || "";
}

function isUsableQuizAnswer(answer) {
  const text = String(answer ?? "").trim();
  return Boolean(
    text
    && text !== "정답 정보가 없습니다."
    && text !== "정답 정보 없음"
    && text !== "정답 정보 확인 필요"
  );
}

function setQuizAnswerForCard(card, answer) {
  if (!card || !isUsableQuizAnswer(answer)) {
    return;
  }

  card.dataset.quizAnswer = answer;
  card.querySelectorAll("[data-quiz-choice]").forEach((button) => {
    button.dataset.answer = answer;
  });

  const answerText = card.querySelector("[data-quiz-answer-text]");
  if (answerText) {
    answerText.textContent = answer;
  }
}

async function ensureQuizAnswerForCard(card) {
  const existingAnswer = card?.dataset.quizAnswer || getAnswerFromResultCard(card);
  if (isUsableQuizAnswer(existingAnswer)) {
    return existingAnswer;
  }

  const quizId = card?.dataset.quizId;
  if (!quizId) {
    return "";
  }

  const quizDetail = await fetchQuizDetail(quizId);
  const answer = getQuizAnswer(quizDetail);
  if (isUsableQuizAnswer(answer)) {
    setQuizAnswerForCard(card, answer);
  }
  return answer;
}

function getChoiceData(choiceElement) {
  const listItem = choiceElement.closest("li") || choiceElement;
  const choices = [...(listItem.parentElement?.children || [])];
  const choiceIndex = choiceElement.dataset.choiceIndex || String(choices.indexOf(listItem) + 1);
  const choiceText = choiceElement.dataset.choiceText || getChoiceText(listItem);
  const card = choiceElement.closest(".result-card");
  const rawAnswer = choiceElement.dataset.answer || card?.dataset.quizAnswer || getAnswerFromResultCard(card);
  const answer = isUsableQuizAnswer(rawAnswer) ? rawAnswer : "";

  return { choiceIndex, choiceText, answer };
}

function getCorrectChoiceIndex(card, answer) {
  const choiceItems = getChoiceItems(card);
  const answerIndex = getAnswerIndex(answer, choiceItems.length);

  if (answerIndex && choiceItems[Number(answerIndex) - 1]) {
    return answerIndex;
  }

  const answerText = stripChoicePrefix(answer);
  if (!answerText) {
    return "";
  }

  const comparableAnswerText = normalizeComparableText(answerText);
  const matchedIndex = choiceItems.findIndex((item) => {
    const choiceText = stripChoicePrefix(getChoiceText(item));
    const comparableChoiceText = normalizeComparableText(choiceText);
    return choiceText && (
      answerText === choiceText
      || (answerText.length >= 2 && choiceText.includes(answerText))
      || (choiceText.length >= 2 && answerText.includes(choiceText))
      || (comparableAnswerText.length >= 2 && comparableAnswerText === comparableChoiceText)
      || (comparableAnswerText.length >= 2 && comparableChoiceText.includes(comparableAnswerText))
      || (comparableChoiceText.length >= 2 && comparableAnswerText.includes(comparableChoiceText))
    );
  });

  if (matchedIndex >= 0) {
    return String(matchedIndex + 1);
  }

  if (comparableAnswerText.length === 1) {
    const prefixMatchedIndexes = choiceItems
      .map((item, index) => ({
        index,
        text: normalizeComparableText(getChoiceText(item))
      }))
      .filter((item) => item.text.startsWith(comparableAnswerText));

    if (prefixMatchedIndexes.length === 1) {
      return String(prefixMatchedIndexes[0].index + 1);
    }
  }

  return "";
}

function isSelectedChoiceCorrect(choiceText, choiceIndex, answer, card = null) {
  const normalizedAnswer = normalizeAnswerText(answer);

  if (!normalizedAnswer) {
    return false;
  }

  const correctChoiceIndex = getCorrectChoiceIndex(card, answer);
  if (correctChoiceIndex) {
    return correctChoiceIndex === String(choiceIndex);
  }

  const answerText = stripChoicePrefix(answer);
  const selectedText = stripChoicePrefix(choiceText);

  return Boolean(answerText && selectedText && answerText === selectedText);
}

function getCorrectChoiceLabel(card, answer) {
  const correctChoiceIndex = getCorrectChoiceIndex(card, answer);

  if (correctChoiceIndex) {
    return `${correctChoiceIndex}번`;
  }

  return "정답 정보 확인 필요";
}

async function gradeQuizChoice(choiceElement) {
  const card = choiceElement.closest(".result-card");
  if (card?.dataset.answered === "true") {
    return;
  }

  const feedback = card?.querySelector("[data-choice-feedback]") || document.createElement("p");
  feedback.dataset.choiceFeedback = "";
  feedback.className = "quiz-choice-feedback";

  if (!feedback.parentElement) {
    choiceElement.closest(".quiz-choice-list")?.after(feedback);
  }

  let { choiceIndex, choiceText, answer } = getChoiceData(choiceElement);

  if (!isUsableQuizAnswer(answer)) {
    feedback.textContent = "정답 확인 중입니다.";
    try {
      answer = await ensureQuizAnswerForCard(card);
    } catch (error) {
      feedback.className = "quiz-choice-feedback is-incorrect";
      feedback.textContent = "정답 정보를 불러오지 못했습니다.";
      return;
    }
  }

  if (!isUsableQuizAnswer(answer)) {
    feedback.className = "quiz-choice-feedback is-incorrect";
    feedback.textContent = "정답 정보가 없습니다.";
    return;
  }

  const correctChoiceIndex = getCorrectChoiceIndex(card, answer);
  if (!correctChoiceIndex) {
    feedback.className = "quiz-choice-feedback";
    feedback.textContent = `정답 정보가 불완전합니다. 서버 정답값: ${answer}`;
    return;
  }

  const isCorrect = correctChoiceIndex === String(choiceIndex);

  card?.querySelectorAll("[data-quiz-choice], .quiz-choice-list li").forEach((item) => {
    item.classList.remove("is-selected", "is-correct", "is-incorrect");
    item.setAttribute("aria-pressed", "false");
  });

  if (card) {
    card.dataset.answered = "true";
    card.querySelectorAll("[data-quiz-choice]").forEach((button) => {
      button.disabled = true;
    });
  }

  const correctChoiceButton = correctChoiceIndex
    ? card?.querySelector(`[data-choice-index="${correctChoiceIndex}"]`)
    : null;
  correctChoiceButton?.classList.add("is-correct");
  correctChoiceButton?.closest("li")?.classList.add("is-correct");

  choiceElement.classList.add("is-selected", isCorrect ? "is-correct" : "is-incorrect");
  choiceElement.closest("li")?.classList.add("is-selected", isCorrect ? "is-correct" : "is-incorrect");
  choiceElement.setAttribute("aria-pressed", "true");

  feedback.className = `quiz-choice-feedback ${isCorrect ? "is-correct" : "is-incorrect"}`;
  const correctChoiceLabel = getCorrectChoiceLabel(card, answer);
  feedback.textContent = isCorrect
    ? `정답입니다. 정답: ${correctChoiceLabel}`
    : `오답입니다. 정답: ${correctChoiceLabel}`;
}
function formatQuizJob(job) {
  const createdAt = job.createdAt ? new Date(job.createdAt).toLocaleString() : "시간 없음";
  return [
    `#${job.jobId} ${job.status || "UNKNOWN"}`,
    `${job.quizType || "유형 없음"} · ${job.quizCount || 0}문항`,
    `결과 ${job.resultCount || 0}개 · ${createdAt}`
  ].join("\n");
}

function renderQuizJobs(jobs = []) {
  if (!quizJobsList) {
    return;
  }

  if (!jobs.length) {
    quizJobsList.innerHTML = '<p class="history-empty">아직 퀴즈 생성 기록이 없습니다.</p>';
    return;
  }

  quizJobsList.innerHTML = jobs
    .map((job) => `
      <div class="quiz-job-item">
        <button type="button" class="quiz-job-load" data-job-id="${escapeHtml(job.jobId)}">
          <span>${escapeHtml(formatQuizJob(job))}</span>
        </button>
        <button type="button" class="quiz-job-delete" data-job-delete="${escapeHtml(job.jobId)}" aria-label="퀴즈 생성 기록 삭제" title="삭제">x</button>
      </div>
    `)
    .join("");
}

function renderQuizJobsMessage(message) {
  if (!quizJobsList) {
    return;
  }

  quizJobsList.innerHTML = `<p class="history-empty">${escapeHtml(message)}</p>`;
}

async function refreshQuizJobs() {
  if (!isServerNotebookId(currentNotebookId)) {
    renderQuizJobsMessage("서버에 저장된 Notebook을 선택하면 퀴즈 생성 기록을 볼 수 있습니다.");
    return;
  }

  try {
    const jobs = await fetchNotebookQuizJobs(currentNotebookId);
    renderQuizJobs(jobs);
  } catch (error) {
    renderQuizJobsMessage(error.message || "퀴즈 생성 기록을 불러오지 못했습니다.");
  }
}

async function loadQuizJobResult(jobId) {
  try {
    const [jobResponse, quizzes] = await Promise.all([
      authFetch(`/api/quiz-jobs/${jobId}`).then((response) => readResponseJson(response, "퀴즈 작업 정보를 불러오지 못했습니다.")),
      fetchGeneratedQuizzes(jobId)
    ]);
    const type = jobResponse.quizType || questionType.value;
    const count = quizzes.length || jobResponse.quizCount || 0;

    renderResultCards(type, count, quizzes);
    setJobStatus(`퀴즈 Job #${jobId} 결과를 불러왔습니다. Q&A 페이지에서 생성된 문제와 해설을 확인하세요.`);
  } catch (error) {
    setJobStatus(error.message || "퀴즈 작업 결과를 불러오지 못했습니다.");
  }
}

async function deleteQuizJob(jobId) {
  if (!jobId || !window.confirm(`퀴즈 생성 기록 #${jobId}을 삭제할까요?`)) {
    return;
  }

  try {
    const response = await authFetch(`/api/quiz-jobs/${jobId}`, {
      method: "DELETE"
    });

    if (!response.ok) {
      let data = null;
      try {
        data = await response.json();
      } catch (error) {
        data = null;
      }
      throw new Error(data?.message || data?.error || "퀴즈 생성 기록 삭제에 실패했습니다.");
    }

    setJobStatus(`퀴즈 생성 기록 #${jobId}을 삭제했습니다.`);
    refreshQuizJobs();
  } catch (error) {
    setJobStatus(error.message || "퀴즈 생성 기록 삭제에 실패했습니다.");
  }
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
    <article class="result-card result-card-empty">
      <p>&#49373;&#49457;&#46108; &#53300;&#51592;&#44032; &#50630;&#49845;&#45768;&#45796;.</p>
    </article>
  `;
}

function getStoredResultCardsMarkup(html) {
  if (!html?.trim()) {
    return getDefaultResultCardMarkup();
  }

  const container = document.createElement("div");
  container.innerHTML = html;
  const cards = [...container.querySelectorAll(".result-card")];
  const textContent = container.textContent || "";
  const isLegacyDefaultCard = cards.length === 1
    && !cards[0].dataset.quizId
    && (
      textContent.includes("Preview")
      || textContent.includes("생성된 문제와 요약 결과")
      || textContent.includes("실제 연결 전")
      || textContent.includes("생성된 문제가 표시됩니다")
      || textContent.includes("생성된 퀴즈가 없습니다")
    );

  return isLegacyDefaultCard ? getDefaultResultCardMarkup() : html;
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
        <article class="history-item">
          <button type="button" class="history-load" data-history-index="${index}" aria-label="이전 요청 불러오기">
            <span class="history-time">${entry.time}</span>
            <strong class="history-title">${entry.type} · ${entry.count}문항</strong>
            <span class="history-text">${entry.prompt}</span>
          </button>
          <button type="button" class="history-delete" data-history-delete="${index}" aria-label="요청 기록 삭제" title="삭제">x</button>
        </article>
      `
    )
    .join("");

  historyList.querySelectorAll(".history-load").forEach((button) => {
    button.addEventListener("click", () => {
      const entry = promptHistory[Number(button.dataset.historyIndex)];

      if (!entry) {
        return;
      }

      promptInput.value = entry.prompt;
      questionType.value = entry.type;
      questionCount.value = String(normalizeQuestionCount(entry.count));
      syncSettingButtons();
      setJobStatus("이전 요청을 입력창으로 불러왔습니다. 실행 버튼을 눌러 다시 생성할 수 있습니다.");
    });
  });

  historyList.querySelectorAll(".history-delete").forEach((button) => {
    button.addEventListener("click", () => {
      const historyIndex = Number(button.dataset.historyDelete);
      const deletedEntry = promptHistory[historyIndex];

      if (!deletedEntry) {
        return;
      }

      promptHistory.splice(historyIndex, 1);
      upsertSavedNotebook(collectNotebookState());
      renderHistory();
      setJobStatus("요청 기록을 삭제했습니다.");
    });
  });
}

function addHistoryEntry(prompt, type, count) {
  const now = new Date();
  const time = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });

  promptHistory = [{ prompt, type, count, time }, ...promptHistory].slice(0, 8);
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
        const isDeleteDisabled = isDocumentAnalysisInProgress(document);
        const deleteTitle = isDeleteDisabled ? "분석 중에는 삭제할 수 없습니다." : "삭제";

        return `
        <article class="uploaded-file-item">
          <div class="uploaded-file-main">
            <span class="uploaded-file-index">${index + 1}</span>
            <span class="uploaded-file-name">${name}</span>
            ${statusLabel ? `<span class="uploaded-file-status ${statusClass}">${statusLabel}</span>` : ""}
          </div>
          <div class="uploaded-file-actions">
            <button type="button" class="ghost-button small-button uploaded-file-detail-toggle" data-file-index="${index}">상세</button>
            <button type="button" class="uploaded-file-remove" data-file-index="${index}" aria-label="${name} 삭제" title="${deleteTitle}" ${isDeleteDisabled ? "disabled" : ""}>x</button>
          </div>
          <div class="uploaded-file-detail" data-document-detail-index="${index}" hidden></div>
        </article>
      `;
      }
    )
    .join("");

  uploadedFilesList.querySelectorAll(".uploaded-file-remove").forEach((button) => {
    button.addEventListener("click", () => removeUploadedFile(Number(button.dataset.fileIndex)));
  });

  uploadedFilesList.querySelectorAll(".uploaded-file-detail-toggle").forEach((button) => {
    button.addEventListener("click", () => toggleUploadedFileDetail(Number(button.dataset.fileIndex)));
  });
}

function renderDocumentDetailMarkup(document, sections) {
  const sectionItems = sections.length > 0
    ? sections.slice(0, 5).map((section) => `
      <li>
        <strong>${escapeHtml(section.heading || `Section ${section.sectionOrder ?? section.sectionId}`)}</strong>
        <span>${escapeHtml((section.content || "").slice(0, 160))}${section.content?.length > 160 ? "..." : ""}</span>
      </li>
    `).join("")
    : "<li>표시할 Section이 없습니다.</li>";

  return `
    <dl class="uploaded-file-meta">
      <div><dt>Document ID</dt><dd>${escapeHtml(document.documentId)}</dd></div>
      <div><dt>파일 형식</dt><dd>${escapeHtml(document.fileType || "-")}</dd></div>
      <div><dt>페이지</dt><dd>${escapeHtml(document.pageCount ?? "-")}</dd></div>
      <div><dt>상태</dt><dd>${escapeHtml(document.analysisStatus || "-")}</dd></div>
    </dl>
    <p class="uploaded-file-section-title">Section preview</p>
    <ul class="uploaded-file-sections">${sectionItems}</ul>
  `;
}

async function toggleUploadedFileDetail(index) {
  const document = uploadedDocuments[index];
  const detailPanel = uploadedFilesList?.querySelector(`[data-document-detail-index="${index}"]`);

  if (!document || !detailPanel) {
    return;
  }

  if (!detailPanel.hidden && detailPanel.dataset.loaded === "true") {
    detailPanel.hidden = true;
    return;
  }

  detailPanel.hidden = false;
  detailPanel.textContent = "문서 상세 정보를 불러오는 중입니다.";

  try {
    const [documentDetail, sections] = await Promise.all([
      fetchDocumentDetail(currentNotebookId, document.documentId),
      fetchDocumentSections(currentNotebookId, document.documentId).catch(() => [])
    ]);
    detailPanel.innerHTML = renderDocumentDetailMarkup(documentDetail, sections);
    detailPanel.dataset.loaded = "true";
  } catch (error) {
    detailPanel.textContent = error.message || "문서 상세 정보를 불러오지 못했습니다.";
  }
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
      : getStatusProgress(statusData, uploadedDocuments[documentIndex].analysisProgress),
    summaryText: statusData.summaryText ?? uploadedDocuments[documentIndex].summaryText
  };

  renderUploadedFiles();
  upsertSavedNotebook(collectNotebookState());
}

async function pollDocumentAnalysisStatus(notebookId, documentId, fileLabel) {
  const pollKey = `${notebookId}:${documentId}`;

  if (activeDocumentAnalysisPolls.has(pollKey)) {
    return null;
  }

  activeDocumentAnalysisPolls.add(pollKey);

  try {
    for (let attempt = 0; attempt < DOCUMENT_STATUS_MAX_ATTEMPTS; attempt += 1) {
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
        setUploadState("done", `${fileLabel} 분석 완료`);
        return statusData;
      }

      if (statusData.analysisStatus === "FAILED") {
        const documentIndex = uploadedDocuments.findIndex((document) => document.documentId === documentId);

        if (documentIndex >= 0) {
          uploadedDocuments[documentIndex] = {
            ...uploadedDocuments[documentIndex],
            analysisStatus: "FAILED",
            analysisProgress: null,
            summaryText: statusData.summaryText ?? uploadedDocuments[documentIndex].summaryText
          };
        }

        syncUploadedDocumentNames();
        renderUploadedFiles();
        upsertSavedNotebook(collectNotebookState());
        throw new Error(`${fileLabel} 문서 분석에 실패했습니다.`);
      }

      const fallbackProgress = Math.min(
        99,
        Math.max(
          uploadedDocuments.find((document) => document.documentId === documentId)?.analysisProgress ?? 1,
          Math.round(((attempt + 1) / DOCUMENT_STATUS_MAX_ATTEMPTS) * 100)
        )
      );
      const progressPercent = getStatusProgress(statusData, fallbackProgress);
      const documentIndex = uploadedDocuments.findIndex((document) => document.documentId === documentId);
      if (documentIndex >= 0) {
        uploadedDocuments[documentIndex] = {
          ...uploadedDocuments[documentIndex],
          analysisStatus: statusData.analysisStatus || "ANALYZING",
          analysisProgress: progressPercent
        };
        renderUploadedFiles();
        upsertSavedNotebook(collectNotebookState());
      }
      setUploadState("loading", `${fileLabel} 분석 중`);
      await wait(DOCUMENT_STATUS_POLL_INTERVAL_MS);
    }

    throw new Error(`${fileLabel} 분석 시간이 초과되었습니다.`);
  } finally {
    activeDocumentAnalysisPolls.delete(pollKey);
  }
}

function updateEmptyUploadState() {
  uploadedDocumentNames = [];
  uploadedDocuments = [];
  if (fileName) {
    fileName.textContent = "PDF, PPT, PPTX 여러 개 업로드";
  }
  setUploadState("idle", "대기 중");
  setJobStatus("문서를 업로드하면 실행 버튼이 활성화됩니다.");
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

  if (isDocumentAnalysisInProgress(document)) {
    setJobStatus(`${removedFileName} 문서는 아직 분석 중이라 삭제할 수 없습니다. 분석이 끝난 뒤 다시 시도하세요.`);
    return;
  }

  if (document?.documentId && isServerNotebookId(currentNotebookId)) {
    try {
      const response = await authFetch(`/api/notebooks/${currentNotebookId}/documents/${document.documentId}`, {
        method: "DELETE"
      });

      if (!response.ok) {
        let data = null;
        try {
          data = await response.json();
        } catch (error) {
          data = null;
        }
        throw new Error(data?.message || data?.error || `${removedFileName} 서버 삭제에 실패했습니다.`);
      }
    } catch (error) {
      setJobStatus(error.message || `${removedFileName} 문서를 삭제하지 못했습니다.`);
      return;
    }
  }

  if (document?.documentId) {
    cancelledDocumentAnalysisIds.add(document.documentId);
  }

  uploadedDocuments.splice(index, 1);
  syncUploadedDocumentNames();
  renderUploadedFiles();
  upsertSavedNotebook(collectNotebookState());

  if (uploadedDocumentNames.length > 0) {
    fileName.textContent = formatUploadedFileLabel();
    setUploadState("done", `${uploadedDocumentNames.length}개 문서`);
    generateButton.disabled = false;
  } else {
    updateEmptyUploadState();
  }

  setJobStatus(`${removedFileName} 문서를 삭제했습니다.`);
}

function renderResultCards(type, count, quizzes = []) {
  if (!resultCards) {
    return;
  }

  resultCards.innerHTML = "";

  if (quizzes.length > 0) {
    quizzes.forEach((quiz, index) => {
      const card = document.createElement("article");
      const quizAnswer = getQuizAnswer(quiz);
      const choicesMarkup = renderQuizChoices(quiz.choices, quizAnswer);
      const sourceSectionIds = quiz.sourceSectionIds?.length
        ? quiz.sourceSectionIds.join(", ")
        : "제공된 참고 Section 없음";

      card.className = "result-card";
      card.dataset.quizId = quiz.quizId || "";
      if (quizAnswer) {
        card.dataset.quizAnswer = quizAnswer;
      }
      card.innerHTML = `
        <p class="result-label">${escapeHtml(quiz.quizType || type)}</p>
        <h3>${index + 1}. ${escapeHtml(quiz.questionText || "문제 내용이 없습니다.")}</h3>
        ${choicesMarkup}
        <div class="result-actions">
          <details>
            <summary class="ghost-button small-button">정답 보기</summary>
            <p data-quiz-answer-text>${escapeHtml(quizAnswer || "정답 정보가 없습니다.")}</p>
          </details>
          <details>
            <summary class="ghost-button small-button">해설 보기</summary>
            <p>${escapeHtml(quiz.explanation || "해설 정보가 없습니다.")}</p>
          </details>
          <details>
            <summary class="ghost-button small-button">참고 Section</summary>
            <p>${escapeHtml(sourceSectionIds)}</p>
          </details>
          ${quiz.quizId ? `<button type="button" class="ghost-button small-button" data-quiz-detail="${escapeHtml(quiz.quizId)}">상세/Q&A</button>` : ""}
        </div>
        ${quiz.quizId ? `
          <div class="quiz-detail-panel" data-quiz-detail-panel="${escapeHtml(quiz.quizId)}" hidden>
            <div class="quiz-detail-content">상세 정보를 불러오지 않았습니다.</div>
            <form class="quiz-qa-form" data-quiz-qa-form="${escapeHtml(quiz.quizId)}">
              <input type="text" name="question" placeholder="이 퀴즈에 대해 질문하세요">
              <button type="submit" class="primary-button small-button">질문</button>
            </form>
            <div class="quiz-qa-history">Q&A 기록이 표시됩니다.</div>
          </div>
        ` : ""}
      `;
      resultCards.appendChild(card);
    });
    return;
  }

  for (let index = 1; index <= count; index += 1) {
    const card = document.createElement("article");
    card.className = "result-card";
    card.innerHTML = `
      <p class="result-label">${type}</p>
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

function formatQuizDetail(quiz, histories = [], explanation = null, sources = []) {
  const historyText = histories.length
    ? histories.map((history, index) => `${index + 1}. Q. ${history.question}\nA. ${history.answer || "답변 없음"}`).join("\n\n")
    : "아직 Q&A 기록이 없습니다.";
  const explanationData = explanation || {};
  const sourceText = sources.length
    ? sources
        .map((source, index) => `${index + 1}. ${source.sourceDocumentName || "문서 이름 없음"} / Section ${source.sectionId || "없음"}`)
        .join("\n")
    : `Section ID: ${(quiz.sourceSectionIds || []).join(", ") || "없음"}`;

  return [
    `[Quiz #${quiz.quizId}]`,
    quiz.questionText || explanationData.questionText || "문제 내용 없음",
    "",
    `정답: ${explanationData.answer || getQuizAnswer(quiz) || "정답 정보 없음"}`,
    `해설: ${explanationData.explanation || quiz.explanation || "해설 정보 없음"}`,
    "",
    "[출처]",
    sourceText,
    "",
    "[Q&A 기록]",
    historyText
  ].join("\n");
}

async function toggleQuizDetail(quizId) {
  const panel = [...(resultCards?.querySelectorAll("[data-quiz-detail-panel]") || [])]
    .find((item) => item.dataset.quizDetailPanel === String(quizId));
  if (!panel) {
    return;
  }

  panel.hidden = !panel.hidden;
  if (panel.hidden || panel.dataset.loaded === "true") {
    return;
  }

  const content = panel.querySelector(".quiz-detail-content");
  if (content) {
    content.textContent = "상세 정보를 불러오는 중입니다.";
  }

  try {
    const [quiz, histories, explanation, sources] = await Promise.all([
      fetchQuizDetail(quizId),
      fetchQuizQaHistories(quizId).catch(() => []),
      fetchQuizExplanation(quizId).catch(() => null),
      fetchQuizSources(quizId).catch(() => [])
    ]);
    if (content) {
      content.textContent = formatQuizDetail(quiz, histories, explanation, sources);
    }
    panel.dataset.loaded = "true";
  } catch (error) {
    if (content) {
      content.textContent = error.message || "퀴즈 상세 정보를 불러오지 못했습니다.";
    }
  }
}

async function submitQuizQuestion(form) {
  const quizId = form.dataset.quizQaForm;
  const input = form.elements.question;
  const question = input?.value.trim();
  const panel = form.closest(".quiz-detail-panel");
  const content = panel?.querySelector(".quiz-detail-content");

  if (!quizId || !question) {
    return;
  }

  form.querySelector("button")?.setAttribute("disabled", "disabled");
  try {
    const answer = await askQuizQuestion(quizId, question);
    input.value = "";
    if (content) {
      content.textContent = [
        `Q. ${question}`,
        "",
        `A. ${answer.answer || "답변 없음"}`,
        `답변 가능 여부: ${answer.answerable ? "가능" : "제한"}`
      ].join("\n");
    }
    panel.dataset.loaded = "";
  } catch (error) {
    if (content) {
      content.textContent = error.message || "퀴즈 질문에 실패했습니다.";
    }
  } finally {
    form.querySelector("button")?.removeAttribute("disabled");
  }
}

async function pollQuizJob(jobId) {
  for (let attempt = 0; attempt < QUIZ_JOB_STATUS_MAX_ATTEMPTS; attempt += 1) {
    const response = await authFetch(`/api/quiz-jobs/${jobId}`);
    const job = await readResponseJson(response, "퀴즈 생성 상태를 확인하지 못했습니다.");

    if (job.status === "COMPLETED" || job.status === "PARTIAL_COMPLETED") {
      return job;
    }

    if (job.status === "FAILED") {
      throw new Error("퀴즈 생성에 실패했습니다.");
    }

    setJobStatus(`${job.status || "RUNNING"}: 퀴즈 생성 중입니다.`, { loading: true });
    await wait(QUIZ_JOB_STATUS_POLL_INTERVAL_MS);
  }

  throw new Error("퀴즈 생성 시간이 초과되었습니다.");
}

async function fetchGeneratedQuizzes(jobId) {
  const response = await authFetch(`/api/quiz-jobs/${jobId}/quizzes`);
  return readResponseJson(response, "생성된 퀴즈 결과를 불러오지 못했습니다.");
}

async function runGeneration() {
  const prompt = promptInput.value.trim();
  const hasUploadedFile = uploadedDocumentNames.length > 0;

  if (!hasUploadedFile) {
    setJobStatus("실행 실패: 업로드된 문서가 없습니다.");
    return;
  }

  if (!prompt) {
    setJobStatus("실행 대기 중: 요청 문구가 비어 있습니다.");
    return;
  }

  const type = questionType.value;
  const count = Math.min(normalizeQuestionCount(questionCount.value), 20);
  questionCount.value = String(count);
  syncSettingButtons();
  const selectedName = uploadedDocumentNames.join(", ");

  setJobStatus("RUNNING: 업로드된 문서를 바탕으로 결과를 생성 중입니다.", { loading: true });
  generateButton.disabled = true;

  try {
    if (!getAccessToken()) {
      throw new Error("로그인 후 퀴즈를 생성할 수 있습니다.");
    }

    if (!isServerNotebookId(currentNotebookId)) {
      const savedNotebook = await saveCurrentNotebook();

      if (!savedNotebook || !isServerNotebookId(currentNotebookId)) {
        throw new Error("퀴즈를 생성하려면 Notebook을 먼저 서버에 저장해야 합니다.");
      }
    }

    const createResponse = await authFetch(`/api/notebooks/${currentNotebookId}/quiz-jobs`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        scopeText: prompt,
        quizType: type,
        difficulty: DEFAULT_QUIZ_DIFFICULTY,
        quizCount: count
      })
    });
    const createdJob = await readResponseJson(createResponse, "퀴즈 생성 요청에 실패했습니다.");
    const completedJob = createdJob.status === "COMPLETED" || createdJob.status === "PARTIAL_COMPLETED"
      ? createdJob
      : await pollQuizJob(createdJob.jobId);
    const quizzes = completedJob.quizzes?.length
      ? completedJob.quizzes
      : await fetchGeneratedQuizzes(completedJob.jobId);
    const resultCount = quizzes.length || completedJob.resultCount || 0;

    setJobStatus(`${completedJob.status}: ${resultCount}개의 ${type} 결과가 준비되었습니다. Q&A 페이지에서 확인하세요.`);
    if (outputWindow) {
      outputWindow.textContent = [
        "[ 생성 결과 요약 ]",
        "",
        `입력 요청: ${prompt}`,
        `문제 유형: ${type}`,
        `문제 수: ${count}`,
        `생성된 문제 수: ${resultCount}`,
        `생성 Job ID: ${completedJob.jobId}`,
        "",
        `검색 대상 문서: ${selectedName}`,
        `상태: ${completedJob.status}`
      ].join("\n");
    }
    renderResultCards(type, count, quizzes);
    addHistoryEntry(prompt, type, count);
    upsertSavedNotebook(collectNotebookState());
    refreshQuizJobs();
  } catch (error) {
    setJobStatus(error.message || "퀴즈 생성에 실패했습니다.");
    if (outputWindow) {
      outputWindow.textContent = error.message || "퀴즈 생성 중 문제가 발생했습니다.";
    }
    if (resultCards) {
      resultCards.innerHTML = getDefaultResultCardMarkup();
    }
  } finally {
    generateButton.disabled = uploadedDocumentNames.length === 0;
  }
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
  questionCount.value = "5";
  syncSettingButtons();
  if (fileInput) {
    fileInput.value = "";
  }
  updateEmptyUploadState();
  renderUploadedFiles();
  renderHistory();
  if (outputWindow) {
    outputWindow.textContent = defaultOutput;
  }
  if (resultCards) {
    resultCards.innerHTML = getDefaultResultCardMarkup();
  }
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

async function loadNotebook(notebookId) {
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

  if (outputWindow) {
    outputWindow.textContent = notebook.output || defaultOutput;
  }
  if (resultCards) {
    resultCards.innerHTML = getStoredResultCardsMarkup(notebook.resultCardsHtml);
  }
  refreshQuizJobs();
  setNotebookStatus(`"${notebook.title}" Notebook을 불러왔습니다.`);

  if (isServerNotebookId(currentNotebookId)) {
    await syncNotebookDetail(currentNotebookId);
    await syncNotebookDocuments(currentNotebookId);
  }
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

function getQuizSettingsPrompt() {
  const typeLabel = questionType?.selectedOptions?.[0]?.textContent.trim() || questionType?.value || "퀴즈";
  const count = normalizeQuestionCount(questionCount?.value || "5");

  return `업로드한 문서를 바탕으로 ${typeLabel} 퀴즈 ${count}문항을 생성해줘. 각 문항마다 정답과 해설을 함께 보여줘.`;
}

function applyQuizSettingsToPrompt() {
  if (!promptInput) {
    return;
  }

  promptInput.value = getQuizSettingsPrompt();
  promptInput.dispatchEvent(new Event("input", { bubbles: true }));
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
quizSettingsConfirm?.addEventListener("click", () => {
  applyQuizSettingsToPrompt();
  closeQuizSettings();
});
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
    setJobStatus("PDF, PPT, PPTX 파일만 업로드할 수 있습니다.");
    return;
  }

  if (!isServerNotebookId(currentNotebookId)) {
    const savedNotebook = await saveCurrentNotebook();

    if (!savedNotebook || !isServerNotebookId(currentNotebookId)) {
      setJobStatus("문서를 업로드하려면 Notebook을 먼저 저장해야 합니다.");
      setUploadState("idle", "Notebook 저장 필요");
      fileInput.value = "";
      return;
    }
  }

  setUploadState("loading", `${selectedFiles.length}개 문서 업로드 중`);
  setJobStatus("문서를 서버에 업로드하는 중입니다.", { loading: true });
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
        credentials: "include",
        body: formData
      });

      if (!response.ok) {
        throw new Error(`${file.name} 업로드에 실패했습니다.`);
      }

      const document = normalizeServerDocument(await response.json());
      document.analysisProgress = document.analysisStatus === "COMPLETED"
        ? 100
        : (isDocumentAnalysisInProgress(document) ? 1 : 0);
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
      setJobStatus(`${document.originalFileName || file.name} 문서 분석 완료를 기다리는 중입니다.`, { loading: true });
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
    setJobStatus("문서 분석이 완료되었습니다. 이제 실행 버튼을 사용할 수 있습니다.");
    generateButton.disabled = uploadedDocumentNames.length === 0;
  } catch (error) {
    syncUploadedDocumentNames();
    renderUploadedFiles();
    setUploadState(uploadedDocumentNames.length > 0 ? "done" : "idle", error.message || "문서 업로드에 실패했습니다.");
    setJobStatus(error.message || "문서 업로드에 실패했습니다.");
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
  setJobStatus("예시 질문을 입력했습니다. 실행 버튼을 눌러 퀴즈를 생성해보세요.");
});

generateButton?.addEventListener("click", runGeneration);
quizJobsRefreshButton?.addEventListener("click", refreshQuizJobs);
quizJobsList?.addEventListener("click", (event) => {
  const deleteButton = event.target.closest("[data-job-delete]");
  if (deleteButton) {
    event.stopPropagation();
    deleteQuizJob(deleteButton.dataset.jobDelete);
    return;
  }

  const item = event.target.closest("[data-job-id]");
  if (!item) {
    return;
  }

  loadQuizJobResult(item.dataset.jobId);
});

resultCards?.addEventListener("click", (event) => {
  const choiceButton = event.target.closest("[data-quiz-choice], .quiz-choice-list li");
  if (choiceButton) {
    void gradeQuizChoice(choiceButton);
    return;
  }

  const detailButton = event.target.closest("[data-quiz-detail]");
  if (!detailButton) {
    return;
  }

  toggleQuizDetail(detailButton.dataset.quizDetail);
});

resultCards?.addEventListener("submit", (event) => {
  const form = event.target.closest("[data-quiz-qa-form]");
  if (!form) {
    return;
  }

  event.preventDefault();
  submitQuizQuestion(form);
});

copyButton?.addEventListener("click", async () => {
  try {
    await navigator.clipboard.writeText(outputWindow?.textContent || "");
    setJobStatus("현재 출력 내용을 클립보드에 복사했습니다.");
  } catch (error) {
    setJobStatus("브라우저 권한 문제로 복사에 실패했습니다.");
  }
});

resetButton?.addEventListener("click", () => resetWorkspaceState(getNotebookDisplayTitle()));
notebookNewButton?.addEventListener("click", () => {
  if (hasDocumentAnalysisInProgress()) {
    setNotebookStatus("문서 분석 중에는 새 Notebook을 만들 수 없습니다.");
    setJobStatus("분석이 완료된 뒤 새 Notebook을 시작하세요.");
    return;
  }

  resetWorkspaceState("");
  setNotebookStatus("새 Notebook을 시작했습니다.");
});
notebookSaveButton?.addEventListener("click", saveCurrentNotebook);
notebookLoadButton?.addEventListener("click", () => loadNotebook(notebookSelect?.value));
notebookDeleteButton?.addEventListener("click", deleteSelectedNotebook);
heroEditTitleButton?.addEventListener("click", focusNotebookTitle);
logoutButton?.addEventListener("click", logoutUser);
withdrawButton?.addEventListener("click", withdrawUser);
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
bootstrapAuthSession().finally(async () => {
  await syncNotebookList();
});
setLeftMenuOpen(false);













