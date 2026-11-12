const { test } = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const script = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/js/login-autofill.js"), "utf8");

function page({ type = "USER", data = {}, credential = null, destination = "http://localhost/", rejectStore = false, blockedStorage = false } = {}) {
    const storage = new Map(Object.entries(data));
    const events = {};
    const fields = {
        username: { value: "" }, password: { value: "" }, memberType: { value: type },
        saveLogin: { checked: false, addEventListener: (event, fn) => { events.change = fn; } }
    };
    if (type === "OWNER") fields.accessNumber = { value: "" };
    const button = { disabled: false, dataset: {} };
    const message = { hidden: true };
    const form = {
        action: "http://localhost/login", dataset: { successUrl: "/" },
        elements: { namedItem: (name) => fields[name] ?? null },
        querySelector: (selector) => selector === "[data-login-message]" ? message : button,
        checkValidity: () => true,
        addEventListener: (event, fn) => { events.submit = fn; }
    };
    const savedPasswords = [];
    const navigations = [];
    const window = {
        isSecureContext: true,
        PasswordCredential: class { constructor(value) { Object.assign(this, value); } },
        location: { href: "http://localhost/login", assign: (url) => navigations.push(url) }
    };
    vm.runInNewContext(script, {
        document: { querySelector: () => form }, window,
        navigator: { credentials: {
            get: async () => credential,
            store: async (value) => {
                if (rejectStore) throw new Error("User declined");
                savedPasswords.push(value);
            }
        } },
        localStorage: {
            getItem: (key) => { if (blockedStorage) throw new Error("Blocked"); return storage.get(key) ?? null; },
            setItem: (key, value) => { if (blockedStorage) throw new Error("Blocked"); storage.set(key, value); },
            removeItem: (key) => storage.delete(key)
        },
        fetch: async () => ({ ok: true, redirected: true, url: destination }),
        FormData: class { *[Symbol.iterator]() { yield ["username", fields.username.value]; } },
        URLSearchParams, URL
    });
    return { fields, storage, savedPasswords, navigations, events, button, message,
        submit: () => events.submit({ preventDefault() {} }) };
}

test("restores owner identity and number independently from user account", () => {
    const p = page({ type: "OWNER", data: {
        "foodorder.saved-login.USER": JSON.stringify({ username: "user" }),
        "foodorder.saved-login.OWNER": JSON.stringify({ username: "owner", accessNumber: "123456789012" })
    } });
    assert.equal(p.fields.username.value, "owner");
    assert.equal(p.fields.accessNumber.value, "123456789012");
    assert.equal(p.fields.saveLogin.checked, true);
    assert.equal(p.navigations.length, 0);
});

test("password fills from browser manager only when identity matches", async () => {
    const p = page({ data: { "foodorder.saved-login.USER": JSON.stringify({ username: "user" }) },
        credential: { type: "password", id: "user", password: "BrowserSecret123" } });
    await Promise.resolve();
    assert.equal(p.fields.password.value, "BrowserSecret123");
    assert.equal(p.navigations.length, 0);
    const other = page({ data: { "foodorder.saved-login.USER": JSON.stringify({ username: "user" }) },
        credential: { type: "password", id: "other", password: "OtherSecret123" } });
    await Promise.resolve();
    assert.equal(other.fields.password.value, "");
});

test("successful owner login stores identity and delegates password to browser", async () => {
    const p = page({ type: "OWNER" });
    p.fields.username.value = "owner";
    p.fields.accessNumber.value = "123456789012";
    p.fields.password.value = "Secret123";
    p.fields.saveLogin.checked = true;
    await p.submit();
    const saved = JSON.parse(p.storage.get("foodorder.saved-login.OWNER"));
    assert.deepEqual(saved, { username: "owner", accessNumber: "123456789012" });
    assert.equal(p.savedPasswords[0].password, "Secret123");
    assert.equal([...p.storage.values()].some(value => value.includes("Secret123")), false);
    assert.equal(p.navigations[0], "http://localhost/");
});

test("failed authentication never saves entered credentials", async () => {
    const p = page({ destination: "http://localhost/login?type=USER&error" });
    p.fields.username.value = "user";
    p.fields.password.value = "Wrong123";
    p.fields.saveLogin.checked = true;
    await p.submit();
    assert.equal(p.storage.size, 0);
    assert.equal(p.savedPasswords.length, 0);
    assert.equal(p.navigations[0], "http://localhost/login?type=USER&error");
});

test("unchecking removes only current member type saved identity", () => {
    const p = page({ data: {
        "foodorder.saved-login.USER": JSON.stringify({ username: "user" }),
        "foodorder.saved-login.OWNER": JSON.stringify({ username: "owner" })
    } });
    p.fields.saveLogin.checked = false;
    p.events.change();
    assert.equal(p.storage.has("foodorder.saved-login.USER"), false);
    assert.equal(p.storage.has("foodorder.saved-login.OWNER"), true);
});

test("unchecked login never requests password storage", async () => {
    const p = page();
    await p.submit();
    assert.equal(p.storage.size, 0);
    assert.equal(p.savedPasswords.length, 0);
    assert.equal(p.navigations[0], "http://localhost/");
});

test("login still completes if password storage is declined or storage blocked", async () => {
    for (const options of [{ rejectStore: true }, { blockedStorage: true }]) {
        const p = page(options);
        p.fields.saveLogin.checked = true;
        await p.submit();
        assert.equal(p.navigations[0], "http://localhost/");
        assert.equal(p.button.disabled, false);
    }
});

test("corrupt saved data does not break login", async () => {
    const p = page({ data: { "foodorder.saved-login.USER": "invalid JSON" } });
    await p.submit();
    assert.equal(p.navigations[0], "http://localhost/");
});
