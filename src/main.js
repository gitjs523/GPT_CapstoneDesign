const fileInput = document.querySelector("#document-upload");
const fileName = document.querySelector("#selected-file-name");
const promptInput = document.querySelector("#prompt-input");
const generateButton = document.querySelector("#generate-button");
const outputWindow = document.querySelector("#output-window");
const resetButton = document.querySelector("#reset-button");

const defaultOutput =
  "아직 생성된 결과가 없습니다. 문서를 업로드하고 요청을 입력한 뒤 결과 미리보기를 눌러주세요.";

if (fileInput && fileName) {
  fileInput.addEventListener("change", (event) => {
    const selectedFile = event.target.files?.[0];
    fileName.textContent = selectedFile
      ? `${selectedFile.name} 선택됨`
      : "아직 업로드된 문서가 없습니다.";
  });
}

if (generateButton && promptInput && outputWindow) {
  generateButton.addEventListener("click", () => {
    const prompt = promptInput.value.trim();

    if (!prompt) {
      outputWindow.textContent =
        "입력된 요청이 없습니다. 문제 수나 문제 유형 같은 조건을 먼저 입력해주세요.";
      return;
    }

    outputWindow.textContent = [
      "[ 생성 결과 예시 영역 ]",
      "",
      `입력 요청: ${prompt}`,
      "",
      "이 위치에 문서 분석 결과와 생성된 문제가 표시됩니다.",
      "임베딩 모델, 검색 결과, 생성형 모델 응답을 연결하면 실제 서비스로 확장할 수 있습니다."
    ].join("\n");
  });
}

if (resetButton && promptInput && outputWindow && fileInput && fileName) {
  resetButton.addEventListener("click", () => {
    promptInput.value = "";
    outputWindow.textContent = defaultOutput;
    fileInput.value = "";
    fileName.textContent = "아직 업로드된 문서가 없습니다.";
  });
}
