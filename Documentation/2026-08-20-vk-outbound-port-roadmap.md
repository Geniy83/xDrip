# VK Outbound (messages.send) — Roadmap для другого приложения

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** В целевом приложении реализовать отправку CGM-показаний в VK через Community Messages API (`messages.send`), совместимо с форматами BloodControl / GlucoWatch.

**Architecture:** Producer (новое чтение глюкозы) → очередь с дедупом/триггерами → renderer шаблона → HTTP POST form-urlencoded на `api.vk.com` → запись статуса доставки. Конфиг хранится локально (токен сообщества, peer_id получателей, шаблон, пороги).

**Tech Stack:** HTTP client приложения + local prefs/DB; VK API `messages.send` v5.199; без SDK VK (достаточно `HttpURLConnection` / OkHttp / fetch).

**Reference in BloodControl:** `OutboundApi.kt`, `OutboundApiSettings.kt`, `docs/outbound-api.md`, UI `OutboundApiSettingsScreen.kt`.

## Global Constraints

- Endpoint по умолчанию: `https://api.vk.com/method/messages.send` (не `vk.ru`).
- Версия API по умолчанию: `5.199`.
- Content-Type: `application/x-www-form-urlencoded; charset=UTF-8`.
- Получатели — только числовые `peer_id` / user id; каждый должен сначала написать сообществу.
- Коэффициент mmol↔mg/dL для шаблонов: согласовать с приложением (в BloodControl часто `18.0182`).
- Не логировать `access_token` в plaintext.
- Outbound-only в v1 (чтение истории VK — отдельная фича).

---

## File map (целевое приложение)

| Модуль | Ответственность |
|--------|-----------------|
| `VkOutboundSettings` / prefs | Destination model, defaults, validation, persistence |
| `VkOutboundQueue` / orchestrator | enqueue, triggers, min-interval, dedup by eventId |
| `VkMessageRenderer` | подстановка токенов шаблона |
| `VkMessagesSender` | POST `messages.send`, parse error, retryable codes |
| `VkOutboundSettingsScreen` | UI: токен, recipients, preset/template, test send |
| Hook в pipeline глюкозы | один вызов `enqueueGlucose(...)` на новое чтение |

---

## Phase 0 — Prerequisites (VK side)

- [ ] Создать сообщество VK (или использовать существующее).
- [ ] Включить сообщения сообщества; выдать ключ доступа сообщества с правом `messages`.
- [ ] Зафиксировать: пользователь-получатель обязан первым написать боту/сообществу (иначе `messages.send` вернёт ошибку доступа).
- [ ] Для тестов: известный numeric `peer_id` / user id и токен в секретах (не в git).

**Done when:** ручной `curl`/Postman к `messages.send` с тестовым текстом доставляет DM.

---

## Phase 1 — Destination model + persistence

Минимальная модель (как в BloodControl):

```text
Destination {
  id, enabled, name,
  preset: glucowatch_vk | vk_messages,   // два шаблона, один sender
  url,                                   // blank → DEFAULT_VK_URL
  token,                                 // access_token
  chatId,                                // recipients: comma/semicolon/newline
  apiVersion,                            // default 5.199
  messageTemplate,
  minIntervalMinutes,                    // default 5; 0 = always
  triggerMode: always | at_or_below | at_or_above | outside_range,
  triggerLowMgdl, triggerHighMgdl,       // 70 / 180
  lastQueuedEventId, lastQueuedAtMs,
  lastAttemptAtMs, lastSuccessAtMs,
  lastResponseCode, lastError
}
```

- [ ] `isReady`: token non-blank + ≥1 recipient + все recipients numeric.
- [ ] `resolvedUrl` / `resolvedTemplate` с defaults.
- [ ] Defaults:
  - URL: `https://api.vk.com/method/messages.send`
  - `vk_messages`: `{status_emoji} {value} {unit} {trend_arrow} {time}` (или упрощённо без emoji)
  - `glucowatch_vk`: `GV:{mmol}|RAW:{raw}|TR:{trend_arrow}|AL:{alarm}|RT:{rate_mmol}|IOB:{iob}|COB:{cob}|TS:{timestamp}`

**Done when:** unit-тесты round-trip JSON/prefs + `isReady` false на пустом токене / нечисловом id.

---

## Phase 2 — Template renderer

- [ ] Ввести flat DTO `Reading` (минимум для v1):

| Поле | Для токенов |
|------|-------------|
| eventId, recipient, timeMillis, test | `{event_id}` `{recipient}` `{timestamp}` `{time}` `{test}` |
| displayText, unit, mgdl, mmol | `{value}` `{unit}` `{mgdl}` `{mmol}` |
| raw*, rate*, trendName, trendArrow, alarm | `{raw}` `{rate_mmol}` `{trend_arrow}` `{alarm}` |
| iob, cob (optional) | `{iob}` `{cob}` — stub `0` если журнала нет |

