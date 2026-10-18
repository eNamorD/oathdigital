# Catalog Batch 8, Item 14b (Rename the Walk's Actor) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every name that holds the walk's actor (`CurrentGameState.walkActor`) is called `actor`, not `activePlayer`. The turn's player keeps the name `activePlayer`.

**Architecture:**

- **Renamed.**
  - The fields `PowerCtx.activePlayer` and `WalkCtx.activePlayer` become `actor`, along with every read of them and every named argument that sets them.
  - Every local and parameter in the walker, in `OathRulesWalker` and in `WalkerDecisionProjector` that holds `walkActor` is renamed.
  - So are the procedure builders' `activePlayer` parameters and the registry lambdas that pass them on.
- **Kept.**
  - `TurnState.activePlayer` (`model/GameState.scala`), the turn's player, read as `turn.activePlayer` and `current.turn.activePlayer`.
  - The frontend projection's `activePlayer`, which is a wire field.
- **Aliases inlined.** The 19 powers that open with `val actor = ctx.activePlayer` read `ctx.actor` directly. An alias stays only where inlining would push a line past 80 characters.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. Python 3 for the rename scripts.

**Spec:** None, since a rename has no design to argue. The decisions come from the ROADMAP entry for item 14b and from the planning round of 2026-10-18: scope (c), inline the aliases, wait for `rename-note-family` to land, and a plan only.

## Global Constraints

- Production Scala files stay at or under 800 lines, and every Scala line at or under 80 characters (`scripts/check-architecture.py`).
- Behaviour does not change. The full suite passes unchanged apart from the renamed names.
- Dated plans and specs under `docs/superpowers/` are history. Leave their `activePlayer` mentions alone.
- Stage explicit paths only; never `git add -A`.

**Starts from:** `main` at `c46fc524` or later, with this plan committed. The scripts match names, not line numbers, so they run on any later `main`. Where a later commit adds a new `activePlayer` holding the walk's actor, the compile step or the final grep finds it.

**Worktree:** run the plan in a worktree branched from local `main`. Symlink the main checkout's `.tooling` and `node_modules` into it before the first `./sbtw`.

## Shadowing, checked at plan time

Renaming a bare `activePlayer` to `actor` would silently rebind a reference if the same method also binds `actor`. The compiler does not catch that. In every file the bare rename touches, the existing `actor` binders sit in sibling methods, not in the renamed ones:

- `OathRulesWalker`: `withFallback`, `offerableWalkerPowers` and the method at line 162.
- `ForgeProcedure.paidNote`.
- `RecoverProcedure`: `rollFeedback`, `actorSite`.
- `TravelProcedure`: `startFacts`, `actorSite`, `queuedDestination`.
- `TakeWealthProcedure`: `noEnemyPawn`, `tree`.
- `WalkerProcedureRegistry`: lines 536 and 590.
- `WalkerDecisionProjector`: `tree`, line 682, `declaredTree`.
- `WalkerProcedureRegistrySuite`: the class-level `private val actor`, which the renamed named argument `actor = actor` reads.

Task 1 Step 3 repeats this check against the diff.

---

### Task 1: Rename `activePlayer` to `actor` for the walk's actor

**Files:**
- Modify: every `src/**/*.scala` file that reads `ctx.activePlayer` or `ran.activePlayer` (113 files on `c46fc524`).
- Modify, bare names: the 17 files listed in `BARE` below.
- Keep: `src/main/scala/oathdigital/model/GameState.scala`, `src/test/scala/oathdigital/gameplay/powers/poweruse/ForestCouncilSuite.scala`, every `turn.activePlayer` and `next.activePlayer` (a `TurnState`), and `frontend/`.

**Interfaces:**
- Produces: `PowerCtx.actor: PlayerId` and `WalkCtx.actor: PlayerId`. Every procedure builder's player parameter is named `actor`.

- [ ] **Step 1: Write the rename script**

Save it as `rename_actor.py` in the scratchpad:

