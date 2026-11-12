(() => {
    "use strict";

    const form = document.querySelector("[data-login-form]");
    if (!form) return;

    const username = form.elements.namedItem("username");
    const password = form.elements.namedItem("password");
    const accessNumber = form.elements.namedItem("accessNumber");
    const save = form.elements.namedItem("saveLogin");
    const type = form.elements.namedItem("memberType").value;
    const key = "foodorder.saved-login." + (type === "OWNER" ? "OWNER" : "USER");
    const message = form.querySelector("[data-login-message]");

    const showMessage = (text) => {
        message.textContent = text;
        message.hidden = false;
    };

    // パスワードはlocalStorage・sessionStorage・Cookieに保存しない。
    const saveIdentity = () => {
        try {
            if (save.checked) {
                localStorage.setItem(key, JSON.stringify({
                    username: username.value.trim(),
                    accessNumber: accessNumber ? accessNumber.value.trim() : ""
                }));
            } else {
                localStorage.removeItem(key);
            }
        } catch {
            // ブラウザが保存を拒否してもログインは続行する。
        }
    };

    try {
        const saved = JSON.parse(localStorage.getItem(key));
        if (saved && typeof saved.username === "string"
                && saved.username.length <= (type === "OWNER" ? 50 : 254)) {
            username.value = saved.username;
            if (accessNumber && typeof saved.accessNumber === "string"
                    && /^[0-9]{12}$/.test(saved.accessNumber)) {
                accessNumber.value = saved.accessNumber;
            }
            save.checked = true;

            // 対応ブラウザでは保存済みパスワードを取得し、入力欄にだけ反映する。
            // 取得できない場合も標準のautocompleteを利用できる。
            if (window.isSecureContext && "PasswordCredential" in window && navigator.credentials?.get) {
                navigator.credentials.get({ password: true, mediation: "silent" })
                    .then((credential) => {
                        if (save.checked && credential?.type === "password"
                                && credential.id === saved.username
                                && username.value === saved.username && !password.value) {
                            password.value = credential.password;
                        }
                    }).catch(() => {});
            }
        }
    } catch {
        // 保存データが壊れている場合や保存が無効な環境でも通常ログインを使う。
    }

    save.addEventListener("change", () => {
        if (!save.checked) {
            try { localStorage.removeItem(key); } catch { /* 保存不可 */ }
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (form.dataset.submitting === "true") return;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }

        form.dataset.submitting = "true";
        const submit = form.querySelector('button[type="submit"]');
        submit.dataset.authBusy = "true";
        submit.disabled = true;
        message.hidden = true;

        try {
            const response = await fetch(form.action, {
                method: "POST",
                credentials: "same-origin",
                body: new URLSearchParams(new FormData(form))
            });
            const destination = new URL(response.url);
            const success = new URL(form.dataset.successUrl, window.location.href);
            if (!response.ok || !response.redirected || destination.origin !== success.origin) {
                throw new Error("Login request failed");
            }
            if (destination.pathname !== success.pathname) {
                // 認証失敗時は入力内容を保存せず、既存のエラー画面へ移動する。
                window.location.assign(destination.href);
                return;
            }

            saveIdentity();
            if (save.checked && window.isSecureContext
                    && "PasswordCredential" in window && navigator.credentials?.store) {
                try {
                    await navigator.credentials.store(new window.PasswordCredential({
                        id: username.value.trim(),
                        password: password.value
                    }));
                } catch {
                    // 保存の許可・拒否はブラウザに委ね、ログイン結果は変えない。
                }
            }
            window.location.assign(destination.href);
        } catch {
            showMessage("ログイン処理を完了できませんでした。通信状態を確認して、もう一度お試しください。");
        } finally {
            delete form.dataset.submitting;
            submit.disabled = false;
            delete submit.dataset.authBusy;
        }
    });
})();
