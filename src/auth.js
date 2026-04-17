const authTabs = document.querySelectorAll(".auth-tab");
const authPanels = document.querySelectorAll(".auth-panel");
const forgotPasswordTrigger = document.querySelector("#forgot-password-trigger");
const loginForgotPassword = document.querySelector("#login-forgot-password");
const forgotForm = document.querySelector("#forgot-form");
const forgotEmail = document.querySelector("#forgot-email");
const forgotPassword = document.querySelector("#forgot-password");
const forgotPasswordConfirm = document.querySelector("#forgot-password-confirm");
const forgotFeedback = document.querySelector("#forgot-feedback");
const forgotSubmit = document.querySelector("#forgot-submit");
const forgotBack = document.querySelector("#forgot-back");
const loginForm = document.querySelector("#login-form");
const loginEmail = document.querySelector("#login-email");
const loginPassword = document.querySelector("#login-password");
const loginFeedback = document.querySelector("#login-feedback");
const loginSubmit = document.querySelector("#login-submit");
const signupForm = document.querySelector("#signup-form");
const signupName = document.querySelector("#signup-name");
const signupEmail = document.querySelector("#signup-email");
const signupPassword = document.querySelector("#signup-password");
const signupPasswordConfirm = document.querySelector("#signup-password-confirm");
const signupAgree = document.querySelector("#signup-agree");
const signupFeedback = document.querySelector("#signup-feedback");
const signupResult = document.querySelector("#signup-result");
const signupSubmit = document.querySelector("#signup-submit");

const mockRegisteredEmails = [
  "snow@example.com",
  "student@example.com",
  "team@snow.ai"
];

function setActiveAuthPanel(targetId) {
  authTabs.forEach((tab) => {
    const isActive = tab.dataset.authTarget === targetId;
    tab.classList.toggle("is-active", isActive);
    tab.setAttribute("aria-selected", String(isActive));
  });

  authPanels.forEach((panel) => {
    const isActive = panel.id === targetId;
    panel.classList.toggle("is-active", isActive);
    panel.hidden = !isActive;
  });
}

function showSignupResult(message, state) {
  if (!signupResult) {
    return;
  }

  signupResult.hidden = false;
  signupResult.textContent = message;
  signupResult.classList.remove("is-valid", "is-error", "auth-feedback-neutral");

  if (state === "success") {
    signupResult.classList.add("is-valid");
  } else if (state === "error") {
    signupResult.classList.add("is-error");
  } else {
    signupResult.classList.add("auth-feedback-neutral");
  }
}

function clearSignupResult() {
  if (!signupResult) {
    return;
  }

  signupResult.hidden = true;
  signupResult.textContent = "";
  signupResult.classList.remove("is-valid", "is-error");
  signupResult.classList.add("auth-feedback-neutral");
}

function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function hasSpecialCharacter(value) {
  return /[^A-Za-z0-9]/.test(value);
}

function updateLoginState() {
  if (!loginEmail || !loginPassword || !loginFeedback || !loginSubmit) {
    return;
  }

  const email = loginEmail.value.trim();
  const password = loginPassword.value;
  const hasEmail = email.length > 0;
  const hasPassword = password.length > 0;
  const validEmail = isValidEmail(email);
  const isReady = hasEmail && hasPassword && validEmail;

  if (!hasEmail && !hasPassword) {
    loginFeedback.textContent = "이메일과 비밀번호를 입력하세요.";
  } else if (!hasEmail) {
    loginFeedback.textContent = "이메일을 입력하세요.";
  } else if (!validEmail) {
    loginFeedback.textContent = "올바른 이메일 형식을 입력하세요.";
  } else if (!hasPassword) {
    loginFeedback.textContent = "비밀번호를 입력하세요.";
  } else {
    loginFeedback.textContent = "로그인 준비가 완료되었습니다.";
  }

  loginFeedback.classList.toggle("is-valid", isReady);
  loginFeedback.classList.toggle("is-error", !isReady);
  loginSubmit.disabled = !isReady;
}

function updateForgotState() {
  if (
    !forgotEmail ||
    !forgotPassword ||
    !forgotPasswordConfirm ||
    !forgotFeedback ||
    !forgotSubmit
  ) {
    return;
  }

  const email = forgotEmail.value.trim();
  const validEmail = isValidEmail(email);
  const existsEmail = mockRegisteredEmails.includes(email.toLowerCase());
  const hasLongEnoughPassword = forgotPassword.value.length >= 8;
  const includesSpecialCharacter = hasSpecialCharacter(forgotPassword.value);
  const passwordsMatch =
    forgotPassword.value.length > 0 &&
    forgotPassword.value === forgotPasswordConfirm.value;
  const isReady =
    validEmail &&
    existsEmail &&
    hasLongEnoughPassword &&
    includesSpecialCharacter &&
    passwordsMatch;

  if (!email) {
    forgotFeedback.textContent = "가입한 이메일과 새 비밀번호를 입력하세요.";
  } else if (!validEmail) {
    forgotFeedback.textContent = "올바른 이메일 형식을 입력하세요.";
  } else if (!existsEmail) {
    forgotFeedback.textContent = "존재하지 않는 이메일 주소입니다.";
  } else if (!forgotPassword.value && !forgotPasswordConfirm.value) {
    forgotFeedback.textContent = "새 비밀번호를 입력하세요.";
  } else if (!hasLongEnoughPassword) {
    forgotFeedback.textContent = "비밀번호는 8자 이상이어야 합니다.";
  } else if (!includesSpecialCharacter) {
    forgotFeedback.textContent = "비밀번호에 특수문자를 최소 1개 포함하세요.";
  } else if (!passwordsMatch) {
    forgotFeedback.textContent = "비밀번호가 일치하지 않습니다.";
  } else {
    forgotFeedback.textContent = "비밀번호 재설정 준비가 완료되었습니다.";
  }

  forgotFeedback.classList.toggle("is-valid", isReady);
  forgotFeedback.classList.toggle("is-error", !isReady);
  forgotSubmit.disabled = !isReady;
}

