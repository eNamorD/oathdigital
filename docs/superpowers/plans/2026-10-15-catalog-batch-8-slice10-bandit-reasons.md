# Catalog Batch 8, Slice 10: Hover Reasons for Unwaivable Operation Refusals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an option that an operation refusal nobody may waive hides, such as a Muster source under a bandit-ruled Forest Council, disabled with its hover reason instead of leaving it out.

**Architecture:**

- **Every power refusal on the operation path carries a waiver.** Under bandits its granter is `None`, as the Hall of Ministers' already is. A waiver with no granter means "a grant would lift this, but nobody may give one".
- **`OperationWaiver` gains `plain: PowerNote`**, the reason shown when nobody may be asked, beside `note`, the asked reason.
- **`OperationAsks` words what it cannot ask.** When granting every refusal a path meets opens it, but `OperationConsent.ask` finds no ask, the option is shown `OptionReason.Noted(first refusal's power, its plain note)`. Asking is impossible when a refusal has no granter, its only granter is the decision's owner, or a ruler refused it this turn (consent design, ruling 8). `OperationConsent.shown` owns this choice, mirroring `PowerUseRules.shown` on the use path.
- **A plain reason never keeps a decision alive.** The walker keeps a required decision whose every option is hidden, and stops a search at it, only for an askable option. A `Noted` operation reason decorates an option only beside an option that can be chosen. Otherwise a decision of unavailable options would park and lock the game.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, slice 10's "Plan-time rulings", the bullets "Where a placement is askable" and "An unexplained placement refusal". `docs/superpowers/specs/2026-10-08-consent-system-design.md`, ruling 8.

**Starts from:** `main` with slice 10 part 2 merged (`80226fde`).

## Global Constraints

- **Build rules.** `-Werror` with unused-import, private and local warnings.
- **File size.** Production and test Scala files stay at or under 800 lines. `ProcedureWalker.scala` is about 765 lines.
- **Import rules.** The engine (`gameplay/walker`, `gameplay/operations`) never names a power.
- **Line width.** Scala lines stay at or under 80 characters.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Stage explicit paths only. Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Fast-forward to local `main`, symlink `.tooling` and `node_modules`, and run `ExitWorktree` with `keep` before merging.
- **Hover reasons, exactly:**
  - "Forest Council: Cannot place favor or secrets on Beast cards."
  - "City Wall: Cannot place favor or secrets on cards while at its site."
  - "Hall of Ministers: Cannot discard cards from its ruler's sites." (new key `no-discard`)
- **Note keys.** Regenerate `src/test/resources/notes/declared-keys.txt`. The diff adds only the Hall's `no-discard`.

---

### Task 1: Plain reasons on the operation path

