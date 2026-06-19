const notebookSelect = document.querySelector("#explanation-notebook-select");
const referencesPanel = document.querySelector("#explanation-references");
const documentSummaryPanel = document.querySelector("#document-summary");
const questionPanel = document.querySelector("#explanation-question");
const answerPanel = document.querySelector("#explanation-answer");
const notebookQaForm = document.querySelector("#notebook-qa-form");
const notebookQaInput = document.querySelector("#notebook-qa-input");
const notebookQaSubmit = document.querySelector("#notebook-qa-submit");
const notebookQaStatus = document.querySelector("#notebook-qa-status");
const notebookQaStatusText = document.querySelector("#notebook-qa-status-text");
const notebookQaHistory = document.querySelector("#notebook-qa-history");
const loginLink = document.querySelector("#login-link");
const logoutButton = document.querySelector("#logout-button");
const leftMenuToggle = document.querySelector("#left-menu-toggle");
const leftMenuDrawer = document.querySelector("#left-menu-drawer");
const scrollExpandButtons = document.querySelectorAll("[data-scroll-toggle]");

const NOTEBOOK_STORAGE_KEY = "snow.notebooks";
const USER_EMAIL_STORAGE_KEY = "snow.userEmail";
const USER_NAME_STORAGE_KEY = "snow.userName";
const API_BASE_URL = "";
const QA_JOB_POLL_INTERVAL_MS = 1500;
const QA_JOB_MAX_POLL_COUNT = 40;

let currentNotebookId = "";
let currentNotebookDocuments = [];
let accessToken = null;
let refreshPromise = null;
let refreshTimerId = null;
let activeQaPollToken = 0;

localStorage.removeItem("snow.accessToken");