function updateSignupState() {
  if (
    !signupName ||
    !signupEmail ||
    !signupPassword ||
    !signupPasswordConfirm ||
    !signupAgree ||
    !signupFeedback ||
    !signupSubmit
  ) {
    return;
  }

  const hasName = signupName.value.trim().length > 0;
  const hasEmail = signupEmail.value.trim().length > 0;
  const hasLongEnoughPassword = signupPassword.value.length >= 8;
  const includesSpecialCharacter = hasSpecialCharacter(signupPassword.value);
  const passwordsMatch =
    signupPassword.value.length > 0 &&
    signupPassword.value === signupPasswordConfirm.value;
  const agreed = signupAgree.checked;
  const isReady =
    hasName &&
    hasEmail &&
    hasLongEnoughPassword &&
    includesSpecialCharacter &&
    passwordsMatch &&
    agreed;

  if (!signupPassword.value && !signupPasswordConfirm.value) {
    signupFeedback.textContent = "비밀번호는 8자 이상이며 특수문자를 포함해야 합니다.";
  } else if (!hasLongEnoughPassword) {
    signupFeedback.textContent = "비밀번호는 8자 이상이어야 합니다.";
  } else if (!includesSpecialCharacter) {
    signupFeedback.textContent = "비밀번호에 특수문자를 최소 1개 포함하세요.";
  } else if (!passwordsMatch) {
    signupFeedback.textContent = "비밀번호가 일치하지 않습니다.";
  } else if (!agreed) {
    signupFeedback.textContent = "회원가입을 진행하려면 약관 동의가 필요합니다.";
  } else if (!hasName || !hasEmail) {
    signupFeedback.textContent = "이름과 이메일을 모두 입력하세요.";
  } else {
    signupFeedback.textContent = "회원가입 준비가 완료되었습니다.";
  }

  signupFeedback.classList.toggle("is-valid", isReady);
  signupFeedback.classList.toggle("is-error", !isReady);
  signupSubmit.disabled = !isReady;
}

function resetSignupForm() {
  if (!signupForm) {
    return;
  }

  signupForm.reset();
  updateSignupState();
}

authTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    setActiveAuthPanel(tab.dataset.authTarget);
  });
});

[forgotPasswordTrigger, loginForgotPassword].forEach((trigger) => {
  if (!trigger) {
    return;
  }

  trigger.addEventListener("click", () => {
    setActiveAuthPanel("forgot-panel");
    updateForgotState();
  });
});

if (forgotBack) {
  forgotBack.addEventListener("click", () => {
    setActiveAuthPanel("login-panel");
    updateLoginState();
  });
}

[loginEmail, loginPassword].forEach((field) => {
  if (!field) {
    return;
  }

  field.addEventListener("input", updateLoginState);
});

[signupName, signupEmail, signupPassword, signupPasswordConfirm, signupAgree].forEach((field) => {
  if (!field) {
    return;
  }

  const eventName = field.type === "checkbox" ? "change" : "input";
  field.addEventListener(eventName, () => {
    clearSignupResult();
    updateSignupState();
  });
});

[forgotEmail, forgotPassword, forgotPasswordConfirm].forEach((field) => {
  if (!field) {
    return;
  }

  field.addEventListener("input", updateForgotState);
});

if (signupForm) {
  signupForm.addEventListener("submit", (event) => {
    event.preventDefault();

    clearSignupResult();
    updateSignupState();

    if (signupSubmit?.disabled) {
      showSignupResult("입력 조건을 먼저 충족한 뒤 다시 시도하세요.", "error");
      setActiveAuthPanel("signup-panel");
      return;
    }

    // TODO: 백엔드 연결 시 이 지점에서 회원가입 API를 호출하고,
    // 응답 결과에 따라 success / error 분기를 서버 응답 기준으로 교체한다.
    const shouldSucceed = signupEmail?.value.trim().toLowerCase() !== "fail@example.com";

    if (shouldSucceed) {
      const submittedEmail = signupEmail?.value.trim() ?? "";
      showSignupResult("회원가입이 완료되었습니다. 로그인 탭에서 로그인하세요.", "success");
      setActiveAuthPanel("login-panel");
      if (loginEmail) {
        loginEmail.value = submittedEmail;
        updateLoginState();
      }
      resetSignupForm();
      return;
    }

    showSignupResult("회원가입에 실패했습니다. 입력 정보를 확인한 뒤 다시 시도하세요.", "error");
    setActiveAuthPanel("signup-panel");
  });
}

if (loginForm) {
  loginForm.addEventListener("submit", () => {
    // TODO: 백엔드 연결 시 로그인 API 호출 후 성공 시 페이지 이동,
    // 실패 시 로그인 패널 유지 및 서버 에러 메시지 표시 로직을 추가한다.
  });
}

if (forgotForm) {
  forgotForm.addEventListener("submit", (event) => {
    event.preventDefault();
    updateForgotState();

    if (forgotSubmit?.disabled) {
      return;
    }

    // TODO: 백엔드 연결 시 비밀번호 재설정 요청 API를 호출하고,
    // 성공 시 안내 메시지 표시, 실패 시 에러 메시지 표시로 교체한다.
    forgotFeedback.textContent = "비밀번호가 재설정되었습니다. 새 비밀번호로 로그인하세요.";
    forgotFeedback.classList.add("is-valid");
    forgotFeedback.classList.remove("is-error");
  });
}

updateLoginState();
updateSignupState();
updateForgotState();
clearSignupResult();