- [ ] `renderMessage(template, reading)` — простой `.replace("{token}", ...)`.
- [ ] Стабильный `eventId` для live: `{sensorId}:{timeMillis}:{mgdl}`; для test: `test-{now}`.

**Совместимость GlucoWatch:** если целевое приложение должно кормить внешний парсер GlucoWatch, **не менять** `DEFAULT_GLUCO_WATCH_TEMPLATE` без согласования.

**Done when:** тесты: известный `Reading` → ожидаемая строка для обоих пресетов.

---

## Phase 3 — HTTP sender (`messages.send`)

Тело (form fields):

| Field | Value |
|-------|--------|
| `access_token` | destination.token |
| `v` | apiVersion or `5.199` |
| `peer_id` | recipient |
| `random_id` | stable non-neg int from hash(eventId, recipient, time, mgdl) |
| `message` | rendered text |

Поведение:

- [ ] POST UTF-8 form-urlencoded.
- [ ] Timeouts: connect ~15s, read ~30s.
- [ ] Успех: HTTP 2xx и JSON **без** `error`.
- [ ] `parseVkError`: читать `error.error_code` / `error_msg`.
- [ ] Retryable API codes: **6, 9, 10** (+ HTTP 429/5xx).
- [ ] Записывать lastAttempt / lastSuccess / lastError; **не** писать token в error string.

**Done when:** integration-тест с mock HTTP: happy path + error JSON + form encoding (URL-encode кириллицы/emoji).

---

## Phase 4 — Queue / gating / producer hook

На каждое новое чтение:

1. Загрузить enabled destinations с preset VK.
2. Skip если `!shouldSendForGlucose(mgdl)`.
3. Skip если duplicate `eventId` или внутри `minIntervalMinutes`.
4. `recordQueued` → enqueue background job (WorkManager / coroutine dispatcher / platform equivalent).
5. Для каждого recipient — отдельный send (как в BloodControl).

- [ ] Network guard: не слать offline (или ставить в pending с лимитом очереди).
- [ ] Кнопка **Test send** обходит interval/trigger, но использует тот же sender.
- [ ] Один вызов из pipeline глюкозы приложения (аналог `SuperGattCallback` → `OutboundApi.enqueueGlucose`).

**Done when:** два подряд одинаковых eventId → один send; test send всегда уходит.

---

## Phase 5 — Settings UI

Минимум экрана:

- [ ] Toggle enabled, name, preset picker (`glucowatch_vk` / `vk_messages`).
- [ ] Token, recipients (help: numeric IDs; first message to community required).
- [ ] API version (optional advanced).
- [ ] Template editor + список токенов.
- [ ] Min interval + trigger mode/thresholds.
- [ ] Status row: last success / last error / response code.
- [ ] Test button.

**Done when:** можно сохранить destination и отправить test без пересборки.

---

## Phase 6 — Hardening (v1.1)

- [ ] Rate-limit / backoff при VK flood (code 6/9).
- [ ] Cap pending sends (BloodControl: ~12).
- [ ] Privacy review: redact token in logs/crash reports.
- [ ] Optional: stale/missed status emoji (`{status}` / `{status_emoji}`) если нужны туннельные статусы.
- [ ] Docs для пользователя: как взять community token и peer_id.

**Out of scope for this roadmap (отдельные фичи):**

- Inbound: `messages.getHistory` как источник глюкозы.
- Edit-in-place / suppress-delta (есть у Telegram в BloodControl, у VK нет).
- Custom JSON webhook / Telegram — не блокируют VK MVP.

---

## Suggested delivery order (MVP → ship)

| Sprint | Deliverable | Risk |
|--------|-------------|------|
| S0 | Manual VK send works with community token | Token permissions / peer_id |
| S1 | Settings + persistence + isReady | Wrong recipient format |
| S2 | Renderer + GlucoWatch-compatible template | Unit conversion mismatch |
| S3 | Sender + error parsing | Flood control / random_id collisions |
| S4 | Queue + glucose hook + test button | Duplicate spam / battery |
| S5 | UX polish + privacy + docs | Support burden |

---

## Acceptance checklist (port complete)

- [ ] Live reading появляется в VK DM в течение ~1 мин при сети.
- [ ] Preset GlucoWatch: сообщение парсится внешним клиентом, если он ожидает `GV:…|RAW:…|…` формат.
- [ ] Preset text: читаемое сообщение с value/unit/arrow/time.
- [ ] Неверный token → понятная ошибка в UI, без краша.
- [ ] Получатель без диалога с сообществом → понятная VK error_msg.
- [ ] Token не попадает в логи и git.

---

## Reference snippets (из BloodControl)

**Send fields:** `OutboundApiWorker.sendVk` — `access_token`, `v`, `peer_id`, `random_id`, `message`.

**Defaults:** `OutboundApiSettings.DEFAULT_VK_URL`, `DEFAULT_VK_API_VERSION`, `DEFAULT_GLUCO_WATCH_TEMPLATE`, `DEFAULT_CHAT_TEMPLATE`.

**Architecture doc:** `docs/outbound-api.md`.