function setNotebookQaLoading(isLoading, message = "답변을 생성하고 있습니다.") {
  if (notebookQaStatus) {
    notebookQaStatus.hidden = !isLoading;
    notebookQaStatus.classList.toggle("is-loading", isLoading);
  }

  if (notebookQaStatusText) {
    notebookQaStatusText.textContent = message;
  }

  if (notebookQaSubmit) {
    notebookQaSubmit.classList.toggle("is-loading", isLoading);
    notebookQaSubmit.setAttribute("aria-busy", String(isLoading));
    notebookQaSubmit.title = isLoading ? "답변 생성 중" : "질문 보내기";
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
function getScrollToggleTargets(button) {
  return (button.dataset.scrollToggle || "")
    .split(/\s+/)
    .map((id) => document.getElementById(id))
    .filter(Boolean);
}

function syncScrollExpandButton(button) {
  const targets = getScrollToggleTargets(button);
  if (!targets.length) {
    button.hidden = true;
    return;
  }

  const isExpanded = targets.some((target) => target.classList.contains("is-expanded"));
  const hasOverflow = targets.some((target) => target.scrollHeight > target.clientHeight + 2);
  button.hidden = !isExpanded && !hasOverflow;
  button.setAttribute("aria-expanded", String(isExpanded));
  button.title = isExpanded ? "접기" : "전체 보기";
}

function syncScrollExpandButtons() {
  window.requestAnimationFrame(() => {
    scrollExpandButtons.forEach(syncScrollExpandButton);
  });
}

function toggleScrollablePanel(button) {
  const targets = getScrollToggleTargets(button);
  if (!targets.length) {
    return;
  }

  const shouldExpand = !targets.some((target) => target.classList.contains("is-expanded"));
  targets.forEach((target) => {
    target.classList.toggle("is-expanded", shouldExpand);
  });
  button.setAttribute("aria-expanded", String(shouldExpand));
  button.title = shouldExpand ? "접기" : "전체 보기";
}
function renderAuthNavigation() {
  const isLoggedIn = Boolean(getAccessToken());

  if (loginLink) {
    loginLink.hidden = isLoggedIn;
  }

  if (logoutButton) {
    logoutButton.hidden = !isLoggedIn;
  }
}

const text = {
  noSavedNotebook: "\uC800\uC7A5\uB41C Notebook \uC5C6\uC74C",
  saveNotebookFirst: "Workspace\uC5D0\uC11C Notebook\uC744 \uC800\uC7A5\uD558\uBA74 \uC774\uACF3\uC5D0\uC11C \uC120\uD0DD\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.",
  selectedNotebookPrompt: "선택한 Notebook의 생성 문제가 표시됩니다.",
  generatedAnswerDraft: "생성된 문제의 정답과 해설이 표시됩니다.",
  noUploadedDocuments: "\uC5C5\uB85C\uB4DC\uB41C \uBB38\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.",
  uploadedDocuments: "\uC5C5\uB85C\uB4DC \uBB38\uC11C",
  noSavedPrompt: "\uC800\uC7A5\uB41C \uC694\uCCAD \uB0B4\uC6A9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.",
  defaultQuestionType: "\uAC1D\uAD00\uC2DD",
  questionType: "\uBB38\uC81C \uC720\uD615",
  questionCount: "\uBB38\uC81C \uC218",
  noGeneratedResult: "아직 생성된 정답 및 해설이 없습니다. Workspace에서 퀴즈를 생성한 뒤 다시 확인하세요."
};

function getSavedNotebooks() {
  try {
    // TODO(back-end): Replace this localStorage mock with GET /api/notebooks.
    return JSON.parse(localStorage.getItem(NOTEBOOK_STORAGE_KEY) ?? "[]");
  } catch (error) {
    return [];
  }
}

function setSavedNotebooks(notebooks) {
  localStorage.setItem(NOTEBOOK_STORAGE_KEY, JSON.stringify(notebooks));
}

function normalizeServerNotebook(notebook, cachedNotebook = {}) {
  const id = String(notebook.notebookId);

  return {
    ...cachedNotebook,
    id,
    notebookId: notebook.notebookId,
    title: notebook.title || cachedNotebook.title || "Untitled Project",
    createdAt: notebook.createdAt || cachedNotebook.createdAt,
    updatedAt: notebook.updatedAt || cachedNotebook.updatedAt
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
    summaryText: document.summaryText,
    analysisErrorMessage: document.analysisErrorMessage,
    uploadedAt: document.uploadedAt
  };
}

function isDocumentAnalysisInProgress(document) {
  const status = String(document?.analysisStatus || "").toUpperCase();
  return status.includes("SUMMAR") || [
    "UPLOADED",
    "ANALYZING",
    "PROCESSING",
    "PENDING",
    "RUNNING",
    "SUMMARIZING",
    "SUMMARY_PENDING",
    "SUMMARY_PROCESSING"
  ].includes(status);
}

function hasDocumentAnalysisInProgress(documents = currentNotebookDocuments) {
  return documents.some(isDocumentAnalysisInProgress);
}

function updateNotebookQaAvailability(documents = currentNotebookDocuments) {
  const isBlocked = hasDocumentAnalysisInProgress(documents);

  if (notebookQaInput) {
    notebookQaInput.disabled = isBlocked;
    notebookQaInput.placeholder = isBlocked
      ? "문서 분석이 완료된 뒤 질문할 수 있습니다"
      : "Notebook 내용에 대해 질문하세요";
  }

  if (notebookQaSubmit) {
    notebookQaSubmit.disabled = isBlocked;
  }

  if (isBlocked && notebookQaHistory) {
    notebookQaHistory.textContent = "문서 분석 중에는 Notebook 질문을 사용할 수 없습니다. 분석 완료 후 다시 질문해주세요.";
  }
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
    nextHeaders.set("Authorization", `Bearer ${token}`);
  }

  return nextHeaders;
}

function clearAuthSession({ clearNotebooks = false } = {}) {
  setAccessToken(null);
  localStorage.removeItem(USER_EMAIL_STORAGE_KEY);
  localStorage.removeItem(USER_NAME_STORAGE_KEY);
  if (clearNotebooks) {
    localStorage.removeItem(NOTEBOOK_STORAGE_KEY);
  }
  renderAuthNavigation();
}

function clearExplanationAfterSignOut() {
  currentNotebookId = "";
  currentNotebookDocuments = [];
  renderEmptyState();
  if (notebookQaHistory) {
    notebookQaHistory.textContent = "로그아웃되어 Q&A 기록을 비웠습니다.";
  }
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
      setAccessToken(data.accessToken);
      renderAuthNavigation();
      return data.accessToken;
    })().finally(() => {
      refreshPromise = null;
    });
  }

  return refreshPromise;
}

