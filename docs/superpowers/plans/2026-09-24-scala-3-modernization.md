# Scala 3 Modernization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the code base from Scala 2 style to Scala 3 style in four stages (givens, opaque ids, enums, braceless syntax), each landing on `main` as its own `--no-ff` merge with the full gate green.

**Architecture:** Each stage is a branch in its own worktree under `.claude/worktrees/`, built from `main`, merged in order A, B, C, D. Stages A to C are hand edits driven by the compiler under `-Werror`; stage D is two compiler `-rewrite` passes over all four compile targets. Behaviour, JSON spellings, and persisted data never change; the tests that pin them are the safety net.

**Tech Stack:** Scala 3.9.0, sbt 1.11.2 via `./sbtw`, Scala.js 1.22.0, munit, sbt-scoverage 2.4.4, akka-http 10.5.3, ujson, HSQLDB.

**Spec:** `docs/superpowers/specs/2026-09-24-scala-3-modernization-design.md`

## Global Constraints

- Both projects compile under `-Werror`; a single warning fails the build.
- Coverage floor: `coverageMinimumStmtTotal := 86.8` (measured 86.84% before this work).
- Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- Every commit message ends with `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Stage order is fixed: A (`modernize-givens`), B (`modernize-opaque-ids`), C (`modernize-enums`), D (`modernize-syntax`). A stage starts from `main` after the previous stage merged.
- No new compiler flag is added; no formatter is added; `build.sbt` and `project/` are not rewritten.
- Wire spellings (`kind` values, `key` values) and the local database format do not change.
- Never run bare `git stash` (shared stash stack). Never `--amend`, `reset` or `rebase` on `main`: other sessions may move it.

---

## Working conventions

These recipes are repeated in the tasks that use them; this section explains them once.

**Worktree.** From the repo root on `main`:

```bash
git checkout main && git pull --ff-only 2>/dev/null; git log --oneline -1
git worktree add .claude/worktrees/<branch> -b <branch> main
cd .claude/worktrees/<branch>
ln -s ../../../.tooling .tooling
ln -s ../../../node_modules node_modules
```

All `./sbtw` and `python3` commands in a stage run inside that worktree. After the merge, from the repo root: `git worktree remove --force .claude/worktrees/<branch> && git branch -d <branch>`.

**Full gate** (before each stage merge, inside the worktree):

```bash
./sbtw clean coverage test coverageReport frontend/test
./sbtw doc frontend/doc
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
git diff --check
JAVA_HOME="$PWD/.tooling/jdk-21.0.12.1+1/Contents/Home" ./sbtw clean Universal/packageBin
```

Expected: tests pass, coverage at or above 86.8, `doc` prints only `[warn] Option -classpath was updated` twice, the two scripts print `... passed`, `git diff --check` prints nothing, packaging succeeds. The `packageBin` run cleans `target/`, so run it last.

**Merge.** From the repo root:

```bash
git checkout main && git log --oneline -3
```

If `main` still ends at the commit the branch started from, merge. If `main` moved: `cd .claude/worktrees/<branch> && git merge main`; if any moved commit touched a `.scala` file or `build.sbt`, re-run the full gate; if only docs moved, run `./sbtw compile Test/compile frontend/compile` and `python3 scripts/check-markdown-links.py`. Then:

```bash
git checkout main && git merge --no-ff --no-edit <branch>
```

**Rollback.** Each stage is one `--no-ff` merge; `git revert -m 1 <merge-sha>` on `main` undoes it. Stage D is last, so reverting an earlier stage never conflicts with the whole-repo rewrite.

**API snapshot** (stages B and C). The local `var/oathdigital*` database predates the Scala 3 switch and holds two games. The snapshot copies it so the original is never opened by a newer build, starts the server on the copy, and stores the raw event history and one player's load projection per game. `LABEL` is `before` or `after`.

```bash
SCRATCH=/private/tmp/claude-501/-Users-roman-projects-oathdigital/b1b4f917-9fa7-4417-87a5-82abd89b665b/scratchpad
rm -rf "$SCRATCH/db" && mkdir -p "$SCRATCH/db"
cp /Users/roman/projects/oathdigital/var/oathdigital.properties /Users/roman/projects/oathdigital/var/oathdigital.script /Users/roman/projects/oathdigital/var/oathdigital.lobs "$SCRATCH/db/"
./sbtw frontend/fastLinkJS
./sbtw "runMain oathdigital.server.OathServer --mode development --database-path $SCRATCH/db/oathdigital --catalog-path docs/catalog/new-foundations-component-catalog.json"
```

Run the `runMain` command in the background (Bash `run_in_background`), wait until `curl -s http://127.0.0.1:8080/health/ready` answers 200, then:

```bash
LABEL=before   # or after
for g in manual-1790121319027-754372 manual-1790205747051-112090; do
  curl -s "http://127.0.0.1:8080/api/dev/first-games/$g/events?limit=100" > "$SCRATCH/api-$LABEL-$g-events.json"
  p=$(grep -oE '"playerId":"[^"]+"' "$SCRATCH/api-$LABEL-$g-events.json" | head -1 | cut -d'"' -f4)
  curl -s "http://127.0.0.1:8080/api/dev/first-games/$g?playerId=$p" > "$SCRATCH/api-$LABEL-$g-load.json"
done
```

Stop the server (kill the background sbt). The `before` snapshot is taken on `main` at the branch point (the worktree right after creation, before any edit); the `after` snapshot on the finished branch. Compare with:

```bash
for f in "$SCRATCH"/api-before-*.json; do cmp "$f" "${f/before/after}" && echo "same: $f"; done
```

Expected: four `same:` lines. A difference is a stage failure to investigate, not to explain away.

---

## Stage A: givens, extension, top-level definitions

### Task 1: Convert the implicit values and parameters to `given`/`using`

**Files:**
- Modify: `src/main/scala/oathdigital/server/OathServer.scala:39-41`
- Modify: `src/test/scala/oathdigital/server/AuthenticatedGameRoutesSuite.scala:27,126`
- Modify: `src/test/scala/oathdigital/server/ServerRoutesSuite.scala:25,80,119,175-176`
- Modify: `src/test/scala/oathdigital/server/GameRoutesSuite.scala:31`
- Modify: `src/test/scala/oathdigital/server/GameTrustBoundaryRoutesSuite.scala:29`
- Modify: `src/test/scala/oathdigital/server/SessionSecuritySuite.scala:26`
- Modify: `src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala:270,404`
- Modify: `src/test/scala/oathdigital/server/ProductionFrontendRoutesSuite.scala:16,52`
- Modify: `src/test/scala/oathdigital/server/AuthenticatedGameBootstrapRoutesSuite.scala:31`
- Modify: `src/test/scala/oathdigital/server/HealthRoutesSuite.scala:15,73-74`
- Modify: `src/test/scala/oathdigital/persistence/HsqldbDatabaseOwnerSuite.scala:14`
- Modify: `src/test/scala/oathdigital/persistence/HsqldbEventStreamRepositorySuite.scala:21`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: no new names. The values keep their names (`system`, `executionContext`) because the suites call `system.terminate()` and `system.executionContext`.

Note: the spec counted 16 implicit value sites and one implicit parameter list. The grep above finds 18 values and three parameter lists (`ServerRoutesSuite.bind`, `HealthRoutesSuite.bind`, `ProductionFrontendRoutesSuite.bind`). All 21 convert in this task.

- [ ] **Step 1: Create the worktree**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -1
git worktree add .claude/worktrees/modernize-givens -b modernize-givens main
cd .claude/worktrees/modernize-givens
ln -s ../../../.tooling .tooling && ln -s ../../../node_modules node_modules
```

- [ ] **Step 2: Confirm the baseline compiles and the route suites pass**

Run: `./sbtw compile Test/compile 'testOnly oathdigital.server.* oathdigital.persistence.*'`
Expected: all tests pass. This is the green baseline the rewrite must keep.

- [ ] **Step 3: Rewrite the three spellings with sed**

```bash
sed -i '' -E \
  -e 's/implicit private val/private given/' \
  -e 's/implicit val/given/' \
  -e 's/\(implicit/(using/' \
  src/main/scala/oathdigital/server/OathServer.scala \
  src/test/scala/oathdigital/server/AuthenticatedGameRoutesSuite.scala \
  src/test/scala/oathdigital/server/ServerRoutesSuite.scala \
  src/test/scala/oathdigital/server/GameRoutesSuite.scala \
  src/test/scala/oathdigital/server/GameTrustBoundaryRoutesSuite.scala \
  src/test/scala/oathdigital/server/SessionSecuritySuite.scala \
  src/test/scala/oathdigital/server/TrustedSeatRoutesSuite.scala \
  src/test/scala/oathdigital/server/ProductionFrontendRoutesSuite.scala \
  src/test/scala/oathdigital/server/AuthenticatedGameBootstrapRoutesSuite.scala \
  src/test/scala/oathdigital/server/HealthRoutesSuite.scala \
  src/test/scala/oathdigital/persistence/HsqldbDatabaseOwnerSuite.scala \
  src/test/scala/oathdigital/persistence/HsqldbEventStreamRepositorySuite.scala