**Files:**
- Modify: `src/main/scala/oathdigital/model/OperationReason.scala` (`OperationWaiver.plain`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/OperationConsent.scala` (add `shown`)
- Modify: `src/main/scala/oathdigital/gameplay/operations/HallOfMinisters.scala` (`noDiscard`, `plain`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/poweruse/RulerBans.scala` (`reason` always waives, `plain`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/poweruse/ForestCouncil.scala`, `CityWall.scala` (pass the plain key)
- Modify: `src/main/scala/oathdigital/gameplay/walker/OperationAsks.scala` (`OperationConsent.shown`)
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala` (`narrowAt`, `reach`: only an ask keeps a decision)
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (the gap bullet)
- Test: `OperationAsksSuite`, `ForestCouncilSuite`, `CityWallSuite`, `OperationRestrictionsSuite`, `PowerUseRulesSuite`

**Interfaces:**
- `OperationWaiver(power: PowerId, granter: Option[PlayerId], covers: ConsentKey, note: PowerNote, plain: PowerNote)`.
- `OperationConsent.shown(ready: ReadyGame, actor: PlayerId, refusals: Vector[OperationReason]): Option[OptionReason]`: `ask`, or else `Noted(first waiver's power, its plain)` when every refusal has a waiver; `None` otherwise.
- `RulerBans.reason(power, card, ruler, requester, covers, plain: NoteKey, asked: NoteKey, code): OperationReason`.

- [ ] **Step 1: Write the failing tests**

In `OperationAsksSuite`, the test power's waiver gains `plain = said(...)` with a new key `said`, and:
- "with nobody to grant it, the card shows its plain reason": `favorOnAlchemistBannedBy(None)` gives `Vector(Unavailable(Denizen(alchemistRef), Noted(ban, said(card))), Denizen(wrestlersRef))`.
- "a refusal remembered this turn shows the plain reason": the ruling 8 test expects the same plain row instead of leaving Alchemist out.
- "a required decision whose only option is plain is not kept": with `beside = false` and no granter, the Muster has nothing to offer. Its advance is a `Left`, as before part 2.

In `ForestCouncilSuite`, the bandit test expects Errand Boy `Unavailable(..., Noted(ForestCouncil.id, ForestCouncil.placingPlain(source)))`.

In `CityWallSuite`, add "under bandits the placing ban names nobody and its plain reason". City Wall refuses every card at its site, and a Muster's sources stand at the musterer's site, so no Muster there keeps a choosable source beside a refused one. The refusal itself is pinned instead: `ban.reason` on a bandit-ruled board gives a waiver with no granter and `plain == CityWall.placingPlain(source)`.

In `OperationRestrictionsSuite` and `PowerUseRulesSuite`, add the `plain` argument to each `OperationWaiver`.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.walker.OperationAsksSuite oathdigital.gameplay.powers.poweruse.*"`
Expected: compilation fails, because `OperationWaiver` has no `plain`.

- [ ] **Step 3: Carry the plain reason**

`OperationWaiver` gains `plain: PowerNote`. The Hall declares `noDiscard` ("Cannot discard cards from its ruler's sites.") and passes `noDiscard(source)`. `RulerBans.reason` takes `plain` and always returns a waiver, its granter the player ruler or `None`; `RulerBans.refusal` passes its `plain` on. Forest Council and City Wall pass `placingPlain` to `reason`.

- [ ] **Step 4: Word what cannot be asked**

```scala
  /** How an option whose plan only `refusals` stop is shown: askable when
    * `ask` finds an ask, else unavailable with the first refusal's plain
    * reason, when every refusal is a power's that a grant would lift.
    * `None` when some refusal has no waiver. */
  def shown(ready: ReadyGame, actor: PlayerId,
      refusals: Vector[OperationReason]): Option[OptionReason] =
    val waivers = refusals.flatMap(_.waiver)
    if refusals.isEmpty || waivers.size < refusals.size then None
    else ask(ready, actor, refusals).orElse(waivers.headOption.map(first =>
      OptionReason.Noted(first.power, first.plain)))
```

`OperationAsks.ask` calls `shown` in place of `ask`.

- [ ] **Step 5: Keep a decision only for an ask**

In `ProcedureWalker.narrowAt`'s `Left` branch, keep the decision only when some reason is `OptionReason.Askable`, and show every reason. In `reach`, stop only when an operation reason is askable. The `Right` branch is unchanged: a plain reason sits beside a choosable option there.

- [ ] **Step 6: Close the gap in the design**

Replace "An unexplained placement refusal" with "**A placement nobody may waive** (part 2). Under bandits, a source card whose placement is refused is shown unavailable with its card's plain reason, beside the cards that may be chosen. A decision with no card left to choose is not asked."

- [ ] **Step 7: Run everything and commit**

Run: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`, check the diff adds only `no-discard`, then `./sbtw test "frontend/test"`, `python3 scripts/check-architecture.py`, `python3 scripts/check-markdown-links.py`.

```bash
git commit -m "feat(batch8): an operation refusal nobody may waive shows its reason"
```