async function authFetch(path, options = {}) {
  const requestUrl = path.startsWith("http") ? path : `${API_BASE_URL}${path}`;
  let response = await fetch(requestUrl, {
    ...options,
    credentials: options.credentials || "include",
    headers: buildAuthHeaders(options.headers)
  });

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

async function readResponseText(response, fallbackMessage) {
  const text = await response.text();

  if (!response.ok) {
    try {
      const data = JSON.parse(text);
      throw new Error(data?.message || data?.error || fallbackMessage);
    } catch (error) {
      if (error instanceof Error && error.name !== "SyntaxError") {
        throw error;
      }
      throw new Error(text || fallbackMessage);
    }
  }

  return text;
}

async function fetchServerNotebooks() {
  if (!getAccessToken()) {
    return getSavedNotebooks();
  }

  const response = await authFetch("/api/notebooks");
  const serverNotebooks = await readResponseJson(response, "Notebook 목록을 불러오지 못했습니다.");
  const cachedNotebooks = getSavedNotebooks();
  const notebooks = serverNotebooks.map((serverNotebook) => {
    const cachedNotebook = cachedNotebooks.find((item) => item.id === String(serverNotebook.notebookId));
    return normalizeServerNotebook(serverNotebook, cachedNotebook);
  });

  setSavedNotebooks(notebooks);
  return notebooks;
}

async function fetchNotebookDocuments(notebookId) {
  if (!getAccessToken() || !/^\d+$/.test(String(notebookId))) {
    return [];
  }

  const response = await authFetch(`/api/notebooks/${notebookId}/documents`);
  const documents = await readResponseJson(response, "Notebook 문서 목록을 불러오지 못했습니다.");
  return documents.map(normalizeServerDocument);
}

async function fetchNotebookQaHistories(notebookId) {
  if (!getAccessToken() || !/^\d+$/.test(String(notebookId))) {
    return [];
  }

  const response = await authFetch(`/api/notebooks/${notebookId}/qa`);
  return readResponseJson(response, "Notebook Q&A 기록을 불러오지 못했습니다.");
}

async function fetchNotebookQuizJobs(notebookId) {
  if (!getAccessToken() || !/^\d+$/.test(String(notebookId))) {
    return [];
  }

  const response = await authFetch(`/api/notebooks/${notebookId}/quiz-jobs`);
  return readResponseJson(response, "퀴즈 생성 기록을 불러오지 못했습니다.");
}

async function fetchGeneratedQuizzes(jobId) {
  if (!getAccessToken() || !jobId) {
    return [];
  }

  const response = await authFetch(`/api/quiz-jobs/${jobId}/quizzes`);
  return readResponseJson(response, "생성된 문제를 불러오지 못했습니다.");
}

async function askNotebookQuestion(notebookId, question) {
  const response = await authFetch(`/api/notebooks/${notebookId}/qa`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ question })
  });
  return readResponseJson(response, "Notebook 질문에 실패했습니다.");
}

async function fetchNotebookQaJob(notebookId, qaJobId) {
  if (!getAccessToken() || !/^\d+$/.test(String(notebookId)) || !qaJobId) {
    throw new Error("Q&A 작업 정보를 조회할 수 없습니다.");
  }

  const response = await authFetch(`/api/notebooks/${notebookId}/qa-jobs/${qaJobId}`);
  return readResponseJson(response, "Notebook Q&A 작업 상태를 불러오지 못했습니다.");
}

