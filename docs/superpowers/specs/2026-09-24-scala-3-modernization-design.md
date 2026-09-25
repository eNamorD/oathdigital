# Scala 3 modernization — design

Date: 2026-09-24
Status: implemented 2026-09-24. Stage A: 21 implicit sites converted to
given/using (18 `implicit val` values, 3 `(implicit ...)` parameter
lists), 1 extension, 1 package object. Stage B: 7 of 7 ids opaque
(dropped: none). Stage C: 49 of 49 flat families converted, kept sealed:
`Phase` (two `private[oathdigital]` cases), the nine `PowerWindow`
sub-families (`SearchWindow`, `TravelWindow`, `CampaignWindow`,
`MusterWindow`, `TradeWindow`, `ForgeWindow`, `RecoverWindow`,
`ChallengeWindow`, `OtherWindow`; cases live in the parent companion).
Stage D: both rewrites, SHAs in `.git-blame-ignore-revs`.

## Goal

Move the code base from Scala 2 style to Scala 3 style in four stages,
each landing on `main` as its own merge with the full gate green. The
compiler switch (2026-09-24 Scala 3 migration design) and the warning
cleanup that followed are done; this spec covers everything that design
deferred, minus the parts ruled out below.

## Decisions

| Topic | Decision |
| --- | --- |
| Execution | One spec, one branch per stage, merged in order A, B, C, D |
| A: implicits | `given`/`using` for the 16 implicit values, one extension method, top-level definitions replacing the frontend package object |
| B: opaque types | The 7 standalone id wrappers; the 6 card and site ids stay case classes |
| C: enums | The 61 flat sealed families (case objects only); mixed and parameterized families stay sealed traits |
| D: syntax | Both compiler rewrites: `-indent` and `-new-syntax` |
| Enforcement | No new compiler flag; `-Werror` from the warning cleanup remains the only gate on style |

Out of scope: redesigning the `CardId`/`WorldCardId`/`ComponentId`
hierarchy so those ids can be opaque; converting mixed families to
parameterized enums; `-source future`; a code formatter.

## Starting point

Measured on 2026-09-24, after the warning cleanup merge (`d832f08c`):

- Scala 3.9.0, sbt 1.11.2, Scala.js 1.22.0, `-Werror` in both projects,
  coverage floor 86.8 against a measured 86.84%.
- 669 Scala files, about 84,000 lines, across `src`, `frontend/src` and
  `shared`.
- Implicits: 16 `implicit val` or `implicit` parameter sites, all
  `ActorSystem[Nothing]` or `ExecutionContext` values in `OathServer` and
  the server route suites; one `private implicit final class
  TakeThrough` in `gameplay/actions/Search.scala`.
- One package object, `frontend/src/main/scala/oathdigital/frontend/package.scala`,
  holding `type`/`val` alias pairs for protocol projection types.
- Sealed families: 148. 61 have only case objects as direct members, 26
  mix objects and case classes, 36 have only case classes, and 25 could
  not be classified by pattern because their members are declared over
  several lines or nested.
- The hand check done during Stage C found 49 flat families, not 61,
  against which the stage was measured: five (`AtlasEntry`,
  `RuleSourceState`, `AuthenticationFailure`, `RepositoryAppendResult`,
  `ServerConnectionState`) are mixed rather than flat, and two
  (`PhaseTransitionRef`, `TriggeredProcedureRef`) had been scanned as
  unclassified rather than flat.
- Id wrappers `final case class XId(value: String)`: 13. `PlayerId`,
  `LineageId`, `DecisionId`, `PowerId` (in `model/Identity.scala`),
  `DefinitionId` (`catalog/CatalogModel.scala`), `UserId`
  (`application/IdentityRepository.scala`) and `ViewId`
  (`presentation/ViewModel.scala`) stand alone. `DenizenId`, `VisionId`
  (`WorldCardId`), `EdificeId`, `LegacyId`, `RelicId` (`CardId`) and
  `SiteId` (`ComponentId`) extend sealed hierarchies that carry dispatch,
  and an opaque type cannot extend a trait.
- No scalafmt or other formatter in the build.
- `main` was not quiet during the design phase: commit `b0b30274`
  landed from another session before this plan started. No other
  session committed to `main` during the four stages.

## Stage A: givens, extension, top-level definitions

Branch `modernize-givens`, three commits.

1. Every `implicit val x: T = ...` becomes `given x: T = ...`. Givens
   are named because code reads `system.executionContext` and
   `system.terminate()`. The one `(implicit system: ActorSystem[Nothing])`
   parameter list in `ProductionFrontendRoutesSuite` becomes
   `(using system: ActorSystem[Nothing])`. Call sites already pass
   arguments with `using` since the warning cleanup.
2. `TakeThrough` becomes
   `extension [A](values: Vector[A]) def takeThrough(...)` with the same
   body; call sites do not change.
3. `package object frontend` becomes top-level `type` and `val`
   definitions under `package oathdigital.frontend`, in the same file
   renamed to `frontend.scala`. Importers do not change:
   `oathdigital.frontend.GameProjection` resolves as before.

Gate: the full gate below. No coverage change is expected.

## Stage B: opaque types for the seven ids

Branch `modernize-opaque-ids`, one commit per file touched by a
definition (`Identity.scala`, `CatalogModel.scala`,
`IdentityRepository.scala`, `ViewModel.scala`) plus the call-site fixes
the compiler demands.