grep -rn "implicit" --include='*.scala' src frontend/src shared
```

Expected: the grep prints only `src/main/scala/oathdigital/gameplay/actions/Search.scala:44:  private implicit final class TakeThrough...` (converted in Task 2) and nothing else.

The result at `OathServer.scala:39-41` must read:

```scala
    given system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, "oathdigital-server")
    given executionContext: ExecutionContext = system.executionContext
```

and `ServerRoutesSuite.scala:175-177` (and the same shape in `HealthRoutesSuite`):

```scala
  private def bind(route: akka.http.scaladsl.server.Route)(using
      system: ActorSystem[Nothing]
  ) = Await.result(
```

Known semantic: an alias given whose right-hand side is not a simple reference is initialised on first use, like a `lazy val`. Every converted site reads the value in the next statement (`bind(...)`, `system.executionContext`), so initialisation order does not change.

- [ ] **Step 4: Compile and run the affected suites**

Run: `./sbtw compile Test/compile 'testOnly oathdigital.server.* oathdigital.persistence.*'`
Expected: zero warnings, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A src && git commit -m "refactor: declare the actor system and execution context values as givens

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 2: Replace the `TakeThrough` implicit class with an extension method

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/Search.scala:44-50`
- Test: `src/test/scala/oathdigital/gameplay/actions/SearchSuite.scala` (existing; find it with `ls src/test/scala/oathdigital/gameplay/actions/ | grep -i search`)

**Interfaces:**
- Produces: `extension [A](values: Vector[A]) private def takeThrough(stop: A => Boolean): Vector[A]`, private to `Search`. The call at `Search.scala:38` (`.takeThrough(_.isInstanceOf[VisionId])`) does not change.

- [ ] **Step 1: Run the search suites for a green baseline**

Run: `./sbtw 'testOnly oathdigital.gameplay.actions.*Search*'`
Expected: pass.

- [ ] **Step 2: Replace the class**

Replace lines 44-50 of `Search.scala` (the whole `private implicit final class TakeThrough ...` block) with:

```scala
  extension [A](values: Vector[A])
    private def takeThrough(stop: A => Boolean): Vector[A] = {
      val index = values.indexWhere(stop)
      if (index < 0) values else values.take(index + 1)
    }
```

- [ ] **Step 3: Compile and rerun the suites**

Run: `./sbtw compile 'testOnly oathdigital.gameplay.actions.*Search*'` then `grep -rn "implicit" --include='*.scala' src frontend/src shared`
Expected: pass, zero warnings, the grep prints nothing.

- [ ] **Step 4: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/Search.scala
git commit -m "refactor: express takeThrough as an extension method

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 3: Replace the frontend package object with top-level definitions

**Files:**
- Rename: `frontend/src/main/scala/oathdigital/frontend/package.scala` to `frontend/src/main/scala/oathdigital/frontend/frontend.scala`

**Interfaces:**
- Produces: the same `type`/`val` pairs (`GameProjection`, `CardDetails`, ..., `WalkerWaitingState`) and `object MinorAdviserPlacement`, now top level in package `oathdigital.frontend`. Every frontend source already sits in that package and uses the names unqualified; no importer changes.

- [ ] **Step 1: Rename the file**

```bash
git mv frontend/src/main/scala/oathdigital/frontend/package.scala frontend/src/main/scala/oathdigital/frontend/frontend.scala
```

- [ ] **Step 2: Unwrap the object**

Edit `frontend.scala`: replace the first three lines

```scala
package oathdigital

package object frontend {
```

with two package clauses (the second clause keeps `protocol.projection.X` resolving relative to `oathdigital`; a single `package oathdigital.frontend` clause would not):

```scala
package oathdigital
package frontend

```

Delete the final closing `}` of the object, and dedent every remaining line by two spaces:

```bash
python3 - <<'PY'
from pathlib import Path
p = Path("frontend/src/main/scala/oathdigital/frontend/frontend.scala")
lines = p.read_text().splitlines()
assert lines[0] == "package oathdigital" and lines[2] == "package object frontend {" and lines[-1] == "}"
body = [l[2:] if l.startswith("  ") else l for l in lines[3:-1]]
p.write_text("\n".join(["package oathdigital", "package frontend", ""] + body) + "\n")
PY
grep -n "package object" -r frontend/src shared src; echo "exit=$?"
```

Expected: the grep finds nothing. The file's first definitions now read:

```scala
package oathdigital
package frontend

type CampaignResultState = protocol.projection.CampaignResultProjection
val CampaignResultState = protocol.projection.CampaignResultProjection
```

- [ ] **Step 3: Compile and test the frontend**

Run: `./sbtw frontend/compile frontend/test`
Expected: zero warnings, all frontend tests pass.

- [ ] **Step 4: Commit**

```bash
git add -A frontend/src/main/scala/oathdigital/frontend/
git commit -m "refactor: replace the frontend package object with top-level definitions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 4: Stage A gate and merge

**Files:** none new.

- [ ] **Step 1: Run the full gate**

```bash
./sbtw clean coverage test coverageReport frontend/test
./sbtw doc frontend/doc
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
git diff --check
JAVA_HOME="$PWD/.tooling/jdk-21.0.12.1+1/Contents/Home" ./sbtw clean Universal/packageBin
```

Expected: tests pass, coverage at or above 86.8, `doc` prints only the two classpath lines, both scripts pass, packaging succeeds.

- [ ] **Step 2: Merge into main**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -3
```

If `main` moved since the branch point, follow the merge recipe in Working conventions (merge `main` into the branch, re-verify). Then:

```bash
git merge --no-ff --no-edit modernize-givens
git worktree remove --force .claude/worktrees/modernize-givens && git branch -d modernize-givens
git log --oneline -5
```

Expected: a merge commit on `main` with the three stage commits behind it.

---

## Stage B: opaque types for the seven ids

### Task 5: Stage B worktree, pre-checks and `before` snapshot

**Files:** none modified.

- [ ] **Step 1: Create the worktree**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -1
git worktree add .claude/worktrees/modernize-opaque-ids -b modernize-opaque-ids main
cd .claude/worktrees/modernize-opaque-ids
ln -s ../../../.tooling .tooling && ln -s ../../../node_modules node_modules
```

- [ ] **Step 2: Run the spec's pre-grep for id-to-string and id-to-id comparisons**

```bash
I='PlayerId|LineageId|DecisionId|PowerId|DefinitionId|UserId|ViewId'
grep -rnE "($I)\([^)]*\) *[!=]= *\"|\"[^\"]*\" *[!=]= *($I)\(" --include='*.scala' src shared frontend/src
grep -rnE "case [a-zA-Z_]+: ($I)\b|isInstanceOf\[($I)\]|asInstanceOf\[($I)\]|classOf\[($I)\]" --include='*.scala' src shared frontend/src
grep -rnE "\"($I)\(" --include='*.scala' src shared frontend/src
grep -rlwE "$I" --include='*.scala' shared frontend/src
```

Expected: all four greps print nothing (measured on 2026-09-24). A hit in the first two means a site whose meaning changes under opaque types: fix it in this task (compare `.value` to the string, or match on the enclosing type) before continuing. A hit in the third is a test pinning `PlayerId(alice)` text; it is updated when its id converts. The fourth confirms the stage is JVM-only.

- [ ] **Step 3: Take the `before` API snapshot**

Follow the API snapshot recipe with `LABEL=before` (copy `var/oathdigital*` to the scratch `db` directory, `./sbtw frontend/fastLinkJS`, run the server in the background on the copy, curl the four responses, stop the server). Expected: four non-empty JSON files `api-before-*.json`.

### Task 6: Make `PlayerId`, `LineageId`, `DecisionId` and `PowerId` opaque

**Files:**
- Modify: `src/main/scala/oathdigital/model/Identity.scala:8-36`
- Modify: `src/main/scala/oathdigital/catalog/CatalogModel.scala:13-16` (delete the `apply(id: String, ...)` overload)
- Modify: the five `CatalogPower("...", ...)` call sites (`grep -rn 'CatalogPower("' --include='*.scala' src`)
- Create: `src/test/scala/oathdigital/model/OpaqueIdSuite.scala`
- Modify: whatever else the compiler reports (see Step 5)

**Interfaces:**
- Produces, for each of the four ids `XId`: `opaque type XId = String`; `XId.apply(value: String): XId` (same validation as today); `XId.unapply(id: XId): Some[String]`; `extension (id: XId) def value: String`. `PowerId.fromValue(value: String): Option[PowerId]` is kept. Construction `PlayerId("p1")`, `.value`, `case PlayerId(v)`, `Map[PlayerId, _]` keys and `Set[PowerId]` all compile unchanged.

- [ ] **Step 1: Write the failing test**

Create `src/test/scala/oathdigital/model/OpaqueIdSuite.scala`:

```scala
package oathdigital.model

class OpaqueIdSuite extends munit.FunSuite {
  test("ids construct, extract and expose their value") {
    val PlayerId(player) = PlayerId("p1")
    assertEquals(player, "p1")
    assertEquals(LineageId("l1").value, "l1")
    assertEquals(DecisionId("d1").value, "d1")
    assertEquals(PowerId("site.coast").value, "site.coast")
  }

  test("ids reject the values they always rejected") {
    intercept[IllegalArgumentException](PlayerId(" "))
    intercept[IllegalArgumentException](LineageId(""))
    intercept[IllegalArgumentException](DecisionId(""))
    intercept[IllegalArgumentException](PowerId("Not-A-Power"))
    assertEquals(PowerId.fromValue("Not-A-Power"), None)
    assertEquals(PowerId.fromValue("site.coast").map(_.value), Some("site.coast"))
  }

  test("an id prints as its raw string") {
    assertEquals(PlayerId("p1").toString, "p1")
    assertEquals(s"${PowerId("site.coast")}", "site.coast")
  }
}
```

- [ ] **Step 2: Run it to see the third test fail**

Run: `./sbtw 'testOnly oathdigital.model.OpaqueIdSuite'`
Expected: the first two tests pass, the third fails with `PlayerId(p1)` versus `p1`.

- [ ] **Step 3: Rewrite the four definitions**

Replace `Identity.scala:8-36` (from `final case class PlayerId` through the end of `object PowerId`) with:

```scala
opaque type PlayerId = String
object PlayerId {
  def apply(value: String): PlayerId = {
    IdentityValidation.nonBlank("player ID", value)
    value
  }
  def unapply(id: PlayerId): Some[String] = Some(id)
  extension (id: PlayerId) def value: String = id
}

opaque type LineageId = String
object LineageId {
  def apply(value: String): LineageId = {
    IdentityValidation.nonBlank("lineage ID", value)
    value
  }
  def unapply(id: LineageId): Some[String] = Some(id)
  extension (id: LineageId) def value: String = id
}

opaque type DecisionId = String
object DecisionId {
  def apply(value: String): DecisionId = {
    IdentityValidation.nonBlank("decision ID", value)
    value
  }
  def unapply(id: DecisionId): Some[String] = Some(id)
  extension (id: DecisionId) def value: String = id
}

opaque type PowerId = String
object PowerId {
  private val pattern = "[a-z][a-z0-9-]*(\\.[a-z0-9-]+)+"

  def apply(value: String): PowerId = {
    require(value.matches(pattern), s"invalid stable power ID $value")
    value
  }
  def unapply(id: PowerId): Some[String] = Some(id)
  extension (id: PowerId) def value: String = id

  /** Safe parse for untrusted (e.g. wire) input: `None` rather than throwing
   * when `value` does not satisfy the stable power ID shape. */
  def fromValue(value: String): Option[PowerId] =
    if (value.matches(pattern)) Some(value) else None
}
```

- [ ] **Step 4: Remove the `CatalogPower` string overload**

`CatalogPower(id: PowerId, persistent: Boolean, rulesText: String)` gets a synthetic `apply(PowerId, Boolean, String)`; with `PowerId` erased to `String` it collides with the hand-written `apply(id: String, persistent: Boolean, rulesText: String)` at `CatalogModel.scala:14`. Delete that overload (the whole `def apply(id: String, ...)` method; keep `object CatalogPower` if anything else remains, otherwise delete the empty object). Then rewrite each of the five callers:

```bash
grep -rn 'CatalogPower("' --include='*.scala' src
```

from `CatalogPower("some.id", ...)` to `CatalogPower(PowerId("some.id"), ...)`, adding `import oathdigital.model.PowerId` where the file lacks it.

- [ ] **Step 5: Compile and fix what the compiler reports**

Run: `./sbtw compile Test/compile 2>&1 | grep -E "error|warn" | head -80`

Expected error kinds and their fixes, applied by hand at each reported site:

- `double definition ... have the same type after erasure`: two overloads that differ only by an id versus a `String` parameter. Remove or rename the `String` variant and update its callers to construct the id.
- `the type test for XId cannot be checked at runtime` (fatal under `-Werror`): a `case x: PlayerId` or `isInstanceOf`. Match on the enclosing sealed type instead; the pre-grep in Task 5 found none.
- `No given instance of type Ordering[XId]`: add to that id's companion `given Ordering[XId] = Ordering.String` (inside the companion the opaque type is `String`, so this compiles).
- `No ClassTag available for XId`: an `Array[XId]` or a generic API needing a `ClassTag`; replace the array with a `Vector`.
- A test that asserts text containing `PlayerId(` now sees the raw string; update the expected text.

Stop rule from the spec: if the fixes spread over more than about 40 files, or an id turns out to need runtime type dispatch, drop that id from the stage (restore its case class), note the reason in the spec's status line at Task 21, and continue with the rest.

- [ ] **Step 6: Run the whole JVM test suite**

Run: `./sbtw test`
Expected: all pass, including `OpaqueIdSuite`.

- [ ] **Step 7: Commit**

```bash
git add -A src && git commit -m "refactor: make the model ids opaque string types

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 7: Make `DefinitionId` opaque

**Files:**
- Modify: `src/main/scala/oathdigital/catalog/CatalogModel.scala:6-8`
- Modify: `src/test/scala/oathdigital/model/OpaqueIdSuite.scala`

**Interfaces:**
- Produces: `opaque type DefinitionId = String` in package `oathdigital.catalog` with `apply`, `unapply` and `extension (id: DefinitionId) def value: String`, same shape as Task 6.

- [ ] **Step 1: Extend the test**

Add to `OpaqueIdSuite` (and `import oathdigital.catalog.DefinitionId` at the top):

```scala
  test("DefinitionId keeps its validation and prints raw") {
    assertEquals(DefinitionId("denizen.coast").value, "denizen.coast")
    intercept[IllegalArgumentException](DefinitionId(" "))
    assertEquals(DefinitionId("x").toString, "x")
  }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw 'testOnly oathdigital.model.OpaqueIdSuite'`
Expected: the new test fails on `toString`.

- [ ] **Step 3: Rewrite the definition**

Replace `CatalogModel.scala:6-8` with:

```scala
opaque type DefinitionId = String
object DefinitionId {
  def apply(value: String): DefinitionId = {
    require(value.trim.nonEmpty, "catalog definition ID must not be blank")
    value
  }
  def unapply(id: DefinitionId): Some[String] = Some(id)
  extension (id: DefinitionId) def value: String = id
}
```

- [ ] **Step 4: Compile, fix reported sites as in Task 6 Step 5, run the tests**

Run: `./sbtw compile Test/compile test`
Expected: all pass. `DefinitionId` is used in four files, so no fixes are expected.

- [ ] **Step 5: Commit**

```bash
git add -A src && git commit -m "refactor: make DefinitionId an opaque string type

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 8: Make `UserId` opaque

**Files:**
- Modify: `src/main/scala/oathdigital/application/IdentityRepository.scala:3`
- Modify: `src/test/scala/oathdigital/model/OpaqueIdSuite.scala`

**Interfaces:**
- Produces: `opaque type UserId = String` in `oathdigital.application` with `apply` (no validation, as today), `unapply`, `extension (id: UserId) def value: String`.

- [ ] **Step 1: Extend the test**

Add to `OpaqueIdSuite` (with `import oathdigital.application.UserId`):

```scala
  test("UserId wraps any string and prints raw") {
    val UserId(raw) = UserId("u-1")
    assertEquals(raw, "u-1")
    assertEquals(UserId("u-1").toString, "u-1")
  }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw 'testOnly oathdigital.model.OpaqueIdSuite'`
Expected: fails on `toString`.

- [ ] **Step 3: Rewrite the definition**

Replace `IdentityRepository.scala:3` with:

```scala
opaque type UserId = String
object UserId {
  def apply(value: String): UserId = value
  def unapply(id: UserId): Some[String] = Some(id)
  extension (id: UserId) def value: String = id
}
```

- [ ] **Step 4: Compile, fix reported sites as in Task 6 Step 5, run the tests**

Run: `./sbtw compile Test/compile test`
Expected: all pass. `HsqldbIdentityRepository` reads and writes `UserId` through `.value` and `UserId(row.getString(1))`, which both compile unchanged.

- [ ] **Step 5: Commit**

```bash
git add -A src && git commit -m "refactor: make UserId an opaque string type

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 9: Make `ViewId` opaque

**Files:**
- Modify: `src/main/scala/oathdigital/presentation/ViewModel.scala:3-6`
- Modify: `src/test/scala/oathdigital/model/OpaqueIdSuite.scala`

**Interfaces:**
- Produces: `opaque type ViewId = String` in `oathdigital.presentation` with `apply` (blank rejected), `unapply`, `extension (id: ViewId) def value: String`.

- [ ] **Step 1: Extend the test**

Add to `OpaqueIdSuite` (with `import oathdigital.presentation.ViewId`):

```scala
  test("ViewId keeps its validation and prints raw") {
    assertEquals(ViewId("site-1").value, "site-1")
    intercept[IllegalArgumentException](ViewId(""))
    assertEquals(ViewId("site-1").toString, "site-1")
  }
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw 'testOnly oathdigital.model.OpaqueIdSuite'`
Expected: fails on `toString`.

- [ ] **Step 3: Rewrite the definition**

Replace `ViewModel.scala:3-6` (keep the doc comment) with:

```scala
/** Stable, serialization-friendly identity for a presented game object. */
opaque type ViewId = String
object ViewId {
  def apply(value: String): ViewId = {
    require(value.trim.nonEmpty, "view ID must not be blank")
    value
  }
  def unapply(id: ViewId): Some[String] = Some(id)
  extension (id: ViewId) def value: String = id
}
```

- [ ] **Step 4: Compile, fix reported sites as in Task 6 Step 5, run the tests**

Run: `./sbtw compile Test/compile test`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add -A src && git commit -m "refactor: make ViewId an opaque string type

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 10: Stage B gate, API comparison and merge

**Files:** none new.

- [ ] **Step 1: Confirm no case class id remains among the seven**

```bash
grep -rnE "case class (PlayerId|LineageId|DecisionId|PowerId|DefinitionId|UserId|ViewId)\b" --include='*.scala' src
```

Expected: nothing (or only an id dropped under the stop rule, which the commit message and Task 21 record).

- [ ] **Step 2: Run the full gate**

```bash
./sbtw clean coverage test coverageReport frontend/test
./sbtw doc frontend/doc
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
git diff --check
JAVA_HOME="$PWD/.tooling/jdk-21.0.12.1+1/Contents/Home" ./sbtw clean Universal/packageBin
```

Expected: as in Working conventions. If coverage drops below 86.8 (the `unapply` and `value` bodies are new statements that the tests in `OpaqueIdSuite` exercise, so no drop is expected), add a test that exercises the uncovered statement rather than lowering the floor.

- [ ] **Step 3: Take the `after` API snapshot and compare**

Follow the API snapshot recipe with `LABEL=after`, then:

```bash
SCRATCH=/private/tmp/claude-501/-Users-roman-projects-oathdigital/b1b4f917-9fa7-4417-87a5-82abd89b665b/scratchpad
for f in "$SCRATCH"/api-before-*.json; do cmp "$f" "${f/before/after}" && echo "same: $f"; done
```

Expected: four `same:` lines. The server also opened the pre-Scala-3 database copy without error, which is the spec's fixture check.

- [ ] **Step 4: Merge into main**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -3
```

If `main` moved, follow the merge recipe in Working conventions. Then:

```bash
git merge --no-ff --no-edit modernize-opaque-ids
git worktree remove --force .claude/worktrees/modernize-opaque-ids && git branch -d modernize-opaque-ids
```

---

## Stage C: enums for the flat families

Conversion rules, applied in Tasks 12 to 15:

1. Objects with no members: `enum CardDeck { case World, Relic, Edifice, Legacy }`.
2. Objects whose members are constants (`val key`, `val value`, `val isImperial`): the constant becomes an enum parameter, the trait's abstract `def` is deleted, each case passes its value: `enum Suit(val key: String) { case Discord extends Suit("discord") ... }`.
3. Objects with a per-case method body: the method moves into the enum body as one `match` on `this` if it is short; otherwise the family stays sealed and Task 21 records it. No family in the 2026-09-24 scan needs this rule.
4. `extends Product with Serializable` on the trait is dropped (an enum already is both). A trait the family extends is kept: `enum AttackDieFace extends DieFace { ... }`. A `final def` shared by every case stays in the enum body.
5. The companion object stays with every member it had (`all`, `fromKey`, `score`); only the case objects leave it. A companion left empty is deleted.
6. Case names, `import X._` sites and `X.Case` references do not change; enum cases are members of the companion. Doc comments on a case object move onto the enum case.
7. A companion member named `values` collides with the enum's synthetic `values`; rename it to `all` and update its callers.
8. Access modifiers on cases cannot be expressed; a family with modified cases stays sealed unless the whole enum can carry the modifier (`private enum Terrain`).

Families that stay sealed, recorded in Task 21:

- `Phase` in `model/GameState.scala`: `RoundEnd` and `WarExhaustion` are `private[oathdigital]` (rule 8).
- The nine window families in `model/PowerWindow.scala` (`SearchWindow`, `TravelWindow`, `CampaignWindow`, `MusterWindow`, `TradeWindow`, `ForgeWindow`, `RecoverWindow`, `ChallengeWindow`, `OtherWindow`): their cases are declared in `object PowerWindow`, and every `PowerWindow.SearchCost` reference in code and reviewed data depends on that. Moving cases into nine companions breaks rule 6.

The spec's stop rule applies to families beyond these ten: if more than ten further families cannot follow rules 1 to 3, stop the stage for review.

The spec counted 61 flat families from a line-based scan. The hand check for this plan found 49: `AtlasEntry`, `RuleSourceState`, `AuthenticationFailure`, `RepositoryAppendResult` and `ServerConnectionState` carry a case class declared over several lines and are mixed, and `PhaseTransitionRef` and `TriggeredProcedureRef` are flat but were scanned as unclassified. The 49 are listed by task below with their pre-edit line numbers. Edit each file from its last listed family upward so the earlier line numbers stay valid.

### Task 11: Stage C worktree and `before` snapshot

**Files:** none modified.

- [ ] **Step 1: Create the worktree**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -1
git worktree add .claude/worktrees/modernize-enums -b modernize-enums main
cd .claude/worktrees/modernize-enums
ln -s ../../../.tooling .tooling && ln -s ../../../node_modules node_modules
```

- [ ] **Step 2: Take the `before` API snapshot**

Follow the API snapshot recipe with `LABEL=before`. Expected: four non-empty JSON files.

- [ ] **Step 3: Confirm the family list still matches the code**

```bash
python3 - <<'PY'
import re, pathlib
roots = ["src/main/scala", "shared/src/main/scala", "frontend/src/main/scala"]
for p in sorted(q for r in roots for q in pathlib.Path(r).rglob("*.scala")):
    text = p.read_text()
    for m in re.finditer(r'^\s*(?:private(?:\[\w+\])?\s+)?sealed\s+(?:abstract\s+)?(?:trait|class)\s+(\w+)', text, re.M):
        name = m.group(1)
        members = re.findall(r'(case object|case class|object|class|trait)\s+(\w+)[^\n]*?\b(?:extends|with)\s+' + name + r'\b', text, re.S)
        kinds = sorted({k for k, _ in members})
        print(f"{p}: {name}: {kinds} {[n for _, n in members]}")
PY
```

Expected: one line per sealed family. Every family named in Tasks 12 to 15 prints kinds `['case object']` (or `[]` for the two `ProcedureRef` sub-families, whose cases sit in a separate companion). If a family named below now prints `case class` among its kinds, it became mixed since 2026-09-24: skip it and note it for Task 21. If a family not named below prints only `['case object']`, open the file; if it is flat, convert it in the task for its package with rule 1 or 2.

### Task 12: Convert the `model` package families

**Files:**
- Modify: `src/main/scala/oathdigital/model/ActionValues.scala:3-7,17-22`
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala:3-9,50-54`
- Modify: `src/main/scala/oathdigital/model/CardDeck.scala:11-21`
- Modify: `src/main/scala/oathdigital/model/CardIndex.scala:3-21`
- Modify: `src/main/scala/oathdigital/model/Cards.scala:3-13,54-66`
- Modify: `src/main/scala/oathdigital/model/CoreOperations.scala:68-79`
- Modify: `src/main/scala/oathdigital/model/DiceSpec.scala:4-8,18-24`
- Modify: `src/main/scala/oathdigital/model/GameEventProtocol.scala:56-60,67-75`
- Modify: `src/main/scala/oathdigital/model/GameState.scala:90-95,129-135`
- Modify: `src/main/scala/oathdigital/model/OperationReason.scala:6-10`
- Modify: `src/main/scala/oathdigital/model/PlayerColor.scala:10-21`
- Modify: `src/main/scala/oathdigital/model/PowerWindow.scala:3-16,209-213`
- Modify: `src/main/scala/oathdigital/model/ProcedureRef.scala:33-35,41-43,97-118`
- Modify: `src/main/scala/oathdigital/model/Resources.scala:102-124`
- Modify: `src/main/scala/oathdigital/model/RuleFallbackProtocol.scala:6-34`
- Modify: `src/main/scala/oathdigital/model/Setup.scala:24-27`
- Modify: `src/main/scala/oathdigital/model/World.scala:3-16,186-196,219-267`
- Modify: the four callers of `MajorActionType.values` and `ActionKind.values`
- Create: `src/test/scala/oathdigital/model/EnumShapeSuite.scala`

**Interfaces:**
- Produces: 37 `model` families as enums with the same type names, case names and companion members. `MajorActionType.all: Vector[MajorActionType]` and `ActionKind.all: Vector[ActionKind]` replace the two `values` members (rule 7); `ActionKind.fromKey` reads `all`.

- [ ] **Step 1: Write the failing test that pins the observable shape**

Create `src/test/scala/oathdigital/model/EnumShapeSuite.scala`:

```scala
package oathdigital.model

class EnumShapeSuite extends munit.FunSuite {
  test("enum cases keep their names, keys and hand-written order") {
    assertEquals(Suit.Order.toString, "Order")
    assertEquals(Suit.Order.productPrefix, "Order")
    assertEquals(Suit.Order.key, "order")
    assertEquals(Suit.all, Vector(Suit.Discord, Suit.Arcane, Suit.Order,
      Suit.Hearth, Suit.Beast, Suit.Nomad))
    assertEquals(Region.all.map(_.key), Vector("cradle", "provinces", "hinterland"))
    assertEquals(FoundationNumber.IV.value, 4)
    assertEquals(Role.Chancellor.isImperial, true)
    assertEquals(MajorActionType.all.map(_.key).head, "search")
    assertEquals(ActionKind.fromKey("when-played"), Some(ActionKind.WhenPlayed))
    assertEquals(PhaseTransitionRef.EndWake.family, "phase-transition")
    assertEquals(PhaseTransitionRef.EndWake.key, "end-wake")
    assertEquals(TriggeredProcedureRef.all.map(_.key), Vector("oathkeeper", "setup"))
  }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw 'testOnly oathdigital.model.EnumShapeSuite'`
Expected: compile error, `all is not a member of object MajorActionType`.

- [ ] **Step 3: Convert `World.scala` (bottom up)**

Lines 263-267 (`TitleSide`):

```scala
enum TitleSide { case Oathkeeper, Usurper }
```

Lines 252-261 (`OathkeeperGoal`):

```scala
enum OathkeeperGoal(val key: String) {
  case Supremacy extends OathkeeperGoal("supremacy")
  case Protection extends OathkeeperGoal("protection")
  case ThePeople extends OathkeeperGoal("the-people")
  case Devotion extends OathkeeperGoal("devotion")
}
object OathkeeperGoal {
  val all: Vector[OathkeeperGoal] =
    Vector(Supremacy, Protection, ThePeople, Devotion)
}
```

Lines 241-245 (`FoundationFace`):

```scala
enum FoundationFace { case Normal, Altered }
```

Lines 226-239 (`FoundationNumber`):

```scala
enum FoundationNumber(val value: Int) {
  case I extends FoundationNumber(1)
  case II extends FoundationNumber(2)
  case III extends FoundationNumber(3)
  case IV extends FoundationNumber(4)
  case V extends FoundationNumber(5)
  case VI extends FoundationNumber(6)
}
object FoundationNumber {
  val all: Vector[FoundationNumber] = Vector(I, II, III, IV, V, VI)
}
```

Lines 219-225 (`Banner`):

```scala
enum Banner(val key: String) {
  case PeoplesFavor extends Banner("peoples-favor")
  case DarkestSecret extends Banner("darkest-secret")
}
object Banner {
  val all: Vector[Banner] = Vector(PeoplesFavor, DarkestSecret)
  def fromKey(key: String): Option[Banner] = all.find(_.key == key)
}
```

Lines 192-196 (`DarkestSecretFace`) and 186-190 (`PeoplesFavorFace`):

```scala
enum DarkestSecretFace { case WanderingFlame, Festival }
```

```scala
enum PeoplesFavorFace { case Mob, GrandCouncil }
```

Lines 3-16 (`Role`):

```scala
enum Role(val isImperial: Boolean) {
  case Exile extends Role(false)
  case Citizen extends Role(true)
  case Chancellor extends Role(true)
}
```

- [ ] **Step 4: Convert `Setup.scala`, `RuleFallbackProtocol.scala`, `Resources.scala`**

`Setup.scala:24-27`:

```scala
enum FirstGameFoundationProfile { case FixedUnaltered }
```

`RuleFallbackProtocol.scala:27-34` (`RuleTiming`):

```scala
enum RuleTiming(val key: String) {
  case Start extends RuleTiming("start")
  case Persistent extends RuleTiming("persistent")
  case Trigger extends RuleTiming("trigger")
  case BattlePlan extends RuleTiming("battle-plan")
  case Inherent extends RuleTiming("inherent")
}
```

`RuleFallbackProtocol.scala:6-25` (`ActionKind`; keep the doc comment above it):

```scala
enum ActionKind(val key: String) {
  case Travel extends ActionKind("travel")
  case Search extends ActionKind("search")
  case Campaign extends ActionKind("campaign")
  case Muster extends ActionKind("muster")
  case Trade extends ActionKind("trade")
  case Forge extends ActionKind("forge")
  case Recover extends ActionKind("recover")
  case Challenge extends ActionKind("challenge")
  case Wake extends ActionKind("wake")
  case Rest extends ActionKind("rest")
  case WhenPlayed extends ActionKind("when-played")
  case ActionBoundary extends ActionKind("action-boundary")
  case Negotiation extends ActionKind("negotiation")
}
object ActionKind {
  val all: Vector[ActionKind] = Vector(Travel, Search, Campaign, Muster, Trade,
    Forge, Recover, Challenge, Wake, Rest, WhenPlayed, ActionBoundary, Negotiation)
  def fromKey(key: String): Option[ActionKind] = all.find(_.key == key)
}
```

`Resources.scala:102-124` (`Suit`; the doc comment above and `val all` plus everything after it in the companion stay):

```scala
enum Suit(val key: String) {
  case Discord extends Suit("discord")
  case Arcane extends Suit("arcane")
  case Order extends Suit("order")
  case Hearth extends Suit("hearth")
  case Beast extends Suit("beast")
  case Nomad extends Suit("nomad")
}
object Suit {
```

- [ ] **Step 5: Convert `ProcedureRef.scala`**

Lines 112-118 (`object TriggeredProcedureRef`) become:

```scala
object TriggeredProcedureRef {
  val all: Vector[TriggeredProcedureRef] = Vector(Oathkeeper, Setup)
}
```

Lines 97-110 (`object PhaseTransitionRef`) become:

```scala
object PhaseTransitionRef {
  val all: Vector[PhaseTransitionRef] = Vector(EndWake, BeginRest, FinishRest)
}
```

Lines 41-43 (`sealed trait TriggeredProcedureRef`; its doc comment above stays):

```scala
enum TriggeredProcedureRef(val key: String) extends ProcedureRef {
  final def family: String = "triggered"

  /** Every change of the Oathkeeper title holder at an action boundary. */
  case Oathkeeper extends TriggeredProcedureRef("oathkeeper")
  /** Runs once, right after `GameStarted` evolves (2026-09-21 Chronicle
    * design, slice 2, "Setup on the walker"). No client command starts it. */
  case Setup extends TriggeredProcedureRef("setup")
}
```

Lines 33-35 (`sealed trait PhaseTransitionRef`; its doc comment above stays):

```scala
enum PhaseTransitionRef(val key: String) extends StartableRef {
  final def family: String = "phase-transition"

  /** Batch-1 Task 7 moved End Wake here from `ActionRef`: the action
    * boundary follows only a completed `ActionRef`, in whatever phase it
    * ran. End Wake is a phase transition, not something a player spends a
    * turn on, so it runs none.
    */
  case EndWake extends PhaseTransitionRef("end-wake")
  /** Leaves Act for Rest (rest-walker spec, Rest procedure). */
  case BeginRest extends PhaseTransitionRef("begin-rest")
  /** Cleans up, refreshes Supply and hands the turn over. */
  case FinishRest extends PhaseTransitionRef("finish-rest")
}
```

`StartableRef` and `ProcedureRef` are sealed traits in the same file, so an enum may extend them; the enum parameter `key` implements `ProcedureRef.key`.

- [ ] **Step 6: Convert `PowerWindow.scala`, `PlayerColor.scala`, `OperationReason.scala`**

`PowerWindow.scala:209-213`:

```scala
enum PowerResolution { case PlayerSelected, Automatic }
```

`PowerWindow.scala:3-16` (`MajorActionType`):

```scala
enum MajorActionType(val key: String) {
  case Search extends MajorActionType("search")
  case Travel extends MajorActionType("travel")
  case Campaign extends MajorActionType("campaign")
  case Muster extends MajorActionType("muster")
  case Trade extends MajorActionType("trade")
  case Forge extends MajorActionType("forge")
  case Recover extends MajorActionType("recover")
  case Challenge extends MajorActionType("challenge")
}
object MajorActionType {
  val all: Vector[MajorActionType] = Vector(
    Search, Travel, Campaign, Muster, Trade, Forge, Recover, Challenge)
}
```

Do not touch `PowerWindow` or its nine sub-families.

`PlayerColor.scala:10-21` (the companion's `all` and `fromKey` at lines 23-27 stay):

```scala
enum PlayerColor(val key: String) {
  case Purple extends PlayerColor("purple")
  case Red extends PlayerColor("red")
  case Blue extends PlayerColor("blue")
  case Yellow extends PlayerColor("yellow")
  case White extends PlayerColor("white")
  case Black extends PlayerColor("black")
  case Pink extends PlayerColor("pink")
  case Brown extends PlayerColor("brown")
}
object PlayerColor {
```

`OperationReason.scala:6-10`:

```scala
enum OperationReasonKind { case Impossible, Invalid }
```

- [ ] **Step 7: Convert `GameState.scala`, `GameEventProtocol.scala`, `DiceSpec.scala`, `CoreOperations.scala`**

`GameState.scala:129-135` (`VictoryKind`):

```scala
enum VictoryKind(val key: String) {
  case Usurper extends VictoryKind("usurper")
  case Visionary extends VictoryKind("visionary")
  case Oathkeeper extends VictoryKind("oathkeeper")
  case RandomSelection extends VictoryKind("random-selection")
}
```

`GameState.scala:90-95` (`PowerTiming`); `Phase` at lines 66-82 stays as it is:

```scala
enum PowerTiming { case Wake, Act, Rest }
```

`GameEventProtocol.scala:67-75` (`WakeResource`):

```scala
enum WakeResource(val key: String) {
  case Favor extends WakeResource("favor")
  case Secret extends WakeResource("secret")
}
object WakeResource {
  val all: Vector[WakeResource] = Vector(Favor, Secret)

  def fromKey(key: String): Option[WakeResource] = all.find(_.key == key)
}
```

`GameEventProtocol.scala:56-60` (`TradeResource`):

```scala
enum TradeResource { case Favor, Secret }
```

`DiceSpec.scala:18-24` (`RollMode`):

```scala
enum RollMode {
  /** The walker parks and the faces ride a later `RollWalker` command. */
  case Parked
  /** The walker asks its dice source and keeps walking in the same command. */
  case Automatic
}
```

`DiceSpec.scala:4-8` (`DiceKind`):

```scala
enum DiceKind { case Defense, Attack }
```

`CoreOperations.scala:74-79` (`StackPosition`) and 68-72 (`SecretSide`):

```scala
enum StackPosition { case Unspecified, Top, Bottom }
```

```scala
enum SecretSide { case FaceUp, FaceDown }
```

- [ ] **Step 8: Convert `Cards.scala`, `CardIndex.scala`, `CardDeck.scala`, `CampaignTypes.scala`, `ActionValues.scala`**

`Cards.scala:54-66` (`Region`):

```scala
enum Region(val key: String) {
  case Cradle extends Region("cradle")
  case Provinces extends Region("provinces")
  case Hinterland extends Region("hinterland")
}
object Region {
  val all: Vector[Region] = Vector(Cradle, Provinces, Hinterland)
}
```

`Cards.scala:9-13` (`EdificeSide`) and 3-7 (`Orientation`):

```scala
enum EdificeSide { case Intact, Ruined }
```

```scala
enum Orientation { case FaceUp, FaceDown }
```

`CardIndex.scala:17-21`, 9-15, 3-7:

```scala
enum LineageCardArea { case Legacies, StartingAdvisers }
```

```scala
enum PlayerCardArea { case Hand, Advisers, Relics, RevealedVision }
```

```scala
enum SiteCardArea { case Denizens, Relics }
```

`CardDeck.scala:11-21` (the doc comment on `key` moves above the enum; `val all` and `fromKey` at lines 23-26 stay):

```scala
/** `key` is the stable wire spelling, frozen: it is the `Location.Deck`
  * journal tag and the identity half of a `DecisionOptionRef.Deck` on the
  * command wire.
  */
enum CardDeck(val key: String) {
  case World extends CardDeck("world")
  case Relic extends CardDeck("relic")
  case Edifice extends CardDeck("edifice")
  case Legacy extends CardDeck("legacy")
}
object CardDeck {
```

If a doc comment already sits above line 11, merge the two into one comment.

`CampaignTypes.scala:50-54` (`CampaignPlanSide`) and 3-9 (`CampaignKind`):

```scala
enum CampaignPlanSide { case Attacker, Defender }
```

```scala
enum CampaignKind(val key: String) {
  case Conquest extends CampaignKind("conquest")
  case Raid extends CampaignKind("raid")
}
```

`ActionValues.scala:17-22` (`DefenseDieFace`; `score` at lines 24-31 and the closing brace stay):

```scala
enum DefenseDieFace extends DieFace { case Blank, OneShield, TwoShields, Doubler }
object DefenseDieFace {
```

`ActionValues.scala:3-7` (`AttackDieFace`; `score` and `skulls` stay):

```scala
enum AttackDieFace extends DieFace { case HollowSword, OneSword, TwoSwordsSkull }
object AttackDieFace {
```

- [ ] **Step 9: Rename the two `values` callers**

```bash
grep -rlE "MajorActionType\.values|ActionKind\.values" --include='*.scala' src | xargs sed -i '' -E 's/(MajorActionType|ActionKind)\.values/\1.all/g'
grep -rnE "MajorActionType\.values|ActionKind\.values" --include='*.scala' src; echo "remaining: $?"
```

Expected: `remaining: 1` (grep found nothing).

- [ ] **Step 10: Compile and run the JVM tests**

Run: `./sbtw compile Test/compile test`
Expected: zero warnings, all tests pass including `EnumShapeSuite`, `GameEventWireSuite` (pins the JSON `kind`/`key` spellings) and `HsqldbEventStreamRepositorySuite` (persistence round trip). An exhaustivity warning means a case was dropped while converting: restore it. A `double definition` on `values` is rule 7.

- [ ] **Step 11: Commit**

```bash
git add -A src && git commit -m "refactor: convert the flat model families to enums

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 13: Convert the `gameplay` package families

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/RuleSourceIndex.scala:8-23` (`RuleSourceFace`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/CardPlay.scala:14-18` (`Origin`)
- Modify: `src/main/scala/oathdigital/gameplay/actions/cardplay/CardPlayProcedure.scala:11-15` (`Origin`)
- Modify: `src/main/scala/oathdigital/gameplay/powers/travel/TravelSitePowers.scala:138-142` (`Terrain`)

**Interfaces:**
- Produces: the same four type names and case names as enums. `RuleSourceState` in `RuleSourceIndex.scala` is mixed and stays sealed.

- [ ] **Step 1: Convert the four families**

`TravelSitePowers.scala:138-142` (inside `object TravelSitePowers`, rule 8 with the modifier on the enum):

```scala
  private enum Terrain { case Mountain, Island, Coast, NarrowPass }
```

`CardPlayProcedure.scala:11-15` (nested in its enclosing object; keep the two-space indent):

```scala
  enum Origin { case TemporaryHand, FacedownAdviser }
```

`CardPlay.scala:14-18`:

```scala
  enum Origin { case FacedownAdviser, TemporaryHand }
```

`RuleSourceIndex.scala:8-23`:

```scala
enum RuleSourceFace {
  case FaceUp, FaceDown, Intact, Ruined, Printed, Active, Inactive, Mob,
    GrandCouncil, WanderingFlame, Festival, Normal, Altered
}
```

- [ ] **Step 2: Compile and run the gameplay tests**

Run: `./sbtw compile Test/compile 'testOnly oathdigital.gameplay.*'`
Expected: zero warnings, all pass.

- [ ] **Step 3: Commit**

```bash
git add -A src && git commit -m "refactor: convert the flat gameplay families to enums

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 14: Convert the `catalog`, `application` and `server` families

**Files:**
- Modify: `src/main/scala/oathdigital/catalog/CatalogModel.scala:32-45` (`CardRestrictions`, `RelicRole`)
- Modify: `src/main/scala/oathdigital/application/IdentityRepository.scala:46-51` (`MembershipRole`)
- Modify: `src/main/scala/oathdigital/application/TrustedGameStore.scala:3-9` (`TrustedGameStoreFailure`)
- Modify: `src/main/scala/oathdigital/server/HealthRoutes.scala:10-16` (`ReadinessState`)
- Modify: `src/main/scala/oathdigital/server/ServerConfig.scala:9-14` (`ServerMode`)
- Modify: `src/main/scala/oathdigital/server/TrustedGameProvisioning.scala:10-16` (`TrustedGameFailure`)

**Interfaces:**
- Produces: seven enums with the same type and case names. `AuthenticationFailure` and `RepositoryAppendResult` in `application` are mixed and stay sealed. `IdentityRepository.scala` line numbers here are pre-Stage-B; `UserId` grew by five lines in Task 8, so `MembershipRole` sits near line 51.

- [ ] **Step 1: Convert the seven families**

`TrustedGameProvisioning.scala:10-16`:

```scala
enum TrustedGameFailure { case InvalidRequest, DuplicateGame, CodeCollision, StorageFailure }
```

`ServerConfig.scala:9-14`:

```scala
enum ServerMode { case Development, TrustedAlpha }
```

`HealthRoutes.scala:10-16`:

```scala
enum ReadinessState { case Starting, Ready, Stopping }
```

`TrustedGameStore.scala:3-9`:

```scala
enum TrustedGameStoreFailure { case DuplicateGame, CodeCollision, InvalidInput, StorageFailure }
```

`IdentityRepository.scala` (`sealed trait MembershipRole` through its companion's closing brace):

```scala
enum MembershipRole { case Owner, Player, Spectator }
```

`CatalogModel.scala:41-45` (`RelicRole`) and 32-39 (`CardRestrictions`):

```scala
enum RelicRole { case Ordinary, GrandScepter }
```

```scala
enum CardRestrictions { case Unrestricted, Locked, SiteOnly, AdviserOnly, LockedAdviserOnly }
```

- [ ] **Step 2: Compile and run the whole JVM suite**

Run: `./sbtw compile Test/compile test`
Expected: zero warnings, all pass. `HsqldbIdentityRepositorySuite` exercises `MembershipRole` persistence; `ServerRoutesSuite` and `HealthRoutesSuite` cover `ServerMode` and `ReadinessState`.

- [ ] **Step 3: Commit**

```bash
git add -A src && git commit -m "refactor: convert the flat catalog, application and server families to enums

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 15: Convert the `frontend` family

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ModifierWorkflow.scala:5-9` (`ModifierWorkflowStage`)
- Test: `frontend/src/test/scala/oathdigital/frontend/ModifierSelectionStateSuite.scala` (existing)

**Interfaces:**
- Produces: `private[frontend] enum ModifierWorkflowStage { case Ordering, Targets }`; the empty companion is deleted (rule 5). `ServerConnectionState` is mixed (`Disconnected` is a case class) and stays sealed. No family under `shared/src/main/scala` is flat.

- [ ] **Step 1: Convert the family**

Replace `ModifierWorkflow.scala:5-9` with:

```scala
private[frontend] enum ModifierWorkflowStage { case Ordering, Targets }
```

- [ ] **Step 2: Compile and test the frontend**

Run: `./sbtw frontend/compile frontend/test`
Expected: zero warnings, all pass.

- [ ] **Step 3: Commit**

```bash
git add -A frontend && git commit -m "refactor: convert the flat frontend family to an enum

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 16: Stage C gate, API comparison and merge

**Files:** none new.

- [ ] **Step 1: Count what converted and what stayed**

```bash
grep -rnE "^\s*(private(\[\w+\])?\s+)?enum\s+\w+" --include='*.scala' src/main shared frontend/src/main | wc -l
grep -rnE "^\s*(private(\[\w+\])?\s+)?sealed\s+(abstract\s+)?(trait|class)\s+\w+" --include='*.scala' src/main shared frontend/src/main | wc -l
```

Expected: 49 enums (or 49 minus any family skipped in Task 11 Step 3, each with its reason noted). Record both numbers and the names of every family kept sealed for Task 21.

- [ ] **Step 2: Run the full gate**

```bash
./sbtw clean coverage test coverageReport frontend/test
./sbtw doc frontend/doc
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
git diff --check
JAVA_HOME="$PWD/.tooling/jdk-21.0.12.1+1/Contents/Home" ./sbtw clean Universal/packageBin
```

Expected: as in Working conventions. If coverage moves below 86.8, report the exact statement counts from `target/scala-3.9.0/scoverage-report/index.html` before deciding anything; do not lower the floor in this stage.

- [ ] **Step 3: Take the `after` API snapshot and compare**

Follow the API snapshot recipe with `LABEL=after`, then:

```bash
SCRATCH=/private/tmp/claude-501/-Users-roman-projects-oathdigital/b1b4f917-9fa7-4417-87a5-82abd89b665b/scratchpad
for f in "$SCRATCH"/api-before-*.json; do cmp "$f" "${f/before/after}" && echo "same: $f"; done
```

Expected: four `same:` lines and the pre-Scala-3 database copy opened without error.

- [ ] **Step 4: Merge into main**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -3
```

If `main` moved, follow the merge recipe in Working conventions. Then:

```bash
git merge --no-ff --no-edit modernize-enums
git worktree remove --force .claude/worktrees/modernize-enums && git branch -d modernize-enums
```

---

## Stage D: braceless syntax

### Task 17: Indentation rewrite

**Files:**
- Modify: every `.scala` file under `src`, `shared` and `frontend/src` (compiler rewrite)

**Interfaces:**
- Produces: the same code with braces replaced by indentation. No name changes.

- [ ] **Step 1: Create the worktree**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -1
git worktree add .claude/worktrees/modernize-syntax -b modernize-syntax main
cd .claude/worktrees/modernize-syntax
ln -s ../../../.tooling .tooling && ln -s ../../../node_modules node_modules
```

- [ ] **Step 2: Run the rewrite over all four compile targets**

`clean` is required: the compiler rewrites only the sources it compiles, and an incremental build would skip most files.

```bash
./sbtw 'set root / scalacOptions ++= Seq("-indent", "-rewrite")' 'set frontend / scalacOptions ++= Seq("-indent", "-rewrite")' clean compile Test/compile frontend/compile frontend/Test/compile
git diff --stat | tail -1
```

Expected: the compile succeeds and the diff touches most Scala files. (`set every scalacOptions` produced a cyclic-reference error in this build during the Scala 3 switch; the two per-project `set` commands are the working form.)

- [ ] **Step 3: Confirm the rewrite is complete and idempotent**

```bash
./sbtw clean compile Test/compile frontend/compile frontend/Test/compile
./sbtw 'set root / scalacOptions ++= Seq("-indent", "-rewrite")' 'set frontend / scalacOptions ++= Seq("-indent", "-rewrite")' clean compile Test/compile frontend/compile frontend/Test/compile
git status --porcelain | wc -l
```

Expected: the plain compile has zero warnings, and the second rewrite pass changes nothing more than the first (the file count printed after it equals the count after Step 2; check with `git diff --stat | tail -1` again).

- [ ] **Step 4: Review the diff for the two known rewriter artefacts**

```bash
git diff -U0 | grep -E "^\+" | awk 'length > 100' | head -40
git diff -U0 | grep -E "^\+.*\}\s*//" | head
```

Fix by hand, in the same commit: lines over about 100 characters that appeared because a closing brace used to hold a wrapped expression (re-wrap them), and comments that sat on a brace line and now dangle (move the comment to the line above the code it describes). Also skim `git diff --stat` for any file the compiler did not rewrite (a Scala file with zero changes that still contains `{` on a definition line) and rerun Step 2 if one is found.

- [ ] **Step 5: Run the tests and checks**

Run: `./sbtw clean test frontend/test && python3 scripts/check-architecture.py && git diff --check`
Expected: all pass, no whitespace errors.

- [ ] **Step 6: Commit**

```bash
git add -A src shared frontend/src
git commit -m "style: rewrite braces to indentation syntax

Mechanical compiler rewrite (-indent -rewrite) over both projects, main and
test sources; listed in .git-blame-ignore-revs.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 18: New control syntax rewrite

**Files:**
- Modify: every `.scala` file with `if (`, `while (` or `for (...) {` (compiler rewrite)

**Interfaces:**
- Produces: `if x then`, `while c do`, `for ... do` / `for ... yield` forms. No name changes.

- [ ] **Step 1: Run the rewrite**

```bash
./sbtw 'set root / scalacOptions ++= Seq("-new-syntax", "-rewrite")' 'set frontend / scalacOptions ++= Seq("-new-syntax", "-rewrite")' clean compile Test/compile frontend/compile frontend/Test/compile
git diff --stat | tail -1
```

Expected: compile succeeds; the diff touches the files that had parenthesised conditions.

- [ ] **Step 2: Confirm completeness and idempotence**

```bash
./sbtw clean compile Test/compile frontend/compile frontend/Test/compile
./sbtw 'set root / scalacOptions ++= Seq("-new-syntax", "-rewrite")' 'set frontend / scalacOptions ++= Seq("-new-syntax", "-rewrite")' clean compile Test/compile frontend/compile frontend/Test/compile
git diff --stat | tail -1
grep -rnE "^\s*(if|while) \(" --include='*.scala' src shared frontend/src | head
```

Expected: zero warnings on the plain compile, the same diff total after the second pass, and the grep prints nothing (a remaining hit is a line the rewriter skipped; rewrite it by hand to the `then`/`do` form).

- [ ] **Step 3: Review for the same artefacts as Task 17 Step 4 and fix them in this commit**

```bash
git diff -U0 | grep -E "^\+" | awk 'length > 100' | head -40
```

- [ ] **Step 4: Run the tests and checks**

Run: `./sbtw clean test frontend/test && python3 scripts/check-architecture.py && git diff --check`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add -A src shared frontend/src
git commit -m "style: rewrite control expressions to the new syntax

Mechanical compiler rewrite (-new-syntax -rewrite) over both projects, main
and test sources; listed in .git-blame-ignore-revs.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 19: Blame ignore file and README note

**Files:**
- Create: `.git-blame-ignore-revs`
- Modify: `README.md:23-40` (the "Build and verification" subsection)

**Interfaces:**
- Produces: `.git-blame-ignore-revs` listing the two Task 17 and Task 18 commit SHAs.

- [ ] **Step 1: Write the ignore file with the two SHAs**

```bash
printf '# Mechanical Scala 3 syntax rewrites; see docs/superpowers/specs/2026-09-24-scala-3-modernization-design.md\n%s\n%s\n' \
  "$(git log --format=%H --grep='rewrite braces to indentation syntax' -1)" \
  "$(git log --format=%H --grep='rewrite control expressions to the new syntax' -1)" > .git-blame-ignore-revs
cat .git-blame-ignore-revs
```

Expected: a comment line and two 40-character SHAs.

- [ ] **Step 2: Add the setup line to the README**

In `README.md`, after the paragraph that ends "The catalog generator without `--output` is a non-writing equality check." (line 40), insert:

````markdown

Two commits rewrote the whole code base to Scala 3 indentation and control
syntax. They are listed in `.git-blame-ignore-revs`; run this once so
`git blame` looks through them:

```sh
git config blame.ignoreRevsFile .git-blame-ignore-revs
```
````

- [ ] **Step 3: Verify blame skips the rewrites**

```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
git blame -L 1,5 src/main/scala/oathdigital/model/Identity.scala | cut -c1-9 | sort -u
python3 scripts/check-markdown-links.py
```

Expected: none of the printed short SHAs is a prefix of either rewrite commit; the link check passes.

- [ ] **Step 4: Commit**

```bash
git add .git-blame-ignore-revs README.md
git commit -m "chore: ignore the syntax rewrite commits in git blame

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 20: Stage D gate and merge

**Files:** none new.

- [ ] **Step 1: Run the full gate**

```bash
./sbtw clean coverage test coverageReport frontend/test
./sbtw doc frontend/doc
python3 scripts/check-architecture.py
python3 scripts/check-markdown-links.py
git diff --check
JAVA_HOME="$PWD/.tooling/jdk-21.0.12.1+1/Contents/Home" ./sbtw clean Universal/packageBin
```

Expected: as in Working conventions. Coverage is statement based and a syntax rewrite does not add or remove statements, so the figure should match the post-Stage-C value.

- [ ] **Step 2: Merge into main**

```bash
cd /Users/roman/projects/oathdigital && git checkout main && git log --oneline -3
```

If `main` moved with Scala changes, merge `main` into the branch first; expect conflicts in any file both sides touched, resolve them in the new syntax, and re-run the full gate. Then:

```bash
git merge --no-ff --no-edit modernize-syntax
git worktree remove --force .claude/worktrees/modernize-syntax && git branch -d modernize-syntax
```

---

## Close-out

### Task 21: Update the spec status line

**Files:**
- Modify: `docs/superpowers/specs/2026-09-24-scala-3-modernization-design.md:4` and the "Done when" section.

- [ ] **Step 1: Gather the counts**

```bash
cd /Users/roman/projects/oathdigital && git checkout main
grep -rnc "given " --include='*.scala' src/main/scala/oathdigital/server/OathServer.scala src/test/scala/oathdigital/server src/test/scala/oathdigital/persistence | awk -F: '{s+=$2} END {print "givens:", s}'
grep -rnE "^opaque type" --include='*.scala' src | wc -l
grep -rnE "^\s*(private(\[\w+\])?\s+)?enum\s+\w+" --include='*.scala' src/main shared frontend/src/main | wc -l
```

- [ ] **Step 2: Replace the status line**

Change line 4 from `Status: approved design, not yet planned or implemented` to one line of the form:

```markdown
Status: implemented 2026-MM-DD. Stage A: N givens/using sites, 1 extension, 1 package object. Stage B: N of 7 ids opaque (dropped: none). Stage C: N of 49 families converted, kept sealed: Phase (private cases), the nine PowerWindow sub-families (cases live in the parent companion), <any others with reason>. Stage D: both rewrites, SHAs in .git-blame-ignore-revs.
```

with the real numbers and any family or id kept back, each with its reason. Also update the "Starting point" bullet that says `main` is quiet if another session committed during the work.

- [ ] **Step 3: Commit on main**

```bash
python3 scripts/check-markdown-links.py
git add docs/superpowers/specs/2026-09-24-scala-3-modernization-design.md
git commit -m "docs: record the Scala 3 modernization outcome in the design status

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
