# Full stack 'Android' dev: The idea — one language, every screen

*Part 1 of a series that builds a complete multi-sport tournament predictor — backend,
Android, iOS, and web — entirely in Kotlin, writing the business logic exactly once.*

> **Full stack 'Android' dev** — a series:
> 1. **The idea — one language, every screen** *(you are here)*
> 2. [The shared module — write the rules once](02-the-shared-module.md)
> 3. [The Ktor backend — a JVM server that speaks your model](03-the-ktor-backend.md)
> 4. [One Compose UI — Android, iOS, and the web](04-the-frontend.md)
> 5. [The numbers — what sharing actually saves](05-the-numbers.md)

---

## Why "Full stack 'Android' dev"? (the name, and the quotes)

Let's deal with the title first, because the quotes around *'Android'* are the whole
argument.

An Android developer already owns a specific, powerful toolkit: **Kotlin** as the
language, **Jetpack Compose** for UI, **coroutines** for concurrency, **Gradle** for the
build. Traditionally, if that developer wanted to go "full stack," they were told to go
learn an *entirely different* world — TypeScript and React for the web, a separate
backend language, a separate build system, a separate way of thinking. Full stack meant
becoming a different kind of engineer.

This series makes the opposite bet: **you can build the entire stack without leaving the
Android developer's toolkit.**

- The **backend** is Kotlin (Ktor on the JVM). Same language, same coroutines, same
  Gradle.
- The **iOS app** is Jetpack Compose — via Compose Multiplatform, the *same* `@Composable`
  functions you write for Android, compiled to a native iOS binary.
- The **web app** is that same Compose UI again, compiled to WebAssembly.
- And the **domain model and business rules** they all share are Kotlin written once.

So the "Android developer" turns out to be a full-stack developer already — they just
hadn't pointed their existing skills at the other targets yet. That's why *'Android'* is
in quotes: this isn't a series about building Android apps. It's about how far the Android
developer's tools and instincts now reach — **backend to browser to both mobile
platforms**, all in one language. The quotes are a wink at the fact that "Android dev" has
quietly become "full-stack dev" without anyone having to relearn their craft.

---

## The problem this solves

Most "full-stack" teams quietly maintain the same ideas several times over.

You define a `Match` on the backend. Then you define it *again* in TypeScript for the web.
Then, for mobile, a *third* and *fourth* time — Swift for iOS, Kotlin for Android. Four
definitions of one concept. Four ideas of what a valid prediction is. And the part that
really hurts: as many implementations of your scoring formula as you have platforms — each
one free to drift from the others the first time someone fixes a bug in one and forgets
the rest.

None of that duplication is essential. The rules of your domain don't change based on
whether they run in a data center or on a phone. So why write them more than once?

This series builds a small but complete application that refuses the duplication: a
**tournament prediction game** — pick the scores of upcoming fixtures, earn points when
you're right, climb a leaderboard — where every layer is Kotlin, and the layers *share*
their core.

---

## The one idea

Here's the whole thesis in a sentence:

> **Write the domain model, the business rules, and the API contract once, in a shared
> module, and compile that same source to every platform that needs it.**

Kotlin Multiplatform (KMP) makes this literal rather than aspirational. A single Kotlin
module can compile to:

- **JVM bytecode** — consumed by the backend.
- **Android** — running on the Android runtime.
- **Native** — linked into an iOS app.
- **WebAssembly** — running in the browser.

Put your domain in that module and there is exactly one `Match`, one definition of a valid
prediction, one scoring function — shared by the server and every client by construction.
Not "kept in sync by discipline." The same source, compiled several ways.

---

## The shape of the thing

```
         ┌─────────────────────────── :shared ───────────────────────────┐
         │  domain model · scoring engine · leaderboard · API contract    │
         │  (pure Kotlin, no platform assumptions)                        │
         └───────┬───────────┬───────────────┬───────────────┬───────────┘
                 │ →ᴶᵛᴹ      │ →Android      │ →ⁱᵒˢ          │ →ᵂᵃˢᵐ
                 ▼           ▼               ▼               ▼
             :server     Android app     iOS app        web app
             (Ktor)      (Compose)       (Compose)      (Compose, bonus)
```

Every consumer depends on `:shared`. That fan-out — many consumers, one definition — is
the entire point. Everything else is plumbing that exists to serve it.