Shape, shown for `PlayerId`:

```scala
opaque type PlayerId = String
object PlayerId {
  def apply(value: String): PlayerId = {
    IdentityValidation.nonBlank("player ID", value); value
  }
  def unapply(id: PlayerId): Some[String] = Some(id)
  extension (id: PlayerId) def value: String = id
}
```

`LineageId`, `DecisionId`, `DefinitionId`, `UserId` and `ViewId` follow
the same shape with their own validation. `PowerId` keeps its regex
`require` in `apply` and its `fromValue` safe parser. Construction,
`.value`, `case PlayerId(v)` extraction and use as `Map` keys compile
unchanged. Every other site the compiler reports is fixed by hand,
expected kinds:

- `Ordering[XId]` for `sortBy`/`sorted`: a `given Ordering[XId] =
  Ordering.String` in the companion when needed.
- `case x: XId` type tests erase to `String` and now raise an unchecked
  warning, an error under `-Werror`; the match is rewritten on the
  enclosing type instead.

Two behaviour changes, accepted:

- `toString` of an id is the raw string: `alice`, not `PlayerId(alice)`.
  Interpolations of a bare id change their text; `.value` sites do not.
  A test pinning the old text is updated to the new text.
- `PlayerId("a") == "a"` and `PlayerId("a") == PowerId("a")` compile
  today and are false; they are true at runtime after the change. Before
  the switch, `grep` for `==` and `!=` between an id and a string or
  another id type must find zero sites.

Stop rule: if compiler errors exceed about 40 files, or an id turns out
to need runtime type dispatch, that id is dropped from the stage and the
spec records why.

Gate: the full gate below, plus the 2.13-era database fixture opens and
`/api` JSON is byte-identical; ids are strings on the wire either way.

## Stage C: enums for the flat families

Branch `modernize-enums`, one commit per package group (`model`;
`gameplay`; `catalog`, `persistence` and `server`; `frontend` and
`shared/protocol`), full test run after each commit.

Conversion rule per family:

- Objects with no members: `enum Phase { case Wake, Act, Rest }`.
- Objects whose members are constants: enum parameters.
  `case object Order extends Suit { val key = "order" }` becomes
  `enum Suit(val key: String) { case Order extends Suit("order") }`.
- Objects with method bodies that differ per case: the method moves into
  the enum body as one `match` over `this` if it is short. Otherwise the
  family stays a sealed trait and the spec's done-when list names it.

Kept verbatim, so nothing else changes:

- Companion members such as `all`, `fromKey` and orderings. `values`
  comes for free but replaces nothing, because a hand-written `all` may
  carry a deliberate order.
- Case names, hence `productPrefix`, `toString` and every `key`-based
  codec spelling.
- `import Suit._` and `Suit.Order` references; enum cases are members of
  the companion.

The 25 unclassified families are classified by hand during the stage and
converted under the same rule when flat.

Stop rule: a family needing more than a parameter list and one short
match stays sealed. If more than 10 of the 61 hit that rule, the stage
stops for review, because the rule itself is then wrong.

Gate: the full gate, plus `GameEventWireSuite` and the persistence round
trip pin every JSON spelling, the 2.13-era database opens, and
`frontend/test` covers the two frontend families.

## Stage D: braceless syntax

Branch `modernize-syntax`, two commits, each one compiler pass over the
four compile targets (root and frontend, main and test):

1. `-indent -rewrite`: braces to indentation.
2. `-new-syntax -rewrite`: `if x then`, `while c do`, `for ... do`.

The compiler rewrites only sources it compiles, so `build.sbt` and
`project/` (sbt's own Scala 2.12) are untouched. After each pass: compile
without the rewrite flags, run the full gate, and review the diff for two
known rewriter artefacts, fixed by hand in the same commit: over-long
lines where a closing brace held a wrapped expression, and comments that
sat on a brace line. In practice the `-new-syntax` rewriter also emitted
unparsable `for` comprehensions at some `case ... => for {` and
off-column `yield` sites; the 37 such layouts were fixed by hand in the
same commit.

`.git-blame-ignore-revs` lists both commit SHAs; the README developer
section gains the one-line `git config blame.ignoreRevsFile
.git-blame-ignore-revs` setup.

Stage D runs last so the whole-repo rewrite lands on finished code and no
earlier stage has to be redone on top of it.

## Full gate

Before each stage's merge:

- `./sbtw clean coverage test coverageReport frontend/test` with the
  coverage floor at 86.8
- `./sbtw doc frontend/doc` with no warning other than scaladoc's own
  `Option -classpath was updated`
- `python3 scripts/check-architecture.py`
- `python3 scripts/check-markdown-links.py`
- `git diff --check`
- `JAVA_HOME=<jdk-21> ./sbtw clean Universal/packageBin`

Stages B and C additionally open the 2.13-era HSQLDB fixture and compare
`/api` JSON with the pre-stage output.

## Rollback

Each stage is one `--no-ff` merge into `main`; `git revert -m 1` undoes
it. Because stage D is last, reverting an earlier stage never conflicts
with the whole-repo rewrite.

## Done when

- The four merges are on `main` and the full gate is green after the
  last one.
- This spec's status line is updated with per-stage counts: givens
  converted, ids made opaque (and any dropped, with the reason), families
  converted, and families kept sealed with the reason for each.