function isNotebookQaJobPending(job) {
  return ["QUEUED", "RUNNING"].includes(job?.status);
}

function isNotebookQaJobCompleted(job) {
  return job?.status === "COMPLETED" || Boolean(job?.answer);
}

function formatNotebookQaJobAnswer(question, job = {}) {
  return [
    `Q. ${question}`,
    "",
    `A. ${job.answer || "답변 없음"}`,
    `답변 가능 여부: ${job.answerable ? "가능" : "제한"}`
  ].join("\n");
}

function wait(ms) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

async function pollNotebookQaJob(notebookId, qaJobId, question, pollToken) {
  for (let attempt = 1; attempt <= QA_JOB_MAX_POLL_COUNT; attempt += 1) {
    await wait(QA_JOB_POLL_INTERVAL_MS);

    if (pollToken !== activeQaPollToken || notebookId !== currentNotebookId) {
      throw new Error("Q&A 조회가 취소되었습니다.");
    }

    const job = await fetchNotebookQaJob(notebookId, qaJobId);

    if (isNotebookQaJobCompleted(job)) {
      return job;
    }

    if (job.status === "FAILED") {
      throw new Error("Notebook 답변 생성에 실패했습니다.");
    }

    if (notebookQaHistory && isNotebookQaJobPending(job)) {
      setNotebookQaLoading(true, `SNOW가 답변을 생성하고 있습니다. (${job.status})`);
      notebookQaHistory.textContent = [
        `Q. ${question}`,
        "",
        `답변 생성 중입니다. (${job.status})`
      ].join("\n");
    }
  }

  throw new Error("Notebook 답변 생성이 오래 걸리고 있습니다. 잠시 후 다시 확인해주세요.");
}

async function resolveNotebookQaJob(notebookId, question, initialJob) {
  if (!initialJob?.qaJobId) {
    return initialJob;
  }

  if (isNotebookQaJobCompleted(initialJob) || initialJob.status === "FAILED") {
    return initialJob;
  }

  const pollToken = activeQaPollToken;
  return pollNotebookQaJob(notebookId, initialJob.qaJobId, question, pollToken);
}
async function bootstrapAuthSession() {
  try {
    await refreshAccessToken();
    renderAuthNavigation();
    return true;
  } catch (error) {
    console.warn("Explanation session restore failed.", error);
    clearAuthSession();
    return false;
  }
}

