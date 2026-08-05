# Full stack 'Android' dev: The numbers — what sharing actually saves

*A data appendix to a series building a multi-sport tournament predictor entirely in
Kotlin. The previous four parts argued that writing the code once is better. This part
counts the lines and the tokens to show, concretely, how much better.*

> **Full stack 'Android' dev** — a series:
> 1. [The idea — one language, every screen](01-the-idea.md)
> 2. [The shared module — write the rules once](02-the-shared-module.md)
> 3. [The Ktor backend — a JVM server that speaks your model](03-the-ktor-backend.md)
> 4. [One Compose UI — Android, iOS, and the web](04-the-frontend.md)
> 5. **The numbers — what sharing actually saves** *(you are here)*

---

The whole series makes one bet: write the domain model, the scoring rule, the API
contract, and even the UI *once*, and let the compiler send that same source to the JVM,
Android, iOS, and the browser. That's a nice story. This part checks it against the
repository as it actually stands.

Everything below is measured, not estimated (except where explicitly labelled). The line
counts come from `wc -l` over `*.kt` files, tests excluded, generated code and build
scripts excluded. You can reproduce every figure with the commands in the last section.

---

## The map: where the code lives

Here is the entire production codebase, by source set and the targets each one compiles
to:

| Source set                 | Lines | Files | Compiles to                          |
|----------------------------|------:|------:|--------------------------------------|
| `:shared` / `commonMain`   |   437 |    12 | **JVM · Android · iOS · Wasm** (4)    |
| `:server` / `main`         |   860 |     7 | JVM only                             |
| `:web` / `commonMain`      |   896 |     7 | **Android · iOS · Wasm** (3)          |
| `:web` / `androidMain`     |    29 |     2 | Android launcher only                |
| `:web` / `iosMain`         |    19 |     2 | iOS launcher only                    |
| `:web` / `wasmJsMain`      |    18 |     2 | Wasm launcher only                   |
| **Total**                  | **2,259** | **32** |                                  |

Two rows carry the whole argument, and they're the two in bold. `:shared/commonMain` is
437 lines that run on *every* tier — server and all three clients. `:web/commonMain` is
896 lines of Compose UI that run on all three *clients*. Between them, that's **1,333
lines written exactly once.**

---

## Metric 1 — how much of the code is shared?

There are two distinct sharing stories, and it's worth keeping them apart because they're
each strong on their own.

**Cross-tier logic — the `:shared` module.** 437 lines — the domain model, the scoring
engine, the leaderboard calculator, and the REST contract — are compiled *unchanged* into
the backend and all three clients. This is the single source of truth the series keeps
pointing at: the same `PredictionScorer.kt` that ranks the leaderboard on the server also
computes the points that appear in the browser as you type a prediction. One file, four
targets, identical results by construction.

**Client UI reuse — the `:web` module.** Of the 962 lines of client code, **896 are
shared** across Android, iOS, and Wasm; only **66** are platform-specific — three tiny
launchers that each wire up an entry point and a Ktor HTTP engine:

$$\frac{896}{896 + 66} = 93.1\%\ \text{of the client UI is written once}$$

The 66 platform lines are not domain logic and not screens — they're the `MainActivity`,
the `MainViewController`, the `main()`, and one `expect`/`actual` for the HTTP engine.
Everything a user actually sees and interacts with is in the shared 896.

**Overall.** Counting the whole production tree:

$$\frac{437 + 896}{2{,}259} = 59\%\ \text{of all production code is written once and reused across targets}$$

Nearly six lines in ten are single-source. The remaining 41% is the genuinely
tier-specific work — the Ktor wiring, the database layer, the seed data, the three
launchers — code that *should* differ per target because it's doing a per-target job.

---

## Metric 2 — the code you didn't have to write

A ratio understates the win, because the honest comparison isn't "shared vs. not shared in
this repo" — it's "what would the *conventional* stack have cost?" In the traditional
polyglot arrangement, each shared piece gets reimplemented per platform, by hand, with no
compiler keeping the copies in agreement.

**The UI.** 896 shared lines stand in for three separately-written UIs — SwiftUI on iOS, a
Compose/Views app on Android, and a React app for the web. Even assuming each
reimplementation is no larger than the shared one:

$$896 \times 3 \approx 2{,}688\ \text{lines}\quad\longrightarrow\quad 896 + 66 = 962\ \text{lines actually written}$$

That's a **~2.8× reduction** on the client UI — and, more importantly, three
implementations that *cannot* drift, because there is only one.

**The domain, scoring, and contract.** The 437-line `:shared` module would otherwise be
reimplemented on each tier that needs it. The floor is two copies: the scoring rule *must*
exist on both the server (the leaderboard authority) and the client (the live points as
you type) — that duality is the feature. With native iOS and Android clients that also
score locally, it's up to four. So the shared module collapses somewhere between **~874
and ~1,748 lines of drift-prone, hand-synchronised duplication into 437** — and it deletes
an entire category of artefact along the way: there is no separate OpenAPI spec and no
contract test, because `ApiContract.kt` *is* the contract and the compiler *is* the test.
Change an endpoint's shape and both the server's route and the client's call fail to
compile until they agree.