The reference app in this series ships with the server and one Compose Multiplatform UI.
Because the UI is written in **Compose Multiplatform**, that *same UI code* is what runs on
Android, iOS, and the web — you don't rebuild the screens per platform any more than you
rebuild the domain model.

---

## Android and iOS first, web as the bonus

A quick, honest word on maturity, because it shapes how you should pitch this to a team.

**Compose Multiplatform is production-stable on Android and iOS.** On Android it *is* the
standard, first-party Jetpack Compose toolkit. On iOS, JetBrains has declared Compose
Multiplatform stable — real apps ship with it today, sharing their entire UI layer with
Android. So the primary story of this series is a pleasantly boring one: **one Kotlin
codebase, two native mobile apps, no per-platform UI rewrite.**

**The web target (Compose for Web via WebAssembly) is the newer, more adventurous one.**
It's excellent for app-like, canvas-rendered UIs — dashboards, tools, and games like this
predictor — and it lets the *exact same* Compose screens run in a browser tab. But it
paints onto a canvas rather than emitting semantic HTML, so it isn't how you'd build a
content- or SEO-driven marketing site, and the toolchain is younger than the mobile one.

So the framing throughout this series is deliberate: **Android and iOS are the main event;
the web app is a bonus you get almost for free**, because it's the same shared module and
the same Compose UI aimed at one more target.

---

## What "write it once" actually buys you

Three things live in `:shared` and are consumed *identically* by every tier. We go deep on
each in later parts; here's why they matter.

- **The domain model.** One `Match`, one `Tournament`, one `MatchOutcome`. The server
  serializes these types to JSON and every client deserializes straight back into the same
  classes. There is no hand-written mobile or web mirror of the server's shapes to fall out
  of date.

- **The scoring engine.** The pure function that turns *(your prediction, the actual
  result, the rules)* into points. It runs on the **server** as the authority that computes
  the official leaderboard, and it runs **on the device** so the app can show *"+3 — right
  result & margin"* the instant a match finishes — computed locally, instantly, and
  *guaranteed* to match the server because it is literally the same code. You physically
  cannot have two implementations, because there is only one.

- **The API contract.** The route paths and the request/response types live in `:shared`
  too. The server *implements* a route; every client *calls* the same declared path and
  types. Rename a path and both sides move together — or the build breaks. **The compiler is
  your contract test**, with no separate spec to drift.

The payoff compounds across platforms. A refactor to the scoring rule is a single edit in
one file; the server, the Android app, the iOS app, and the web app all pick it up on the
next compile. A change that would normally have to cross four codebases and a network
boundary crosses one.

---

## What it costs (the honest version)

It isn't free, and pretending otherwise helps no one:

- **The toolchain is heavier than `npm` or a plain backend build.** The first Gradle build
  pulls the Kotlin Multiplatform compiler, Compose, and (for web) a Node/webpack layer.
  Subsequent builds are cached and quick, but the cold start is real.
- **The web target is young.** Great for app-like canvas UIs; not the tool for a
  semantic-HTML, SEO-first site.
- **The ecosystem is Kotlin/JetBrains-centric.** For a domain like this that's plenty; for
  a niche widget you might occasionally miss a specific npm or native package.

The upside is the reason to pay it: **no duplicated domain logic, no client/server or
mobile/web drift, one language to hire for and reason about, and refactors that cross the
network boundary in a single compile.** For a team already strong in Kotlin — that is, for
an Android team — that trade is often very good, and it gets better with every extra
platform, because each new screen reuses the same core instead of re-implementing it.

---

## Where this series goes next

We build the whole thing, one layer at a time:

1. **This part — the idea.** One language, one shared codebase, every screen.
2. **[The shared module.](02-the-shared-module.md)** The domain model, the write-once
   scoring engine, a data-driven design that adds a whole new sport in one line, and the
   shared API contract.
3. **[The Ktor backend.](03-the-ktor-backend.md)** A small JVM server that consumes the
   shared types directly and serves both the API and the clients.
4. **[One Compose UI.](04-the-frontend.md)** The same screens running natively on Android
   and iOS — and, as the bonus, in the browser as WebAssembly.

By the end, one function — the scoring engine — will run on the server as JVM bytecode and
on every client from the same source, producing identical results by construction. That
one file running everywhere is the whole idea.

---

*Built with Kotlin Multiplatform, Ktor, and Compose Multiplatform. One language, from the
database to every screen.*
