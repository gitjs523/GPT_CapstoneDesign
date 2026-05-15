const notebookSelect = document.querySelector("#explanation-notebook-select");
const referencesPanel = document.querySelector("#explanation-references");
const questionPanel = document.querySelector("#explanation-question");
const answerPanel = document.querySelector("#explanation-answer");

const NOTEBOOK_STORAGE_KEY = "snow.notebooks";

const text = {
  noSavedNotebook: "\uC800\uC7A5\uB41C Notebook \uC5C6\uC74C",
  saveNotebookFirst: "Workspace\uC5D0\uC11C Notebook\uC744 \uC800\uC7A5\uD558\uBA74 \uC774\uACF3\uC5D0\uC11C \uC120\uD0DD\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.",
  selectedNotebookPrompt: "\uC120\uD0DD\uD55C Notebook\uC758 \uC694\uCCAD \uB0B4\uC6A9\uC774 \uD45C\uC2DC\uB429\uB2C8\uB2E4.",
  generatedAnswerDraft: "\uC0DD\uC131 \uACB0\uACFC\uC640 \uD574\uC124 \uCD08\uC548\uC774 \uD45C\uC2DC\uB429\uB2C8\uB2E4.",
  noUploadedDocuments: "\uC5C5\uB85C\uB4DC\uB41C \uBB38\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.",
  uploadedDocuments: "\uC5C5\uB85C\uB4DC \uBB38\uC11C",
  noSavedPrompt: "\uC800\uC7A5\uB41C \uC694\uCCAD \uB0B4\uC6A9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.",
  defaultQuestionType: "\uAC1D\uAD00\uC2DD",
  questionType: "\uBB38\uC81C \uC720\uD615",
  questionCount: "\uBB38\uC81C \uC218",
  difficulty: "\uB09C\uC774\uB3C4",
  noGeneratedResult: "\uC544\uC9C1 \uC0DD\uC131\uB41C \uACB0\uACFC\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4. Workspace\uC5D0\uC11C \uBB38\uC11C\uB97C \uC5C5\uB85C\uB4DC\uD558\uACE0 \uD034\uC988\uB97C \uC0DD\uC131\uD558\uC138\uC694."
};

function getSavedNotebooks() {
  try {
    // TODO(back-end): Replace this localStorage mock with GET /api/notebooks.
    return JSON.parse(localStorage.getItem(NOTEBOOK_STORAGE_KEY) ?? "[]");
  } catch (error) {
    return [];
  }
}

function renderEmptyState() {
  if (notebookSelect) {
    notebookSelect.innerHTML = `<option value="">${text.noSavedNotebook}</option>`;
  }
  if (referencesPanel) {
    referencesPanel.textContent = text.saveNotebookFirst;
  }
  if (questionPanel) {
    questionPanel.textContent = text.selectedNotebookPrompt;
  }
  if (answerPanel) {
    answerPanel.textContent = text.generatedAnswerDraft;
  }
}

function formatReferences(notebook) {
  const files = notebook.uploadedDocumentNames ?? [];

  if (files.length === 0) {
    return text.noUploadedDocuments;
  }

  return [`[ ${text.uploadedDocuments} ]`, "", ...files.map((name, index) => `${index + 1}. ${name}`)].join("\n");
}

function formatQuestion(notebook) {
  const title = notebook.title || "Untitled Project";
  const prompt = notebook.prompt?.trim() || text.noSavedPrompt;
  const settings = notebook.quizSettings ?? {};
  const type = settings.questionType || text.defaultQuestionType;
  const count = settings.questionCount || "5";
  const difficulty = settings.difficulty || "medium";

  return [
    `[ ${title} ]`,
    "",
    prompt,
    "",
    `${text.questionType}: ${type}`,
    `${text.questionCount}: ${count}`,
    `${text.difficulty}: ${difficulty}`
  ].join("\n");
}

function formatAnswer(notebook) {
  if (notebook.output?.trim()) {
    return notebook.output.trim();
  }

  return text.noGeneratedResult;
}

function renderNotebook(notebookId) {
  const notebooks = getSavedNotebooks();
  const notebook = notebooks.find((item) => item.id === notebookId) ?? notebooks[0];

  if (!notebook) {
    renderEmptyState();
    return;
  }

  if (notebookSelect) {
    notebookSelect.value = notebook.id;
  }
  referencesPanel.textContent = formatReferences(notebook);
  questionPanel.textContent = formatQuestion(notebook);
  answerPanel.textContent = formatAnswer(notebook);
}

function renderNotebookOptions() {
  const notebooks = getSavedNotebooks();

  if (notebooks.length === 0) {
    renderEmptyState();
    return;
  }

  notebookSelect.innerHTML = notebooks
    .map((notebook) => `<option value="${notebook.id}">${notebook.title || "Untitled Project"}</option>`)
    .join("");
  renderNotebook(notebooks[0].id);
}

notebookSelect?.addEventListener("change", () => {
  renderNotebook(notebookSelect.value);
});

renderNotebookOptions();