```python
import os, re, sys
root = sys.argv[1]
SRC = root + '/src'
M = 'src/main/scala/oathdigital/'
T = 'src/test/scala/oathdigital/'
# Files whose every bare `activePlayer` holds the walk's actor.
BARE = [
    M + 'application/WalkerDecisionProjector.scala',
    M + 'gameplay/OathRulesWalker.scala',
    M + 'gameplay/actions/forge/ForgeProcedure.scala',
    M + 'gameplay/actions/recover/RecoverProcedure.scala',
    M + 'gameplay/actions/travel/TravelProcedure.scala',
    M + 'gameplay/oathkeeper/OathkeeperProcedure.scala',
    M + 'gameplay/phases/wake/EndWakeProcedure.scala',
    M + 'gameplay/phases/wake/TakeWealthProcedure.scala',
    M + 'gameplay/powerresolver/ContributingPower.scala',
    M + 'gameplay/setup/SetupProcedure.scala',
    M + 'gameplay/walker/ExecutedTriggers.scala',
    M + 'gameplay/walker/ProcedureWalker.scala',
    M + 'gameplay/walker/WalkState.scala',
    M + 'gameplay/walker/WalkerPowerGather.scala',
    M + 'gameplay/walker/WalkerProcedureRegistry.scala',
    T + 'gameplay/ContributionCollectorSuite.scala',
    T + 'gameplay/walker/WalkerProcedureRegistrySuite.scala',
]
FIELD = re.compile(r'\b(ctx|ran)\.activePlayer\b')
NAME = re.compile(r'(?<![.\w])activePlayer\b')
changed = 0
for d, _, fs in os.walk(SRC):
    for f in fs:
        if not f.endswith('.scala'):
            continue
        p = os.path.join(d, f)
        rel = os.path.relpath(p, root)
        s = open(p).read()
        t = FIELD.sub(r'\1.actor', s)
        if rel in BARE:
            t = NAME.sub('actor', t)
        if t != s:
            open(p, 'w').write(t)
            changed += 1
for rel in BARE:
    assert os.path.exists(os.path.join(root, rel)), rel
print('changed', changed)
```

`NAME` skips a name after a dot, so `ready.game.current.turn.activePlayer` in `ContributionCollectorSuite` keeps its name. The named argument before it is renamed.

- [ ] **Step 2: Run the script and compile**

Run: `python3 <scratchpad>/rename_actor.py <worktree>`, then `./sbtw "Test/compile"`.

Expected: about 125 files changed, and a clean compile. A compile error names a site the script missed or renamed wrongly. Fix it by hand: a `PowerCtx` or `WalkCtx` read under another receiver name becomes `.actor`; a `TurnState` read stays `activePlayer`.

- [ ] **Step 3: Check for shadowing and leftovers**

Run: `git diff -U0 | grep -nE '^\+.*\bactor\b'` and read each renamed method in the `BARE` files. No renamed method may also bind `actor` (a parameter, `val`, lambda or `case` binder).

Run: `grep -rnw activePlayer src --include='*.scala'`. Every remaining hit must be a `TurnState` field, its reads through `turn`, `next` or `current.turn`, or the `copy(activePlayer = …)` calls in `ForestCouncilSuite`.

- [ ] **Step 4: Run the suite**

Run: `./sbtw "test"`. Expected: all tests pass.

- [ ] **Step 5: Commit**

Stage the changed files by explicit path (from `git diff --name-only`), then commit:

```bash
git commit -m "refactor(batch8): name the walk's actor actor" \
  -m "PowerCtx and WalkCtx carry the walk's actor, which since slice 14 can be a queued entry's player rather than the turn's. The field, the walker locals that hold walkActor, and the procedure builders' parameters are renamed. TurnState.activePlayer keeps its name." \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Inline the `val actor = ctx.actor` aliases

**Files:**
- Modify: the powers that open with `val actor = ctx.actor`: ExtraWarbands, ActingSite, TrueOath, BanditPrince, FamilyHeirloom, KeyToTheCity, Riots, SaladDays, TownMeeting, RoyalTax, FabledFeast, TwinBrother, Garrison, CallForHelp, GreatHerd, ARoundOfAle, WizardsConclave, AnimalHost, Blackmail.

**Interfaces:**
- Consumes: `PowerCtx.actor` from Task 1.

- [ ] **Step 1: Write the inline script**

Save it as `inline_actor.py` in the scratchpad. It finds the alias, takes its scope as the lines up to the first non-blank line indented less than the alias, and replaces `actor` references in code (not in comments) with `ctx.actor`. A named argument `actor = …` keeps its name. A file where any scope line would pass 80 characters is left untouched and reported.

```python
import os, re, sys
root = sys.argv[1]
ALIAS = re.compile(r'^(\s*)val actor = ctx\.actor\s*$')
REF = re.compile(r'(?<![.\w])actor\b(?!\s*=(?![=>]))')
kept, inlined = [], []
for d, _, fs in os.walk(root + '/src/main/scala'):
    for f in fs:
        if not f.endswith('.scala'):
            continue
        p = os.path.join(d, f)
        lines = open(p).read().split('\n')
        out, i, ok, hit = [], 0, True, False
        while i < len(lines):
            m = ALIAS.match(lines[i])
            if not m:
                out.append(lines[i]); i += 1; continue
            hit, indent = True, len(m.group(1))
            i += 1
            while i < len(lines):
                line = lines[i]
                if line.strip() and len(line) - len(line.lstrip()) < indent:
                    break
                stripped = line.lstrip()
                if stripped.startswith(('*', '/*', '//')):
                    out.append(line)
                else:
                    code, sep, comment = line.partition('//')
                    new = REF.sub('ctx.actor', code) + sep + comment
                    if len(new) > 80:
                        ok = False
                    out.append(new)
                i += 1
        if not hit:
            continue
        rel = os.path.relpath(p, root)
        if ok:
            open(p, 'w').write('\n'.join(out))
            inlined.append(rel)
        else:
            kept.append(rel)
print('inlined', len(inlined))
print('kept (line over 80):', *kept, sep='\n  ')
```

- [ ] **Step 2: Run it and compile**

Run: `python3 <scratchpad>/inline_actor.py <worktree>`, then `./sbtw "Test/compile"`.

Expected: a clean compile. A lambda or `case` binder named `actor` inside a scope would come out as `ctx.actor =>`, which does not compile. Restore the alias in that file by hand.

- [ ] **Step 3: Read the diff**

Run: `git diff`. Check that no string literal or log text changed, and that each removed alias left no blank line it used to separate.

- [ ] **Step 4: Run the touched suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.*"`. Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor(batch8): powers read ctx.actor directly" \
  -m "The val actor = ctx.activePlayer aliases only repeated the field's new name." \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Docs and ROADMAP

**Files:**
- Modify: `docs/architecture/follow-up-actions.md` ("Off-turn actors": `ctx.activePlayer` becomes `ctx.actor`).
- Modify: `docs/ROADMAP.md` (item 14b).

- [ ] **Step 1: Update the architecture doc**

In "Off-turn actors", change `` `ctx.activePlayer` is that actor`` to `` `ctx.actor` is that actor``.

- [ ] **Step 2: Check item 14b off in the ROADMAP**

Replace the open entry with:

```markdown
- [x] **14b. Rename the walk's actor.** `PowerCtx` and `WalkCtx` name the
  walk's actor `actor`, and so do the walker's locals and the procedure
  builders' parameters. The turn's player keeps `activePlayer`.
  Plan: [item 14b](superpowers/plans/2026-10-18-catalog-batch-8-item14b.md).
```

Replace "Only item 14b of catalog batch 8 remains open." with "Catalog batch 8 is complete." In the phase's status line, add 14b to the done items.

- [ ] **Step 3: Check the docs**

Run: `python3 scripts/check-markdown-links.py` and `python3 scripts/check-architecture.py`. Expected: both pass.

- [ ] **Step 4: Run the full suite, then commit**

Run: `./sbtw "test"`. Expected: all pass.

```bash
git commit -m "docs(batch8): item 14b done" \
  -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
