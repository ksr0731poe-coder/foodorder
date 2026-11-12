(() => {
    "use strict";

    const page = document.querySelector(".auth-page");

    if (!page) {
        return;
    }

    // 비밀번호 표시용 고정 SVG
    const eyeIcon = `
        <svg class="auth-eye-icon"
             viewBox="0 0 24 24"
             fill="none"
             stroke="currentColor"
             stroke-width="1.8"
             stroke-linecap="round"
             stroke-linejoin="round"
             aria-hidden="true">
            <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/>
            <circle cx="12" cy="12" r="3"/>
        </svg>
    `;

    const eyeOffIcon = `
        <svg class="auth-eye-icon"
             viewBox="0 0 24 24"
             fill="none"
             stroke="currentColor"
             stroke-width="1.8"
             stroke-linecap="round"
             stroke-linejoin="round"
             aria-hidden="true">
            <path d="m3 3 18 18"/>
            <path d="M10.6 5.1A12 12 0 0 1 12 5c6.5 0 10 7 10 7a18 18 0 0 1-3.1 4.1"/>
            <path d="M6.3 6.3A18 18 0 0 0 2 12s3.5 7 10 7a12 12 0 0 0 5.7-1.5"/>
            <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>
        </svg>
    `;

    // 비밀번호 보기·숨기기
    page.querySelectorAll("[data-toggle-password]")
        .forEach((button) => {
            const input = document.getElementById(
                button.dataset.togglePassword
            );

            if (!input || !page.contains(input)) {
                return;
            }

            const label =
                input.id.endsWith("password-confirm")
                    ? "確認用パスワード"
                    : "パスワード";

            const render = () => {
                const visible = input.type === "text";

                button.innerHTML = visible
                    ? eyeOffIcon
                    : eyeIcon;

                const action = label
                    + (visible ? "を非表示" : "を表示");

                button.setAttribute("aria-label", action);
                button.setAttribute("title", action);
                button.setAttribute(
                    "aria-pressed",
                    String(visible)
                );
            };

            render();

            button.addEventListener("click", () => {
                input.type = input.type === "password"
                    ? "text"
                    : "password";

                render();
            });
        });

    const signupForm = page.querySelector(
        "[data-signup-form]"
    );

    if (signupForm) {
        const password = signupForm.querySelector(
            "#signup-password"
        );

        const confirm = signupForm.querySelector(
            "#signup-password-confirm"
        );

        const fields = signupForm.querySelector(
            "[data-signup-fields]"
        );

        const messageBox = signupForm.querySelector(
            "[data-signup-message]"
        );

        const emailRequired = signupForm.querySelector(
            "[data-email-required]"
        );

        const submitButton = signupForm.querySelector(
            'button[type="submit"]'
        );

        const showMessage = (message) => {
            if (!messageBox) {
                return;
            }

            messageBox.textContent = message;
            messageBox.hidden = false;
            messageBox.focus();
        };

        const validatePassword = () => {
            if (!password || !confirm) {
                return;
            }

            password.setCustomValidity("");
            confirm.setCustomValidity("");

            const value = password.value;

            if (value !== "") {
                if (
                    !/[A-Za-z]/.test(value)
                    || !/[0-9]/.test(value)
                ) {
                    password.setCustomValidity(
                        "パスワードには半角英字と数字を含めてください。"
                    );
                } else if (
                    new TextEncoder().encode(value).length > 72
                ) {
                    password.setCustomValidity(
                        "パスワードはUTF-8で72バイト以内にしてください。"
                    );
                }
            }

            if (
                confirm.value !== ""
                && value !== confirm.value
            ) {
                confirm.setCustomValidity(
                    "確認用パスワードが一致しません。"
                );
            }
        };

        password?.addEventListener(
            "input",
            validatePassword
        );

        confirm?.addEventListener(
            "input",
            validatePassword
        );

        // 회원가입만 비동기 전송
        signupForm.addEventListener("submit", async (event) => {
            event.preventDefault();

            if (signupForm.dataset.submitting === "true") {
                return;
            }

            if (fields?.disabled) {
                showMessage(
                    "先にメール認証を完了してください。"
                );
                return;
            }

            validatePassword();

            if (!signupForm.reportValidity()) {
                return;
            }

            // 버튼을 비활성화하기 전에 입력값·CSRF 토큰 수집
            const formData = new FormData(signupForm);

            signupForm.dataset.submitting = "true";
            signupForm.setAttribute("aria-busy", "true");

            if (submitButton) {
                submitButton.disabled = true;
            }

            if (messageBox) {
                messageBox.hidden = true;
            }

            try {
                const response = await fetch(
                    signupForm.action,
                    {
                        method: "POST",
                        body: formData,
                        credentials: "same-origin",
                        headers: {
                            "Accept": "application/json"
                        }
                    }
                );

                if (response.status === 403) {
                    showMessage(
                        "セッションが無効になった可能性があります。"
                        + "入力内容を確認し、再認証してください。"
                    );
                    return;
                }

                const contentType =
                    response.headers.get("content-type") || "";

                if (!contentType.includes("application/json")) {
                    throw new Error("Unexpected response");
                }

                const result = await response.json();

                if (!response.ok || result.success !== true) {
                    showMessage(
                        result.message
                        || "会員登録に失敗しました。"
                        + "入力内容を確認してください。"
                    );

                    // 認証期限が切れた場合は入力値を消さずに再ロック
                    if (result.emailVerified === false) {
                        if (fields) {
                            fields.disabled = true;
                        }

                        if (emailRequired) {
                            emailRequired.hidden = false;
                        }
                    }

                    return;
                }

                // 가입 성공한 경우에만 완료 화면으로 이동
                window.location.assign(
                    signupForm.dataset.completeUrl
                );

            } catch (error) {
                // ネットワークエラーでもフォームは初期化しない
                showMessage(
                    "通信に失敗しました。"
                    + "入力内容はこの画面に残っています。"
                    + "通信状態を確認してください。"
                    + "再送信前に、登録が完了していないか確認してください。"
                );

            } finally {
                delete signupForm.dataset.submitting;
                signupForm.removeAttribute("aria-busy");

                if (submitButton) {
                    submitButton.disabled = false;
                }
            }
        });
    }

    // 로그인·메일 인증 등 기존 폼은 기존 방식 유지
    // 회원가입 폼에는 위 비동기 처리만 적용
    page.querySelectorAll("[data-auth-submit]")
        .forEach((form) => {
            if (form.matches("[data-signup-form], [data-login-form]")) {
                return;
            }

            form.addEventListener("submit", (event) => {
                if (form.dataset.submitting === "true") {
                    event.preventDefault();
                    return;
                }

                if (!form.checkValidity()) {
                    event.preventDefault();
                    form.reportValidity();
                    return;
                }

                form.dataset.submitting = "true";

                form.querySelectorAll('button[type="submit"]')
                    .forEach((button) => {
                        button.dataset.authBusy = "true";
                        button.disabled = true;
                    });
            });
        });

    // 브라우저 뒤로 가기로 복원된 버튼 상태 정리
    window.addEventListener("pageshow", () => {
        page.querySelectorAll("form").forEach((form) => {
            delete form.dataset.submitting;
            form.removeAttribute("aria-busy");
        });

        page.querySelectorAll(
            'button[data-auth-busy="true"]'
        ).forEach((button) => {
            button.disabled = false;
            delete button.dataset.authBusy;
        });
    });
})();