async function logoutUser() {
  try {
    await fetch(`${API_BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include"
    });
  } catch (error) {
    console.warn("Explanation logout failed.", error);
  } finally {
    clearAuthSession({ clearNotebooks: true });
    clearExplanationAfterSignOut();
    window.location.href = "./login.html";
  }
}

function renderEmptyState() {
  currentNotebookDocuments = [];
  updateNotebookQaAvailability();

  if (notebookSelect) {
    notebookSelect.innerHTML = `<option value="">${text.noSavedNotebook}</option>`;
  }
  if (referencesPanel) {
    referencesPanel.textContent = text.saveNotebookFirst;
  }
  if (documentSummaryPanel) {
    documentSummaryPanel.textContent = "Notebook을 선택하면 문서 요약이 표시됩니다.";
  }
  if (questionPanel) {
    questionPanel.textContent = text.selectedNotebookPrompt;
  }
  if (answerPanel) {
    answerPanel.textContent = text.generatedAnswerDraft;
  }
  syncScrollExpandButtons();
}

function formatReferences(notebook, documents = []) {
  const files = documents.length > 0
    ? documents.map((document) => `${document.originalFileName} (${document.analysisStatus})`)
    : notebook.uploadedDocumentNames ?? [];

  if (files.length === 0) {
    return text.noUploadedDocuments;
  }

  return [`[ ${text.uploadedDocuments} ]`, "", ...files.map((name, index) => `${index + 1}. ${name}`)].join("\n");
}


function getDocumentSummaryFallback(document) {
  if (document.analysisStatus === "FAILED") {
    return document.analysisErrorMessage || "분석에 실패해 요약을 표시할 수 없습니다.";
  }

  if (isDocumentAnalysisInProgress(document)) {
    return "요약 중입니다. 문서 분석이 끝나면 자동으로 표시됩니다.";
  }

  return "요약 정보가 없습니다.";
}

function formatDocumentSummaries(documents = []) {
  if (!documents.length) {
    return "요약할 문서가 없습니다.";
  }

  return documents
    .map((document, index) => [
      `${index + 1}. ${document.originalFileName || "문서 이름 없음"}`,
      document.summaryText?.trim() || getDocumentSummaryFallback(document)
    ].join("\n"))
    .join("\n\n");
}
function normalizeQuizChoices(choices) {
  if (Array.isArray(choices)) {
    return choices.map((choice) => String(choice).trim()).filter(Boolean);
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
      return parsedChoices.map((choice) => String(choice).trim()).filter(Boolean);
    }
  } catch (error) {
    // Plain text choices are handled below.
  }

  return trimmedChoices
    .split(/\r?\n|(?<!^)\s*\d+[.)]\s+/)
    .map((choice) => choice.replace(/^[-*]\s*/, "").trim())
    .filter(Boolean);
}

function formatGeneratedQuestions(quizzes = []) {
  if (!quizzes.length) {
    return "아직 생성된 문제가 없습니다. Workspace에서 퀴즈를 생성한 뒤 다시 확인하세요.";
  }

  return quizzes
    .map((quiz, index) => {
      const choices = normalizeQuizChoices(quiz.choices);
      return [
        `${index + 1}. ${quiz.questionText || "문제 내용 없음"}`,
        ...choices.map((choice, choiceIndex) => `   ${choiceIndex + 1}) ${choice}`)
      ].join("\n");
    })
    .join("\n\n");
}

function extractGeneratedQuestionsFromSavedHtml(html = "") {
  if (!html.trim()) {
    return [];
  }

  const container = document.createElement("div");
  container.innerHTML = html;

  return [...container.querySelectorAll(".result-card")]
    .map((card) => {
      const heading = card.querySelector("h3")?.textContent.trim() || "";
      const choices = [...card.querySelectorAll(".quiz-choice-text")]
        .map((choice) => choice.textContent.trim())
        .filter(Boolean);
      const details = [...card.querySelectorAll("details")];
      const answer = details.find((detail) => detail.querySelector("summary")?.textContent.includes("정답"))
        ?.querySelector("p")?.textContent.trim() || "";
      const explanation = details.find((detail) => detail.querySelector("summary")?.textContent.includes("해설"))
        ?.querySelector("p")?.textContent.trim() || "";

      return heading
        ? { questionText: heading.replace(/^\d+\.\s*/, ""), choices, answer, explanation }
        : null;
    })
    .filter(Boolean);
}

function getLatestQuizJob(jobs = []) {
  return [...jobs]
    .filter((job) => (job.resultCount ?? 0) > 0 || ["COMPLETED", "PARTIAL_COMPLETED"].includes(job.status))
    .sort((a, b) => new Date(b.finishedAt || b.createdAt || 0) - new Date(a.finishedAt || a.createdAt || 0))[0] || null;
}

function formatGeneratedAnswers(quizzes = []) {
  if (!quizzes.length) {
    return text.noGeneratedResult;
  }

  return quizzes
    .map((quiz, index) => [
      `${index + 1}. ${quiz.questionText || "문제 내용 없음"}`,
      `정답: ${quiz.answer || "정답 정보 없음"}`,
      `해설: ${quiz.explanation || "해설 정보 없음"}`
    ].join("\n"))
    .join("\n\n");
}

function renderGeneratedQuizPanels(quizzes) {
  if (questionPanel) {
    questionPanel.textContent = formatGeneratedQuestions(quizzes);
    syncScrollExpandButtons();
  }

  if (answerPanel) {
    answerPanel.textContent = formatGeneratedAnswers(quizzes);
    syncScrollExpandButtons();
  }
}

async function renderGeneratedQuizContent(notebook) {
  if (questionPanel) {
    questionPanel.textContent = "생성된 문제를 불러오는 중입니다.";
  }
  if (answerPanel) {
    answerPanel.textContent = "정답과 해설을 불러오는 중입니다.";
  }

  try {
    const jobs = await fetchNotebookQuizJobs(notebook.id);
    const latestJob = getLatestQuizJob(jobs);
    const quizzes = latestJob ? await fetchGeneratedQuizzes(latestJob.jobId) : [];
    const fallbackQuizzes = extractGeneratedQuestionsFromSavedHtml(notebook.resultCardsHtml);
    renderGeneratedQuizPanels(quizzes.length ? quizzes : fallbackQuizzes);
  } catch (error) {
    const fallbackQuizzes = extractGeneratedQuestionsFromSavedHtml(notebook.resultCardsHtml);
    if (fallbackQuizzes.length) {
      renderGeneratedQuizPanels(fallbackQuizzes);
      return;
    }

    if (questionPanel) {
      questionPanel.textContent = error.message || "생성된 문제를 불러오지 못했습니다.";
    }
    if (answerPanel) {
      answerPanel.textContent = text.noGeneratedResult;
    }
  }
}

function formatNotebookQaHistory(histories) {
  if (!histories || histories.length === 0) {
    return "아직 Notebook Q&A 기록이 없습니다.";
  }

  return histories
    .map((history, index) => [
      `${index + 1}. Q. ${history.question}`,
      `A. ${history.answer || "답변 없음"}`,
      `답변 가능 여부: ${history.answerable ? "가능" : "제한"}`
    ].join("\n"))
    .join("\n\n");
}

async function renderNotebookQaHistories(notebookId) {
  if (!notebookQaHistory) {
    return;
  }

  if (hasDocumentAnalysisInProgress()) {
    updateNotebookQaAvailability();
    return;
  }

  notebookQaHistory.textContent = "Notebook Q&A 기록을 불러오는 중입니다.";

  try {
    const histories = await fetchNotebookQaHistories(notebookId);
    if (hasDocumentAnalysisInProgress()) {
      updateNotebookQaAvailability();
      return;
    }
    notebookQaHistory.textContent = formatNotebookQaHistory(histories);
  } catch (error) {
    notebookQaHistory.textContent = error.message || "Notebook Q&A 기록을 불러오지 못했습니다.";
  }
}

async function renderNotebook(notebookId) {
  const notebooks = getSavedNotebooks();
  const notebook = notebooks.find((item) => item.id === notebookId) ?? notebooks[0];

  if (!notebook) {
    renderEmptyState();
    return;
  }

  currentNotebookId = notebook.id;
  if (notebookSelect) {
    notebookSelect.value = notebook.id;
  }
  referencesPanel.textContent = "참고 문서를 불러오는 중입니다.";
  if (documentSummaryPanel) {
    documentSummaryPanel.textContent = "문서 요약을 불러오는 중입니다.";
  }
  renderGeneratedQuizContent(notebook);

  try {
    const documents = await fetchNotebookDocuments(notebook.id);
    currentNotebookDocuments = documents;
    referencesPanel.textContent = formatReferences(notebook, documents);
    if (documentSummaryPanel) {
      documentSummaryPanel.textContent = formatDocumentSummaries(documents);
      syncScrollExpandButtons();
    }
    updateNotebookQaAvailability(documents);
    renderNotebookQaHistories(notebook.id);
  } catch (error) {
    currentNotebookDocuments = [];
    updateNotebookQaAvailability();
    referencesPanel.textContent = error.message || formatReferences(notebook);
    if (documentSummaryPanel) {
      documentSummaryPanel.textContent = "문서 요약을 불러오지 못했습니다.";
      syncScrollExpandButtons();
    }
    renderNotebookQaHistories(notebook.id);
  }
}

async function renderNotebookOptions() {
  let notebooks = [];

  try {
    notebooks = await fetchServerNotebooks();
  } catch (error) {
    console.warn("Explanation notebook sync failed.", error);
    notebooks = getSavedNotebooks();
  }

  if (notebooks.length === 0) {
    renderEmptyState();
    return;
  }

  notebookSelect.innerHTML = notebooks
    .map((notebook) => `<option value="${notebook.id}">${notebook.title || "Untitled Project"}</option>`)
    .join("");
  renderNotebook(notebooks[0].id);
}

scrollExpandButtons.forEach((button) => {
  button.addEventListener("click", () => {
    toggleScrollablePanel(button);
  });
});

window.addEventListener("resize", syncScrollExpandButtons);
notebookSelect?.addEventListener("change", () => {
  renderNotebook(notebookSelect.value);
});


notebookQaForm?.addEventListener("submit", async (event) => {
  event.preventDefault();

  const question = notebookQaInput?.value.trim() || "";
  if (!question || !currentNotebookId) {
    return;
  }

  if (hasDocumentAnalysisInProgress()) {
    updateNotebookQaAvailability();
    return;
  }

  notebookQaSubmit.disabled = true;
  setNotebookQaLoading(true, "SNOW에게 질문을 보내는 중입니다.");
  notebookQaHistory.textContent = "질문을 보내는 중입니다.";

  try {
    activeQaPollToken += 1;
    const submittedNotebookId = currentNotebookId;
    const pollToken = activeQaPollToken;
    const initialJob = await askNotebookQuestion(submittedNotebookId, question);
    notebookQaInput.value = "";

    if (initialJob?.qaJobId && isNotebookQaJobPending(initialJob)) {
      setNotebookQaLoading(true, `SNOW가 답변을 생성하고 있습니다. (${initialJob.status})`);
      notebookQaHistory.textContent = [
        `Q. ${question}`,
        "",
        `\uB2F5\uBCC0 \uC0DD\uC131 \uC911\uC785\uB2C8\uB2E4. (${initialJob.status})`
      ].join("\n");
    }

    const resolvedJob = await resolveNotebookQaJob(submittedNotebookId, question, initialJob);

    if (pollToken !== activeQaPollToken || submittedNotebookId !== currentNotebookId) {
      return;
    }

    if (resolvedJob?.status === "FAILED") {
      throw new Error("Notebook \uB2F5\uBCC0 \uC0DD\uC131\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.");
    }

    notebookQaHistory.textContent = formatNotebookQaJobAnswer(question, resolvedJob);
  } catch (error) {
    if (error.message === "Q&A \uC870\uD68C\uAC00 \uCDE8\uC18C\uB418\uC5C8\uC2B5\uB2C8\uB2E4.") {
      return;
    }
    notebookQaHistory.textContent = error.message || "Notebook \uC9C8\uBB38\uC5D0 \uC2E4\uD328\uD588\uC2B5\uB2C8\uB2E4.";
  } finally {
    setNotebookQaLoading(false);
    notebookQaSubmit.disabled = hasDocumentAnalysisInProgress();
  }
});

leftMenuToggle?.addEventListener("click", () => {
  setLeftMenuOpen(leftMenuToggle.getAttribute("aria-expanded") !== "true");
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && leftMenuDrawer?.classList.contains("is-open")) {
    setLeftMenuOpen(false);
    syncScrollExpandButtons();
    leftMenuToggle?.focus();
  }
});

logoutButton?.addEventListener("click", logoutUser);
renderAuthNavigation();
setLeftMenuOpen(false);
syncScrollExpandButtons();

bootstrapAuthSession().finally(() => {
  renderNotebookOptions();
});