---

## Metric 3 — the token angle: cheaper changes, not just fewer lines

Line counts describe the code at rest. The more interesting cost is the code *in motion* —
what it takes to change it — and that's exactly where a single source of truth compounds,
including when an AI assistant is doing the editing.

First, the sizes of the shared units (bytes measured; tokens estimated at the usual
~4 characters/token for source):

| Shared unit                                   | Bytes  | ≈ tokens |
|-----------------------------------------------|-------:|---------:|
| API contract (`ApiContract.kt`: paths + DTOs) |  2,293 |    ~570  |
| Scoring engine (scorer + rules + `Sport`)     |  7,174 |  ~1,800  |
| Whole `:shared` module                        | 14,860 |  ~3,700  |
| All production Kotlin                          | 85,739 | ~21,400  |

The point isn't the size of any one file — it's the **multiplier that appears when a change
spans the stack.** Take a concrete, representative task from this codebase: *add a new
sport.* Because `Sport` is [data, not branches](02-the-shared-module.md) — each enum entry
carries its own vocabulary, draw rule, and scoring preset, and there are no `when (sport)`
blocks anywhere — the change is:

```
1. one new entry in  shared/.../model/Sport.kt   (~one line of enum + its properties)
2. seed data in      server/.../data/SeedData.kt
```

Two files. The API serialises the new sport, the scorer scores it, and every screen renders
its vocabulary — all without another edit, because they all read the same `Sport`
properties. An agent (or a person) loads on the order of those two files — roughly a couple
of thousand tokens of context — emits two edits, and the compiler guarantees nothing was
missed.

Now cost the same change in the conventional polyglot stack. The sport concept lives, by
hand, in:

- the **server's** model and scoring code,
- the **web** client's model and rendering,
- the **iOS** client's model and rendering,
- the **Android** client's model and rendering,
- and a **separate API spec** (plus the contract tests that exist precisely because nothing
  else keeps these in sync).

The context an assistant must read, and the edits it must emit, scale **roughly linearly
with the number of duplicated copies — about 2–4×** — plus the reconciliation pass to make
the copies agree, plus the standing risk of updating three tiers and silently forgetting
the fourth. Single-source turns an N-tier coordinated edit into a 1× edit that *can't
compile* if it's incomplete. The saving isn't only tokens; it's the elimination of a whole
failure mode.

That's the through-line of the entire series, expressed as a cost: **you don't just write
the logic once — you read it once, change it once, and review it once, every time it
moves.**

---

## The headline figures

- **59%** of all production Kotlin is written once and shared across compilation targets.
- **93%** of the client UI is a single Compose codebase; only **66 lines** are
  platform-specific.
- **437 lines** — the domain, scoring, and contract — compile to **four** targets from one
  source, replacing an estimated **~874–1,748 lines** of hand-synchronised duplication.
- A typical cross-stack change (e.g. *add a sport*) touches **2 files** here, versus the
  **~2–4×** context-and-edit cost of keeping duplicated copies in agreement — with no spec
  to update and no contract test to write, because the compiler is both.

---

## Reproduce it yourself

Every number above comes from the checked-in source. To regenerate the counts:

```bash
# Lines per source set (tests, generated code, and build scripts excluded)
for ss in shared/src/commonMain \
          server/src/main \
          web/src/commonMain \
          web/src/androidMain web/src/iosMain web/src/wasmJsMain; do
  printf "%-28s " "$ss"
  find "$ss" -name '*.kt' | xargs wc -l | tail -1 | awk '{print $1 " lines"}'
done

# Bytes (for the token estimate) of the shared module and its parts
find shared/src/commonMain -name '*.kt' | xargs cat | wc -c          # whole module
cat shared/src/commonMain/kotlin/com/predictor/shared/api/ApiContract.kt | wc -c
```

Counts reflect the repository at the time of writing; they'll drift as the code grows —
but the *ratios* are the durable point, and they get **better** with every platform you
add, because each new target reuses the same 1,333 write-once lines instead of
reimplementing them.

---

## Where this leaves the series

Part 1 made the promise, Parts 2–4 built the thing, and this appendix priced it. The bet
pays in three currencies at once: **fewer lines to write, zero drift to chase, and cheaper
changes forever after** — and, uniquely to Kotlin Multiplatform, it collects all three
without the team leaving the Android toolkit it already knows.

Start reading the source at `shared/scoring/PredictionScorer.kt` and follow it outward.
That one file runs everywhere — and now you know roughly what writing it four times would
have cost.

---

> **Full stack 'Android' dev** — a series:
> [1. The idea](01-the-idea.md) ·
> [2. The shared module](02-the-shared-module.md) ·
> [3. The Ktor backend](03-the-ktor-backend.md) ·
> [4. One Compose UI](04-the-frontend.md) ·
> **5. The numbers**
