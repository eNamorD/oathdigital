# Catalog Batch 8, Slice 7 (Trade at Another Site) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Small Friends (177), Friendly Familiar (224) and Secret Signal (55).

**Architecture:**

- **An as-if site.** A modifier pick may now be followed by its own questions inside the modifier loop, in the same pass, after its payment (`ContributingPower.selectionQuestions`).
  - Small Friends and Friendly Familiar ask which site their user acts as if at, under one shared decision, `PowerAccess.asIfDecision`.
  - `PowerAccess` reads that answer. The as-if site replaces the pawn site in `usableSites`, so later modifier passes offer the new site's cards. Ruled sites, advisers and relics stay usable. The physical pawn site does not, unless the player rules it.
  - `EconomySources.relocated` replaces the Trade's source options with the new site's cards. Skilled Merchants' and Map Library's "your region" becomes the new site's region.
- **"Start" is judged with the walk's answers.** The dry runs that decide whether "Start" is available begin with the pass's answers (`ProcedureWalker.advance`'s new `seed`). Without that, a Trade whose pawn site has nothing to trade with would show "Start" unavailable even after the player chose a site that has a card.
- **The modifier decision names the start selection.** `ModifierChoice` implements a new `ModifierHost`, which carries what the player selected when the action started. Secret Signal reads it to tell a Trade for secrets.
- **Secret Signal** records every favor its trader gains during the Trade under one deferral key, `TradeFavor.key`: the Trade's own gain, and Moving Market's. At the Trade's end, when the sum is exactly one, it gains one more favor from the bank of the Trade's own gain.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "Goal and scope";
  - "The bar" (the slice 7 bullet);
  - "Slice 7: Trade at another site";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 7 entries.

Task 1 amends both with this plan's rulings. Read them again after Task 1.

**Starts from:** `main` with slice 6 (operation triggers) done, at `69d52bac` or later. Slice 6 registered powers in `WalkerPowerCatalog` and added two ids to `PowerKindsCatalogSuite`. This plan touches neither of those lines.

Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it.
  - Each new file below lists the imports it needs.
  - When a card moves out of a holding file, remove any import that file no longer uses (for example `AdviserOnly` or `Cost`).
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). `ProcedureWalker.scala` is at 729 lines and gains about 4. Check with `wc -l`.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources, comments included.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Tree addresses.** No procedure tree gains, loses or moves a node. The site question lives inside the pass's existing payment `Branch`, so the pass keeps its two children.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`. An untracked `src/test/scala/oathdigital/gameplay/HiddenScopeProbeSuite.scala` belongs to another session; leave it alone.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Names, exactly:**
  - In `oathdigital.gameplay.PowerAccess`:
    - `val asIfDecision: String = "modifier.as-if-site"`;
    - `def asIfSite(answered: Vector[Answered]): Option[SiteId]`;
    - `def presentSite(ready: ReadyGame, actor: PlayerId, answered: Vector[Answered]): Option[SiteId]`;
    - `locate` gains a last parameter, `answered: Vector[Answered] = Vector.empty`.
  - `ContributingPower.selectionQuestions(ready: ReadyGame, actor: PlayerId): Vector[Operation]`, empty by default.
  - In `oathdigital.gameplay.powerresolver`, file `ModifierHost.scala`: `trait ModifierHost extends Operation` with `def startSelection: Vector[DecisionOptionRef]`.
  - `ProcedureWalker.advance` gains a last parameter, `seed: Vector[Answered] = Vector.empty`.
  - `WalkerSimulation.preview` and `WalkerSimulation.refusal` gain a last parameter, `answered: Vector[Answered] = Vector.empty`.
  - `SelectedModifier.selectable(ready: ReadyGame, actor: PlayerId, answered: Vector[Answered]): Boolean`.
  - `ModifierChoice.loop` gains a last parameter, `selection: Vector[DecisionOptionRef] = Vector.empty`. `EconomyTree.Kind` gains a last field, `selection: Vector[DecisionOptionRef] = Vector.empty`.
  - In `EconomySources`:
    - `val RelocationPriority: Int = -1`;
    - `def relocated(catalog: ExecutableCatalog, ctx: PowerCtx, operations: Vector[Operation]): Vector[Operation]`;
    - `def cardsAt(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId): Vector[SiteDenizenState]`;
    - `inPawnRegion` gains a last parameter, `answered: Vector[Answered]`.
  - In `oathdigital.gameplay.powers.economy`:
    - file `ActingSite.scala`: `trait ActingSite extends SelectedModifier` and `object ActingSite`;
    - file `SmallFriends.scala`: `SmallFriendsCard`, moved out of `HoldingBeastDenizens`, and `final case class SmallFriends`;
    - file `FriendlyFamiliar.scala`: `FriendlyFamiliarCard`, moved out of `HoldingDiscordDenizens`, and `final case class FriendlyFamiliar`;
    - file `TradeFavor.scala`: `private[economy] object TradeFavor` with `key = DeferralKey("trade.favor-gained")` and `record`;
    - file `SecretSignal.scala`: `SecretSignalCard`, moved out of `HoldingArcaneDenizens`, and `final case class SecretSignal`.
  - Power ids are unchanged: `denizen.small-friends`, `denizen.friendly-familiar` and `denizen.secret-signal`.
  - Note keys: `ActingSite.acted` (`"acted"`) and `SecretSignal.gained` (`"gained"`).
- **Texts, exactly:**
  - Small Friends' question: heading "Small Friends: act as if at which site?".
  - Friendly Familiar's question: heading "Friendly Familiar: act as if at which site?".
  - Their line: "Acted as if at {the Shrine}." (`NoteArg.Site`).
  - Their unavailable reasons:
    - "Already acting as if at another site";
    - "A modifier you picked is at your pawn site";
    - Small Friends: "No other site has a Beast card";
    - Friendly Familiar: "No other site has a card to trade with".
  - Secret Signal's line: "Gained 1 more favor.", covering the generic gain line.
  - Secret Signal's unavailable reason: "A Trade for secrets gains no favor".
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - This plan adds 16 server tests and no frontend test.
  - It changes one existing expectation: `PowerKindsCatalogSuite`'s modifiers gain the three new ids.
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

Agreed with the product owner on 2026-10-14. Task 1 writes them into the design and the rulings appendix.

1. **"Start" sees the walk's answers.** The dry runs that judge "Start" begin with the pass's answers, so they see the as-if site as the walk will. This adds to the design's approved engine changes.
2. **Pick order.**
   - Once a picked modifier's card stands at the pawn site, and the player does not rule that site, Small Friends and Friendly Familiar are unavailable: "A modifier you picked is at your pawn site".
   - Once either is picked and its site answered, the other is unavailable: "Already acting as if at another site".
3. **The sites offered.**
   - Small Friends: the sites in play, other than the pawn site, that hold a card of the Beast suit.
   - Friendly Familiar: the sites in play, other than the pawn site, that hold a card a Trade can draw on (a card with a suit).
   - With no such site, the card is unavailable: "No other site has a Beast card" or "No other site has a card to trade with".
   - The question comes right after the pick and its payment, in the same pass. It has no Skip.
4. **Rules that read the pawn site follow the as-if site.** Skilled Merchants' and Map Library's region is the as-if site's region. Map Library's "while your pawn is at this site" counts the as-if site, so Map Library there is selectable.
5. **Secret Signal's whole favor gain.**
   - It resolves at the Trade's end, after Moving Market.
   - It sums every favor its trader gained in the Trade: the Trade's own gain and Moving Market's.
   - Moving Market's gain is counted at what the Nomad bank could give, so an empty bank counts 0. The Trade's own gain needs no such cut: when its bank runs short, that bank is empty, and Secret Signal's favor comes from it too.
   - When the sum is exactly one, the trader gains one more favor from the bank of the Trade's own gain.
6. **Secret Signal's availability.** It is unavailable only for a Trade for secrets: "A Trade for secrets gains no favor". In a Trade for favor it is always available. When the sum is not one, it does nothing and writes no line. (The source is chosen after the modifiers, so "gains more than one" cannot be known when it is picked.)
7. **Lines.**
   - Small Friends and Friendly Familiar: "Acted as if at {the Shrine}." after the site question. The start line already names the card.
   - Secret Signal: "Gained 1 more favor.", covering the generic gain line. No line when it does not apply.

## File map

- Modify:
  - `src/main/scala/oathdigital/gameplay/PowerAccess.scala`: the as-if site.
  - `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`: `selectionQuestions`.
  - `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala`: answers to `locate`, the questions after a pick, the seeded dry runs, `ModifierHost`.
  - `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`: `advance`'s `seed`.
  - `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`: `preview` and `refusal` take answers.
  - `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`: `Kind.selection`.
  - `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala`: passes its selection.
  - `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`: `selectable` takes answers.
  - `src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala`, `MapLibrary.scala`, `SkilledMerchants.scala`, `EconomySources.scala`.
  - `src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala`: records its favor.
  - `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`: registers the three powers.
  - `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`, `HoldingDiscordDenizens.scala`, `HoldingArcaneDenizens.scala`: the cards move out.
  - `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`: imports the moved cards.
  - `src/test/scala/oathdigital/gameplay/PowerAccessSuite.scala`: 2 tests.
  - `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`: 3 ids.
  - Docs: the design, the rulings appendix, `docs/ROADMAP.md`.
- Create:
  - `src/main/scala/oathdigital/gameplay/powerresolver/ModifierHost.scala`
  - `src/main/scala/oathdigital/gameplay/powers/economy/ActingSite.scala`
  - `src/main/scala/oathdigital/gameplay/powers/economy/SmallFriends.scala`
  - `src/main/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliar.scala`
  - `src/main/scala/oathdigital/gameplay/powers/economy/TradeFavor.scala`
  - `src/main/scala/oathdigital/gameplay/powers/economy/SecretSignal.scala`
  - `src/test/scala/oathdigital/gameplay/powers/economy/SmallFriendsSuite.scala`: 6 tests.
  - `src/test/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliarSuite.scala`: 4 tests.
  - `src/test/scala/oathdigital/gameplay/powers/economy/SecretSignalSuite.scala`: 4 tests.

---

### Task 1: Write the plan-time rulings into the design and the rulings appendix

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

- [ ] **Step 1: Replace the slice 7 bullet of "The bar"**

In the design, replace the whole bullet that starts `- **An as-if pawn site for Trade** (slice 7).` with:

```markdown
- **An as-if pawn site for Trade** (slice 7, detailed at plan time). A
  modifier pick may be followed by its own questions inside the modifier
  loop, in the same pass, after its payment
  (`ContributingPower.selectionQuestions`). Small Friends and Friendly
  Familiar ask for a site under one shared decision,
  `PowerAccess.asIfDecision`. `PowerAccess.locate` reads the walk's
  answers: the as-if site replaces the pawn site in `usableSites`. Ruled
  sites and the player's advisers and relics stay usable; the physical
  pawn site does not, unless the player rules it. `ModifierChoice` passes
  its answers to `locate`, and `SelectedModifier.selectable` and its
  overrides receive them. `EconomySources.relocated`, a sibling of
  `widened`, replaces the Trade source options with the new site's cards.
- **"Start" judged with the walk's answers** (slice 7, approved
  2026-10-14). The dry runs that judge a modifier pass's "Start" begin
  with the pass's answers (`ProcedureWalker.advance`'s `seed`), so they see
  the as-if site as the walk will.
- **The modifier decision names its start selection** (slice 7, added at
  plan time). `ModifierChoice` is a `ModifierHost`, which carries what the
  player selected when the action started, so a modifier judged there can
  tell a Trade for secrets. The walk's state does not hold the selection yet
  on the command that starts the action.
```

- [ ] **Step 2: Add plan-time rulings to "Slice 7: Trade at another site"**

After the slice 7 section's paragraph, before `## Slice 8: Effective suit`, insert:

```markdown
### Plan-time rulings

- **Pick order.** Once a picked modifier's card stands at the unruled pawn
  site, Small Friends and Friendly Familiar are unavailable. Once one of
  them is picked, the other is.
- **Rules that read the pawn site follow the as-if site:** Skilled
  Merchants' and Map Library's region, and Map Library's own site.
- **Secret Signal** sums, at the Trade's end, every favor its trader gained
  in the Trade, Moving Market's included. Each records under one deferral
  key, `TradeFavor.key`.
- **Secret Signal's availability.** Unavailable only for a Trade for
  secrets: the Trade's source, and so its gain, is chosen after the
  modifiers.
```

- [ ] **Step 3: Rewrite the rulings appendix's slice 7 entries**

In the rulings appendix, replace everything from `## Slice 7: Trade at another site` up to (not including) `## Slice 8: Effective suit` with:

```markdown
## Slice 7: Trade at another site

- **177 Small Friends.** "Act as if your pawn is at any site with a
  [suit-beast] card. (You may use Trade modifiers there.)"
  - A Trade modifier. Picked in the modifier loop, it asks which site, right
    after the pick, in the same pass. It offers the sites in play, other
    than the pawn site, that hold a card of the Beast suit. The question has
    no Skip.
  - For the rest of the Trade, the chosen site takes the place of the pawn
    site: the Trade's source cards and the modifiers usable at a site come
    from it. Ruled sites, advisers and relics stay usable.
  - The player is not in two places at once. The physical pawn site's cards
    are not usable unless the player rules that site (FAQ: Trade modifiers
    only at the new site).
  - Rules that read the pawn site follow the chosen site: Skilled Merchants'
    and Map Library's region is its region, and Map Library there is
    selectable.
  - Unavailable, with the reason:
    - once a picked modifier's card stands at the pawn site, and the player
      does not rule it: "A modifier you picked is at your pawn site";
    - once Friendly Familiar acts at another site: "Already acting as if at
      another site";
    - with no such site: "No other site has a Beast card".
  - The question: "Small Friends: act as if at which site?". The line:
    "Acted as if at {the Shrine}."
- **224 Friendly Familiar.** "[secret] Act as if your pawn is at any site.
  You may use Trade modifiers at the site." As Small Friends, costing one
  secret paid onto it when picked.
  - It offers the sites in play, other than the pawn site, that hold a card
    a Trade can draw on (a card with a suit). With none: "No other site has
    a card to trade with".
  - The question: "Friendly Familiar: act as if at which site?". The line:
    "Acted as if at {the Shrine}."
- **55 Secret Signal.** "If you gain only one [favor], gain one more
  [favor]."
  - An optional Trade modifier. It reads the Trade's whole favor gain at the
    Trade's end, after Moving Market: the Trade's own gain and Moving
    Market's. When that is exactly one, one more favor is gained from the
    bank of the Trade's own gain.
  - Two separate gains of one favor are not "only one": a Trade with Moving
    Market that gains one favor and moves it gains two, and nothing more.
  - Moving Market's favor counts at what the Nomad bank could give, so an
    empty bank counts 0.
  - The extra favor does not count again.
  - Unavailable for a Trade for secrets: "A Trade for secrets gains no
    favor". In a Trade for favor it is always available: the source is
    chosen after the modifiers. When the gain is not one, it does nothing
    and writes no line.
  - The line: "Gained 1 more favor.", covering the generic gain line.
```

- [ ] **Step 4: Check the links**

Run: `python3 scripts/check-markdown-links.py`
Expected: no broken link.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): slice 7 rulings: the as-if site, pick order, Secret Signal's whole gain"
```

---

### Task 2: The as-if site, questions after a pick, and seeded dry runs

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/PowerAccess.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala`
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/ModifierHost.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala`
- Modify: `src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala`
- Test: `src/test/scala/oathdigital/gameplay/PowerAccessSuite.scala`

**Interfaces:**
- Produces, for Tasks 3 and 4: every name in Global Constraints' "Names" list except the economy files.

- [ ] **Step 1: Write the failing tests**

Append to `PowerAccessSuite` (package `oathdigital.gameplay`; it already imports `oathdigital.model._`, `CatalogNames` and `Table`; `card` is Errand Boy, `home` Ancient City and `far` Broken Peaks):

```scala
  /** A plain Order denizen, at the pawn site. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")

  /** The walk's answer that `actor` acts as if at `site`. */
  private def actingAt(site: SiteId): Vector[Answered] = Vector(Answered(
    PowerAccess.asIfDecision,
    DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Site(site)), actor))

  test("an as-if site takes the pawn site's place: its cards are usable " +
      "and the pawn site's are not"):
    val state = Table.start.pawn(actor, at = home)
      .denizen(wrestlers, at = home).denizen(card, at = far).ready
    assertEquals(PowerAccess.locate(state, actor, card, actingAt(far)),
      Some(PowerAccess.Held.AtSite(far)))
    assertEquals(PowerAccess.locate(state, actor, wrestlers, actingAt(far)),
      None)
    // Without the answer, the pawn site's card is usable as before.
    assertEquals(PowerAccess.locate(state, actor, wrestlers),
      Some(PowerAccess.Held.AtSite(home)))

  test("a pawn site the actor rules stays usable beside an as-if site"):
    val state = Table.start.pawn(actor, at = home)
      .warbandsAt(home, actor, 1).denizen(wrestlers, at = home)
      .denizen(card, at = far).ready
    assertEquals(PowerAccess.locate(state, actor, wrestlers, actingAt(far)),
      Some(PowerAccess.Held.AtSite(home)))
```

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerAccessSuite"`
Expected: compile failure: `asIfDecision` is not a member of `PowerAccess`, and `locate` takes no fourth argument.

- [ ] **Step 3: The as-if site in `PowerAccess`**

In `PowerAccess.scala`:

1. Add to the object's doc comment, after its first paragraph:

```scala
  * A modifier may make its user act as if their pawn stood at another site
  * for the rest of the action ([[asIfDecision]]). That site then takes the
  * pawn site's place for `locate`, read from the walk's answers. Ruled
  * sites and the play area stay usable; the physical pawn site does not,
  * unless the player rules it.
  *
```

2. Replace `usableSites` with:

```scala
  /** The decision a modifier asks to name the site its user acts as if at
    * for the rest of the action. Only one such modifier is picked per
    * action, so the walk holds at most one answer. */
  val asIfDecision: String = "modifier.as-if-site"

  /** The site the walk's answers say the actor acts as if at, if any. */
  def asIfSite(answered: Vector[Answered]): Option[SiteId] =
    answered.reverseIterator.collectFirst:
      case Answered(`asIfDecision`, DecisionAnswer.ChooseOneAnswer(
          DecisionOptionRef.Site(site)), _) => site

  /** Where the actor acts from: the as-if site, else the pawn site. */
  def presentSite(ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered]): Option[SiteId] =
    asIfSite(answered).orElse(pawnSite(ready, actor))

  private def usableSites(ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered]): Set[SiteId] =
    ruledSites(ready, actor) ++ presentSite(ready, actor, answered)
```

3. In `accessible`, change `lazy val sites = usableSites(ready, actor)` to `lazy val sites = usableSites(ready, actor, Vector.empty)`. Phase powers keep the pawn site.

4. Change `locate`'s doc and signature to:

```scala
  /** Where `card` is, if `actor` may use it. A site card or edifice at a
    * usable site is `AtSite`. A faceup adviser or relic in the actor's play
    * area is `InPlayArea`. `answered` are the walk's answers, which may
    * place the actor at an as-if site. */
  def locate(ready: ReadyGame, actor: PlayerId, card: CardId,
      answered: Vector[Answered] = Vector.empty): Option[Held] =
    val current = ready.game.current
    val atSite = usableSites(ready, actor, answered).toVector
      .sortBy(_.value).collectFirst:
```

The rest of `locate` and `siteOf` stay as they are.

- [ ] **Step 4: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerAccessSuite"`
Expected: PASS, every test of the suite.

- [ ] **Step 5: `selectionQuestions` and `ModifierHost`**

In `ContributingPower.scala`, add after `selectionWindows`:

```scala
  /** What the modifier decision asks once the player has picked this power
    * and paid for it, in the same pass, before the next pass (catalog
    * batch 8, "An as-if pawn site for Trade"). Nothing by default. */
  def selectionQuestions(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] = Vector.empty
```

Create `src/main/scala/oathdigital/gameplay/powerresolver/ModifierHost.scala`:

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.model.{DecisionOptionRef, Operation}

/** An action's modifier decision, as a power judged there finds it at
  * `PowerCtx.operation`. It names what the player selected when the action
  * started (Trade's resource), which the walk's state does not hold yet on
  * the command that starts the action. */
trait ModifierHost extends Operation:
  def startSelection: Vector[DecisionOptionRef]
```

- [ ] **Step 6: `advance` takes a seed, and the dry runs take answers**

In `ProcedureWalker.scala`, change `advance`:

```scala
  /** ... keep the existing doc, and add:
    * `seed` are the answers a fresh walk starts with, for a dry run of the
    * rest of an action that must see what the player answered before it. A
    * resumed walk ignores it and reads `pending`. */
  def advance(state: ReadyGame, action: Operation,
      pending: Option[PendingTree], powers: WalkerPowers,
      dice: WalkerDice = WalkerDice.unavailable,
      seed: Vector[Answered] = Vector.empty)
      : Either[OathViolation, WalkerOutcome] =
    val activePlayer = state.game.current.turn.activePlayer
    val answered = pending.fold(seed)(_.answered)
```

`advance` has no doc comment today; add one that says only the `seed` sentence.

In `WalkerSimulation.scala`, add `Answered` to the `oathdigital.model` import, and change `refusal` and `preview`:

```scala
  def refusal(tree: Operation, state: ReadyGame, powers: WalkerPowers,
      answered: Vector[Answered] = Vector.empty): Option[OathViolation] =
    guarded {
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer, answered)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder, answered))
    }.left.toOption
```

```scala
  def preview(tree: Operation, state: ReadyGame,
      powers: WalkerPowers, answered: Vector[Answered] = Vector.empty)
      : Either[OathViolation, Vector[PreviewedOption]] =
    guarded:
      ProcedureWalker.restrictionViolations(tree, powers, state,
        state.game.current.turn.activePlayer, answered)
        .headOption.toLeft(())
        .flatMap(_ => ProcedureWalker.advance(state, tree, None, powers,
          WalkerDice.placeholder, answered))
        .flatMap:
          // unchanged from here
```

Add to each doc comment: "`answered` are the answers the walk starts with (`ProcedureWalker.advance`'s `seed`)."

- [ ] **Step 7: `ModifierChoice`**

In `ModifierChoice.scala`:

1. Add `ModifierHost` to the `powerresolver` import.

2. Change the class header, and add to its doc comment the paragraph below the code:

```scala
final class ModifierChoice(val actor: PlayerId, modifierWindow: PowerWindow,
    rest: Operation, startLabel: String,
    explain: Option[WalkerProcedureRegistry.OptionPricing],
    val startSelection: Vector[DecisionOptionRef])
    extends ModifierHost:
```

```scala
  * A pick pays, then asks the picked power's own questions
  * (`selectionQuestions`), inside the pass's second child. The walk's
  * answers reach every judgement of the next pass: which cards the actor
  * may use, and the dry runs of "Start".
```

3. In `candidates`, pass the answers to `locate`:

```scala
      power.selectionCard.exists(card =>
        PowerAccess.locate(pass.state, actor, card, pass.answered).isDefined))
```

4. Replace `payment`:

```scala
  /** After the pass's answer: the payment of the modifier it picked, then
    * the questions that modifier asks once picked. */
  private def payment(powers: WalkerPowers): Operation =
    Branch((ready, tree) => tree.answered.reverseIterator.collectFirst {
      case Answered(`decisionId`, DecisionAnswer.ChooseOneAnswer(ref), _) =>
        ref
    }.flatMap(ModifierPicks.powerFor(powers.pool, modifierWindow, _))
      .toVector.flatMap(power =>
        paying(power) +: power.selectionQuestions(ready, actor)))
```

5. In `refusal`, pass the pass's answers to both dry runs and to `explained`:

```scala
    val opens = WalkerSimulation.preview(rest, pass.state, powers,
        pass.answered) match
      case Right(previewed) => previewed.exists(_.outcome.isRight)
      case Left(_) => WalkerSimulation.refusal(rest, pass.state, powers,
        pass.answered).isEmpty
    if opens then None
    else Some(explained(pass.state, powers, pass.answered)
      .getOrElse(ModifierChoice.cannotStart))
```

6. `explained` takes the answers and seeds its walk:

```scala
  private def explained(state: ReadyGame, powers: WalkerPowers,
      answered: Vector[Answered]): Option[String] = for
    pricing <- explain
    pending <- ProcedureWalker.advance(state, rest, None, powers,
        seed = answered).toOption
      .collect { case WalkerOutcome.Parked(at, _) => at }
```

The rest of `explained` is unchanged: `pending` now carries the answers to `parkedDecide` and to `pricing`.

7. `loop` takes the selection:

```scala
  def loop(actor: PlayerId, window: PowerWindow, rest: Operation,
      startLabel: String,
      explain: Option[WalkerProcedureRegistry.OptionPricing] = None,
      selection: Vector[DecisionOptionRef] = Vector.empty)
      : Operation =
    val id = ModifierPicks.decisionId(window)
    Repeat((_, pending) => !ModifierPicks.started(pending.answered, id),
      new ModifierChoice(actor, window, rest, startLabel, explain,
        selection))
```

Add to `loop`'s doc: "`selection` is what the player selected when the action started."

- [ ] **Step 8: Economy trees pass their selection**

In `EconomyTree.scala`, add the last field to `Kind`:

```scala
      yields: (PlayerId, MusterSource, Int, ForceKind) => Option[CoreOperation],
      /** What the player selected when the action started, which the
        * modifier decision names. */
      selection: Vector[DecisionOptionRef] = Vector.empty)
```

and pass it in `tree`:

```scala
    Sequence(Vector(ModifierChoice.loop(actor, kind.modifiers, rest,
      kind.startLabel, Some(startReason), kind.selection), rest))
```

In `TradeProcedure.kind`, give each `Kind` its selection, as the last argument of each constructor call:

```scala
      (actor, source, matching, _) =>
        Some(Gain.Favor(actor, source.suit, 1 + matching)),
      selection = selection(resource))
```

and the same `selection = selection(resource)` after the secrets `yields`.

- [ ] **Step 9: `SelectedModifier.selectable` takes the answers**

In `SelectedModifier.scala`:

```scala
      case Some(action) => actions(action) &&
        (ModifierPicks.isPicked(ctx.answered, ctx.window, cardId) ||
          selectable(ctx.state, ctx.activePlayer, ctx.answered))
```

```scala
  /** The player may use the card, from where the walk's answers place them,
    * and can pay for it. */
  def selectable(ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered]): Boolean =
    PowerAccess.locate(ready, actor, cardId, answered).isDefined &&
      Costs.affordable(ready, actor, Location.OnCard(cardId), cost)
```

In `AwaitedReturn.scala`:

```scala
  override def selectable(ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered]): Boolean =
    super.selectable(ready, actor, answered) &&
      PlayerFacts.player(ready, actor).exists(_.board.warbands > 0)
```

- [ ] **Step 10: The as-if site in the economy sources**

In `EconomySources.scala`:

1. Replace `inPawnRegion`:

```scala
  /** Every card with a suit at the sites in play in the region of `actor`'s
    * pawn, or of the site the walk's answers place them at, that site
    * included, in map then site order. A card without a suit is no source,
    * as at the pawn site. */
  def inPawnRegion(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId, answered: Vector[Answered])
      : Vector[SiteDenizenState] =
    val map = ready.game.current.map
    val region = PowerAccess.presentSite(ready, actor, answered)
      .flatMap(map.regionOf)
    map.inPlay.filter(site => region.exists(map.regionOf(site).contains))
      .flatMap(site => cardsAt(catalog, ready, site))
```

2. Add after it:

```scala
  /** Every card with a suit at `site`, in site order. */
  def cardsAt(catalog: ExecutableCatalog, ready: ReadyGame, site: SiteId)
      : Vector[SiteDenizenState] =
    ready.game.current.map.sites.get(site).toVector.flatMap(_.denizens)
      .filter(card => catalog.suitOf(card.id).nonEmpty)

  /** The priority the as-if site's rewrite folds at: before the powers that
    * add cards, at 0, which then add to the new site's cards. */
  val RelocationPriority: Int = -1

  /** `operations` with each source decision's options replaced by the
    * cards at the site the walk's answers place the actor at, offered as
    * the pawn site's are. Unchanged without such a site. */
  def relocated(catalog: ExecutableCatalog, ctx: PowerCtx,
      operations: Vector[Operation]): Vector[Operation] =
    PowerAccess.asIfSite(ctx.answered).fold(operations)(site =>
      withOptions(ctx, operations)(_ =>
        cardsAt(catalog, ctx.state, site).map(MusterSource.offered)))
```

In `MapLibrary.scala` and `SkilledMerchants.scala`, pass `ctx.answered` to `inPawnRegion`:

```scala
        EconomySources.inPawnRegion(catalog, ctx.state, ctx.activePlayer,
          ctx.answered)))))
```

In `MapLibrary.scala`, replace `selectable` and its doc:

```scala
  /** The rulebook's access, and the pawn at Map Library's site, or the
    * player acting as if there: ruling the site is not enough. */
  override def selectable(ready: ReadyGame, actor: PlayerId,
      answered: Vector[Answered]): Boolean =
    super.selectable(ready, actor, answered) &&
      PowerAccess.presentSite(ready, actor, answered).exists(site =>
        SiteRulers.siteOf(ready, cardId).contains(site))
```

Add one sentence to the class docs of Map Library and Skilled Merchants: "A player acting as if at another site draws on that site's region."

- [ ] **Step 11: Compile and run the affected suites**

Run: `./sbtw "testOnly oathdigital.gameplay.PowerAccessSuite oathdigital.gameplay.powers.economy.* oathdigital.gameplay.walker.*"`
Expected: PASS. Nothing has an as-if answer yet, so every existing Trade, Muster and modifier test behaves as before.

If the build reports another override of `selectable` or another caller of `inPawnRegion`, update it the same way.

- [ ] **Step 12: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS, the baseline plus 2.

- [ ] **Step 13: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/PowerAccess.scala \
  src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala \
  src/main/scala/oathdigital/gameplay/powerresolver/ModifierHost.scala \
  src/main/scala/oathdigital/gameplay/walker/ProcedureWalker.scala \
  src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala \
  src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/EconomyTree.scala \
  src/main/scala/oathdigital/gameplay/actions/economy/TradeProcedure.scala \
  src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/AwaitedReturn.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/MapLibrary.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/SkilledMerchants.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/EconomySources.scala \
  src/test/scala/oathdigital/gameplay/PowerAccessSuite.scala
git commit -m "feat(batch8): a modifier may place its user at an as-if site"
```

---

### Task 3: Small Friends and Friendly Familiar

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/ActingSite.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/SmallFriends.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliar.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/SmallFriendsSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliarSuite.scala`

**Interfaces:**
- Consumes from Task 2: `PowerAccess.asIfDecision`, `asIfSite`, `pawnSite`, `ruledSites`; `ContributingPower.selectionQuestions`; `EconomySources.relocated`, `cardsAt`, `RelocationPriority`; `ModifierPicks.cards`.

- [ ] **Step 1: Write the failing tests for Small Friends**

Create `src/test/scala/oathdigital/gameplay/powers/economy/SmallFriendsSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class SmallFriendsSuite extends munit.FunSuite:
  private val friends = CatalogNames.denizen("Small Friends")
  private val power = SmallFriends.forCatalog(catalog)
  private val map = Table.start.unchecked.game.current.map
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** A site in play in another region. */
  private val away = map.inPlay.find(site =>
    map.regionOf(site) != map.regionOf(home)).get
  /** A third site in play. */
  private val third = map.inPlay.find(site =>
    site != home && site != away).get
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val oldOak = CatalogNames.denizen("The Old Oak")
  private val market = CatalogNames.denizen("Moving Market")
  private val merchants = CatalogNames.denizen("Skilled Merchants")
  private val modifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Small Friends, with Wrestlers beside them,
    * Errand Boy at a site in another region, and the Beast bank holding 3
    * favor. */
  private def withBeastCardAway: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, friends).denizen(wrestlers, at = home)
    .denizen(errandBoy, at = away).bankFavor(Suit.Beast, 3)

  /** p1 starts a Trade for `resource`, parked on the modifier decision. */
  private def tradeBegun(ready: ReadyGame, resource: String = "favor")
      : OathTransition = accepted(MenuPlay.started(rules, Ready(ready), p1,
    ActionRef.Trade, Vector(DecisionOptionRef.Button(resource))))

  /** `from`, then p1's answer `ref` at `decisionId`; the events run from
    * the Trade's start. */
  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(rules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  private def friendsPicked(from: OathTransition): OathTransition =
    answeredWith(from, modifiers, DecisionOptionRef.Denizen(friends))

  /** `from` with Small Friends picked and acting as if at `site`. */
  private def actingAt(from: OathTransition, site: SiteId): OathTransition =
    answeredWith(friendsPicked(from), PowerAccess.asIfDecision,
      DecisionOptionRef.Site(site))

  /** Each option's id, state and reason, at the parked decision. */
  private def optionStates(at: OathTransition)
      : Option[Vector[(String, String, Option[String])]] =
    PhasePowerHarness.queryOf(at, p1).map(_.offeredOptions.map(option =>
      (option.id, option.state, option.reason)))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("picked, it asks which site, offering only the other sites with a " +
      "Beast card"):
    // Alchemist, at the third site, is no Beast card.
    val asked = friendsPicked(tradeBegun(withBeastCardAway
      .denizen(alchemist, at = third).ready))
    assertEquals(PhasePowerHarness.offered(asked, p1),
      Some(Vector("site" -> away.value)))

  test("acting at the chosen site, the Trade draws only on its cards, and " +
      "the line names it"):
    val ready = withBeastCardAway.ready
    // No other modifier to pick, so the Trade starts by itself.
    val acting = actingAt(tradeBegun(ready), away)
    assertEquals(PhasePowerHarness.offered(acting, p1),
      Some(Vector("denizen" -> errandBoy.value)))
    val done = answeredWith(acting, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(errandBoy))
    val end = Look(done.state)
    assertEquals(end.tokensOn(errandBoy), Tokens(0, 1))
    // 1, and 1 more for Small Friends, a faceup Beast adviser.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Beast), 1)
    assertEquals(said(done.events), Vector(NoteText.Said("acted",
      s"Acted as if at ${away.value}.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events),
      Outcomes.ready(done.state))
    assertWireRoundTrips(done.events)

  test("Start stays available after the site, though nothing near the " +
      "pawn can be traded with"):
    // Skilled Merchants keeps the modifier decision asking.
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .adviser(p1, merchants).denizen(errandBoy, at = away).ready
    val acting = actingAt(tradeBegun(ready), away)
    assertEquals(optionStates(acting).map(_.take(1)),
      Some(Vector(("start", "available", None))))

  test("a Trade modifier at the chosen site becomes selectable, and " +
      "applies there"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .denizen(oldOak, at = away).favor(p1, 2).ready
    val begun = tradeBegun(ready, "secret")
    // From Ancient City, The Old Oak is out of reach.
    assert(!optionStates(begun).exists(_.exists(_._1 == oldOak.value)))
    val acting = actingAt(begun, away)
    assert(optionStates(acting).exists(_.contains(
      (oldOak.value, "available", None))))
    val picked = answeredWith(acting, modifiers,
      DecisionOptionRef.Denizen(oldOak))
    val done = answeredWith(picked, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(oldOak))
    // 1 secret for Small Friends, a faceup Beast adviser, and 1 more for
    // The Old Oak.
    assertEquals(Look(done.state).faceUpSecrets(p1),
      Look(ready).faceUpSecrets(p1) + 2)

  test("with a modifier picked at the pawn site, it is unavailable"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .denizen(market, at = home).denizen(errandBoy, at = away).ready
    val picked = answeredWith(tradeBegun(ready), modifiers,
      DecisionOptionRef.Denizen(market))
    assert(optionStates(picked).exists(_.contains((friends.value,
      "unavailable", Some("A modifier you picked is at your pawn site")))))

  test("it is unavailable when no other site has a Beast card"):
    // Errand Boy stands at the pawn site, which does not count.
    val ready = Table.start.pawn(p1, at = home).adviser(p1, friends)
      .adviser(p1, merchants).denizen(errandBoy, at = home).ready
    assert(optionStates(tradeBegun(ready)).exists(_.contains((friends.value,
      "unavailable", Some("No other site has a Beast card")))))
```

- [ ] **Step 2: Write the failing tests for Friendly Familiar**

Create `src/test/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliarSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, PhasePowerHarness,
  PowerRules, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class FriendlyFamiliarSuite extends munit.FunSuite:
  private val familiar = CatalogNames.denizen("Friendly Familiar")
  private val friends = CatalogNames.denizen("Small Friends")
  private val merchants = CatalogNames.denizen("Skilled Merchants")
  private val library = CatalogNames.denizen("Map Library")
  private val map = Table.start.unchecked.game.current.map
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** Another site in play in the pawn's region. */
  private val homeNeighbour = map.inPlay.find(site => site != home &&
    map.regionOf(site) == map.regionOf(home)).get
  /** A site in play in another region. */
  private val away = map.inPlay.find(site =>
    map.regionOf(site) != map.regionOf(home)).get
  /** Another site in play in `away`'s region. */
  private val awayNeighbour = map.inPlay.find(site => site != away &&
    map.regionOf(site) == map.regionOf(away)).get
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Friendly Familiar, with a second faceup
    * secret: one for the Familiar, one for a Trade for favor. */
  private def holdingFamiliar: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, familiar).secrets(p1, faceUp = 2)

  private def tradeBegun(ready: ReadyGame): OathTransition =
    accepted(MenuPlay.started(rules, Ready(ready), p1, ActionRef.Trade,
      Vector(DecisionOptionRef.Button("favor"))))

  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(rules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  /** `from` with `card`'s modifier picked and acting as if at `site`. */
  private def actingAt(from: OathTransition, card: DenizenId, site: SiteId)
      : OathTransition = answeredWith(answeredWith(from, modifiers,
    DecisionOptionRef.Denizen(card)), PowerAccess.asIfDecision,
    DecisionOptionRef.Site(site))

  private def optionStates(at: OathTransition)
      : Option[Vector[(String, String, Option[String])]] =
    PhasePowerHarness.queryOf(at, p1).map(_.offeredOptions.map(option =>
      (option.id, option.state, option.reason)))

  test("picked, it pays a secret onto itself and offers every other site " +
      "with a card to trade with"):
    // Errand Boy stands at the pawn site, which is not offered.
    val ready = holdingFamiliar.denizen(wrestlers, at = away)
      .denizen(errandBoy, at = home).ready
    val asked = answeredWith(tradeBegun(ready), modifiers,
      DecisionOptionRef.Denizen(familiar))
    assertEquals(PhasePowerHarness.offered(asked, p1),
      Some(Vector("site" -> away.value)))
    assertEquals(Look(asked.state).adviserTokens(p1, familiar), Tokens(0, 1))

  test("acting at the chosen site, Skilled Merchants adds that site's " +
      "region, not the pawn's"):
    val ready = holdingFamiliar.adviser(p1, merchants)
      .denizen(wrestlers, at = away).denizen(alchemist, at = awayNeighbour)
      .denizen(errandBoy, at = homeNeighbour).ready
    val acting = actingAt(tradeBegun(ready), familiar, away)
    // Nothing is left to pick, so the Trade starts by itself.
    val sources = answeredWith(acting, modifiers,
      DecisionOptionRef.Denizen(merchants))
    assertEquals(PhasePowerHarness.offered(sources, p1), Some(Vector(
      "denizen" -> wrestlers.value, "denizen" -> alchemist.value)))

  test("Map Library at the chosen site is selectable while the pawn " +
      "stands elsewhere"):
    val ready = holdingFamiliar.denizen(library, at = away).ready
    val begun = tradeBegun(ready)
    assert(!optionStates(begun).exists(_.exists(_._1 == library.value)))
    assert(optionStates(actingAt(begun, familiar, away)).exists(_.contains(
      (library.value, "available", None))))

  test("once Small Friends acts at another site, it is unavailable"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, familiar)
      .adviser(p1, friends).denizen(errandBoy, at = away).ready
    val acting = actingAt(tradeBegun(ready), friends, away)
    assert(optionStates(acting).exists(_.contains((familiar.value,
      "unavailable", Some("Already acting as if at another site")))))
```

If `awayNeighbour`'s `.get` fails because `away`'s region has only one site in play, pick `away` from a region that has two.

- [ ] **Step 3: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.SmallFriendsSuite oathdigital.gameplay.powers.economy.FriendlyFamiliarSuite"`
Expected: compile failure: `SmallFriends` is not found.

- [ ] **Step 4: Move the cards out of the holding files**

Cut `SmallFriendsCard` from `HoldingBeastDenizens.scala` and `FriendlyFamiliarCard` from `HoldingDiscordDenizens.scala`. Paste each, unchanged, at the top of its new file below. Remove any import the holding files no longer use.

In `NewFoundations.scala`, add `FriendlyFamiliarCard` and `SmallFriendsCard` to the `oathdigital.gameplay.powers.economy` import, in alphabetical order. The card lists already name them, and `oathdigital.catalog.holding._` no longer provides them.

- [ ] **Step 5: The shared trait**

Create `src/main/scala/oathdigital/gameplay/powers/economy/ActingSite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.{Contribution, ModifierPicks,
  OfferReason, PowerCtx, Transform}
import oathdigital.gameplay.powers.{NoteSupport, SelectedModifier}
import oathdigital.model._

/** What Small Friends and Friendly Familiar share (catalog batch 8, slice
  * 7): a Trade modifier that, once picked and paid for, asks which site its
  * user acts as if at for the rest of the Trade
  * ([[PowerAccess.asIfDecision]]).
  *
  * The site then takes the pawn site's place: later passes of the modifier
  * decision offer its cards' modifiers, and the Trade's source options are
  * its cards ([[EconomySources.relocated]]), rewritten before the powers
  * that add cards. The question offers only sites other than the pawn site,
  * and has no Skip.
  *
  * Unavailable, in this order: once the other card acts at another site;
  * once a picked modifier's card stands at the unruled pawn site, since the
  * player is not in two places at once; and with no site to offer. Its line
  * names the site: "Acted as if at {the Shrine}."
  */
trait ActingSite extends SelectedModifier:
  /** The sites the player may act as if at: never the pawn site. */
  def sites(ready: ReadyGame, actor: PlayerId): Vector[SiteId]
  /** Why the card is unavailable when `sites` is empty. */
  def noSite: String
  /** The site question's heading. */
  def heading: String

  final def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  final override def priority: Int = EconomySources.RelocationPriority
  final override def noteKeys: Vector[NoteKey] = Vector(ActingSite.acted)
  final override def narratedDecisions: Set[String] =
    Set(PowerAccess.asIfDecision)

  final def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeSourceSelection -> Vector(Transform((ctx, operations) =>
      EconomySources.relocated(catalog, ctx, operations))))

  final override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    val reason =
      if PowerAccess.asIfSite(ctx.answered).nonEmpty then
        Some(ActingSite.elsewhere)
      else if pickedAtPawnSite(ctx) then Some(ActingSite.pickedHere)
      else Option.when(sites(ctx.state, ctx.activePlayer).isEmpty)(noSite)
    reason.map(OfferReason.Unavailable(_))

  final override def selectionQuestions(ready: ReadyGame, actor: PlayerId)
      : Vector[Operation] = Vector(
    Decide(PowerAccess.asIfDecision, actor, DecisionQuery.ChooseOne(
      sites(ready, actor).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some(heading))),
    Note(id, actedNote))

  /** Whether a modifier already picked stands at the pawn site, which the
    * actor does not rule. */
  private def pickedAtPawnSite(ctx: PowerCtx): Boolean =
    val ready = ctx.state
    val actor = ctx.activePlayer
    val picked = ModifierPicks.cards(ctx.answered).toSet
    PowerAccess.pawnSite(ready, actor)
      .filterNot(PowerAccess.ruledSites(ready, actor))
      .flatMap(ready.game.current.map.sites.get)
      .exists(_.denizens.exists(card => picked(card.id)))

  private def actedNote(states: NoteStates): Option[PowerNote] =
    NoteSupport.answer(states, PowerAccess.asIfDecision).collect {
      case DecisionOptionRef.Site(site) =>
        ActingSite.acted(PowerSourceRef.Card(cardId), NoteArg.Site(site))
    }

object ActingSite:
  /** "Acted as if at {the Shrine}." */
  val acted: NoteKey = NoteKey("acted", Vector(
    NotePart.Text("Acted as if at "), NotePart.Arg(0), NotePart.Text(".")))
  val elsewhere: String = "Already acting as if at another site"
  val pickedHere: String = "A modifier you picked is at your pawn site"

  /** The sites in play other than `actor`'s pawn site that hold a card
    * `admits`, in map order. */
  def otherSites(ready: ReadyGame, actor: PlayerId)(
      admits: SiteDenizenState => Boolean): Vector[SiteId] =
    val current = ready.game.current
    val pawn = PowerAccess.pawnSite(ready, actor)
    current.map.inPlay.filter(site => !pawn.contains(site) &&
      current.map.sites.get(site).exists(_.denizens.exists(admits)))
```

`ModifierPicks.cards` returns `Vector[CardId]`, and `SiteDenizenState.id` is a `CardId`, so the set lookup compiles. If `card.id` there is a narrower id type, widen with `card.id: CardId`.

- [ ] **Step 6: Small Friends**

Create `src/main/scala/oathdigital/gameplay/powers/economy/SmallFriends.scala`. Start it with the package line, these imports, then the moved `SmallFriendsCard`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.model._

// SmallFriendsCard, moved here unchanged.

/** Small Friends (card 177, adviser-only), a selected Trade modifier with
  * no cost: "Act as if your pawn is at any site with a beast card. (You may
  * use Trade modifiers there.)" It offers the sites in play, other than the
  * pawn site, that hold a card of the Beast suit ([[ActingSite]]).
  */
final case class SmallFriends private (catalog: ExecutableCatalog)
    extends ActingSite:
  val cardId: DenizenId = SmallFriendsCard.id
  def id: PowerId = SmallFriends.id
  val noSite: String = "No other site has a Beast card"
  val heading: String = "Small Friends: act as if at which site?"

  def sites(ready: ReadyGame, actor: PlayerId): Vector[SiteId] =
    ActingSite.otherSites(ready, actor)(card =>
      catalog.suitOf(card.id).contains(Suit.Beast))

object SmallFriends:
  val id: PowerId = SmallFriendsCard.power.id

  def forCatalog(catalog: ExecutableCatalog): SmallFriends =
    new SmallFriends(catalog)
```

- [ ] **Step 7: Friendly Familiar**

Create `src/main/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliar.scala`, the same way, with the moved `FriendlyFamiliarCard`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.model._

// FriendlyFamiliarCard, moved here unchanged.

/** Friendly Familiar (card 224, adviser-only), a selected Trade modifier
  * costing one secret, paid onto it when picked: "Act as if your pawn is at
  * any site. You may use Trade modifiers at the site." It offers the sites
  * in play, other than the pawn site, that hold a card a Trade can draw on
  * ([[ActingSite]]).
  */
final case class FriendlyFamiliar private (catalog: ExecutableCatalog)
    extends ActingSite:
  val cardId: DenizenId = FriendlyFamiliarCard.id
  def id: PowerId = FriendlyFamiliar.id
  override def cost: Cost = FriendlyFamiliarCard.power.cost
  val noSite: String = "No other site has a card to trade with"
  val heading: String = "Friendly Familiar: act as if at which site?"

  def sites(ready: ReadyGame, actor: PlayerId): Vector[SiteId] =
    ActingSite.otherSites(ready, actor)(card =>
      catalog.suitOf(card.id).nonEmpty)

object FriendlyFamiliar:
  val id: PowerId = FriendlyFamiliarCard.power.id

  def forCatalog(catalog: ExecutableCatalog): FriendlyFamiliar =
    new FriendlyFamiliar(catalog)
```

- [ ] **Step 8: Register them**

In `ActionModifiers.scala`, add `FriendlyFamiliar` and `SmallFriends` to the `economy` import, in alphabetical order, and after `Vector(SkilledMerchants.forCatalog(catalog)) ++` add:

```scala
      Vector(SmallFriends.forCatalog(catalog),
        FriendlyFamiliar.forCatalog(catalog)) ++
```

In `PowerKindsCatalogSuite`, add to `modifiers`, after `"denizen.old-songs"`:

```scala
    "denizen.pressgangs", "denizen.old-songs", "denizen.small-friends",
    "denizen.friendly-familiar")
```

- [ ] **Step 9: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.SmallFriendsSuite oathdigital.gameplay.powers.economy.FriendlyFamiliarSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS, 10 new tests.

If "Start stays available after the site" fails with Start unavailable, the seed does not reach a dry run. Check every call in `ModifierChoice.refusal` and `explained` from Task 2, Step 7.

- [ ] **Step 10: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS, the baseline plus 12.

- [ ] **Step 11: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/ActingSite.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/SmallFriends.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliar.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingBeastDenizens.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingDiscordDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/SmallFriendsSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/FriendlyFamiliarSuite.scala
git commit -m "feat(batch8): Small Friends and Friendly Familiar trade at another site"
```

---

### Task 4: Secret Signal

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/TradeFavor.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/economy/SecretSignal.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/economy/SecretSignalSuite.scala`

**Interfaces:**
- Consumes from Task 2: `ModifierHost.startSelection`; `TradeProcedure.selection`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/scala/oathdigital/gameplay/powers/economy/SecretSignalSuite.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, EconomyActions,
  Look, Outcomes, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class SecretSignalSuite extends munit.FunSuite:
  private val signal = CatalogNames.denizen("Secret Signal")
  private val power = SecretSignal.forCatalog(catalog)
  private val selected = Vector(SecretSignal.id)
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** An empty site in play, for Moving Market's move. */
  private val giant = CatalogNames.site("Buried Giant")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val market = CatalogNames.denizen("Moving Market")
  private val merchants = CatalogNames.denizen("Skilled Merchants")
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Secret Signal, with Wrestlers and Alchemist
    * beside them and the Order and Arcane banks holding 3 favor each. */
  private def holdingSignal: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, signal).denizen(wrestlers, at = home)
    .denizen(alchemist, at = home).bankFavor(Suit.Order, 3)
    .bankFavor(Suit.Arcane, 3)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  /** Secret Signal's option at the start of a Trade for `resource`: its
    * state and reason. */
  private def signalAtStart(ready: ReadyGame, resource: String)
      : Option[(String, Option[String])] =
    PhasePowerHarness.queryOf(accepted(rules.startWalker(Ready(ready),
      ActionRef.Trade, p1,
      startArgs = Vector(DecisionOptionRef.Button(resource)))), p1)
      .flatMap(_.offeredOptions.find(_.id == signal.value))
      .map(option => (option.state, option.reason))

  test("a Trade that gains one favor gains one more from the same bank, " +
      "with its line"):
    val ready = holdingSignal.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.result)
    // 1 for the Trade, p1 holding no Order adviser, and 1 more.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Order), 1)
    assertEquals(said(done.journal.events), Vector(NoteText.Said("gained",
      "Gained 1 more favor.", covers = true)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.journal.events),
      done.result)
    assertWireRoundTrips(done.journal.events)

  test("a Trade that gains more than one favor gains nothing more"):
    val ready = holdingSignal.ready
    val done = EconomyActions.trade(rules, ready, p1, selected, "favor",
      DecisionOptionRef.Denizen(alchemist))
    val end = Look(done.result)
    // Secret Signal is itself a faceup Arcane adviser: 2 favor.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Arcane), 1)
    assertEquals(said(done.journal.events), Vector.empty)

  test("with Moving Market's favor after the Trade's, two gains of one " +
      "gain nothing more"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, signal)
      .denizen(market, at = home).bankFavor(Suit.Nomad, 3).ready
    val started = accepted(ModifierPlay.started(rules, Ready(ready),
      ActionRef.Trade, p1, PowerWindow.TradeModifierSelection,
      Vector(MovingMarket.id, SecretSignal.id),
      startArgs = Vector(DecisionOptionRef.Button("favor"))))
    val asked = accepted(rules.resolveWalker(started.state, p1,
      TradeProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(market))))
    val done = accepted(rules.resolveWalker(asked.state, p1,
      MovingMarket.decisionId,
      ChooseOneAnswer(DecisionOptionRef.SiteSlot(giant, 0))))
    val end = Look(done.state)
    // 1 from the Trade and 1 from Moving Market, both Nomad.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Nomad), 1)
    assertEquals(said(started.events ++ asked.events ++ done.events),
      Vector.empty)
    assert(Outcomes.ready(done.state).game.current.walkerDeferred.isEmpty)

  test("it is unavailable for a Trade for secrets, and available for " +
      "favor"):
    // Skilled Merchants keeps the modifier decision asking.
    val ready = holdingSignal.adviser(p1, merchants).favor(p1, 2).ready
    assertEquals(signalAtStart(ready, "secret"), Some(("unavailable",
      Some("A Trade for secrets gains no favor"))))
    assertEquals(signalAtStart(ready, "favor"), Some(("available", None)))
```

The last assertion of the Moving Market test checks that the walker dropped the held gains when the Trade ended.

- [ ] **Step 2: Run them to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.SecretSignalSuite"`
Expected: compile failure: `SecretSignal` is not found.

- [ ] **Step 3: The shared record**

Create `src/main/scala/oathdigital/gameplay/powers/economy/TradeFavor.scala`:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.model.{Defer, DeferralKey, Gain, ReadyGame}

/** The favor a Trade's trader gains, held under one key as it runs, for a
  * power that counts it (Secret Signal). The counting power records the
  * Trade's own gain; Moving Market records its own. The walker drops what
  * is left when the action ends.
  */
private[economy] object TradeFavor:
  val key: DeferralKey = DeferralKey("trade.favor-gained")

  /** `gain` recorded at what its bank can give now: run before the gain. */
  def record(ready: ReadyGame, gain: Gain.Favor): Defer =
    Defer(key, Vector(gain.copy(amount =
      gain.amount min ready.banks.favor.getOrElse(gain.suit, 0))))
```

- [ ] **Step 4: Moving Market records its favor**

In `MovingMarket.scala`, replace the body of `gain`:

```scala
  private def gain(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    Right(PowerAnswers.one(pending, decisionId).toVector.collect {
      case slot: DecisionOptionRef.SiteSlot
          if SiteRulers.siteOf(ready, cardId).contains(slot.site) =>
        Gain.Favor(actor, Suit.Nomad, Gained)
    }.flatMap(gain =>
      Vector[CoreOperation](TradeFavor.record(ready, gain), gain)))
```

Add to the class doc: "The gain is recorded under `TradeFavor.key`, at what the Nomad bank can give, so a power that counts the Trade's favor sees it."

- [ ] **Step 5: Secret Signal**

Cut `SecretSignalCard` from `HoldingArcaneDenizens.scala`, removing any import that file no longer uses. Add `SecretSignalCard` to `NewFoundations.scala`'s `economy` import.

Create `src/main/scala/oathdigital/gameplay/powers/economy/SecretSignal.scala`, with the moved card under the imports:

```scala
package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog,
  PrintedPower}
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Deferred,
  FoldPhase, ModifierHost, OfferReason, OperationRewrite, PowerCtx,
  Transform}
import oathdigital.gameplay.powers.{NoteSupport, SelectedModifier}
import oathdigital.model._

// SecretSignalCard, moved here unchanged.

/** Secret Signal (card 55, adviser-only), a selected Trade modifier with no
  * cost: "If you gain only one favor, gain one more favor."
  *
  * It reads the Trade's whole favor gain. Its rewrite of the gain node
  * records the trader's favor gain as it runs, under [[TradeFavor.key]],
  * where Moving Market records its own. A step appended to the Trade's root
  * folds `Late`, after Moving Market's: when the recorded favor sums to
  * exactly one, the trader gains one more from the bank of the first gain,
  * the Trade's own. Two gains of one sum to two. The extra favor is not
  * recorded, so it never counts again.
  *
  * The Trade's own gain is recorded as requested. When its bank holds less,
  * the bank is empty after it, and the extra favor, drawn from the same
  * bank, is nothing either way.
  *
  * Unavailable for a Trade for secrets, read from the modifier decision's
  * start selection. In a Trade for favor it is always available: the source,
  * and so the gain, is chosen after the modifiers. Its line, "Gained 1 more
  * favor.", covers the extra gain's generic line.
  */
final case class SecretSignal private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import SecretSignal._

  val cardId: DenizenId = SecretSignalCard.id
  def id: PowerId = SecretSignal.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((_, _) => {
        case gain @ Gain.Favor(player, _, _) if player == ctx.activePlayer =>
          Vector(gain, Defer(TradeFavor.key, Vector(gain)))
      }))),
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children :+ extra(ctx.activePlayer), FoldPhase.Late)))

  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    ctx.operation match
      case host: ModifierHost if host.startSelection ==
          TradeProcedure.selection(TradeResource.Secret) =>
        Some(OfferReason.Unavailable(noFavor))
      case _ => None

  /** One more favor, when the recorded favor sums to exactly one, then its
    * line. */
  private def extra(actor: PlayerId): Operation =
    Deferred.resolve(TradeFavor.key)((_, _, held) => {
      val gains = held.collect {
        case gain: Gain.Favor if gain.player == actor => gain
      }
      gains.headOption.filter(_ => gains.map(_.amount).sum == Only)
        .toVector.flatMap(first => Vector[Operation](
          Gain.Favor(actor, first.suit, Extra),
          Note(id, gainedNote(_, actor), covers = true)))
    })

  /** The favor the step before gained. */
  private def gainedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    step <- states.previous
    amount = NoteSupport.favor(step, actor)
    if amount > 0
  yield gained(PowerSourceRef.Card(cardId), NoteArg.Number(amount))

object SecretSignal:
  val id: PowerId = SecretSignalCard.power.id
  /** The whole gain that earns the extra favor. */
  val Only: Int = 1
  val Extra: Int = 1
  val noFavor: String = "A Trade for secrets gains no favor"
  /** "Gained {n} more favor." */
  val gained: NoteKey = NoteKey("gained", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Text(" more favor.")))

  def forCatalog(catalog: ExecutableCatalog): SecretSignal =
    new SecretSignal(catalog)
```

`TradeResource` is in `oathdigital.model`.

- [ ] **Step 6: Register it**

In `ActionModifiers.scala`, add `SecretSignal` to the `economy` import and extend the vector Task 3 added:

```scala
      Vector(SmallFriends.forCatalog(catalog),
        FriendlyFamiliar.forCatalog(catalog),
        SecretSignal.forCatalog(catalog)) ++
```

In `PowerKindsCatalogSuite`, add `"denizen.secret-signal"` after `"denizen.friendly-familiar"`.

- [ ] **Step 7: Run the tests to see them pass**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.economy.SecretSignalSuite oathdigital.gameplay.powers.economy.MovingMarketSuite oathdigital.gameplay.PowerKindsCatalogSuite"`
Expected: PASS. Moving Market's own tests pass unchanged: the held gain writes no line and is dropped at the Trade's end.

- [ ] **Step 8: Run the whole server suite**

Run: `./sbtw test`
Expected: PASS, the baseline plus 16.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/economy/TradeFavor.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/SecretSignal.scala \
  src/main/scala/oathdigital/gameplay/powers/economy/MovingMarket.scala \
  src/main/scala/oathdigital/catalog/holding/HoldingArcaneDenizens.scala \
  src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala \
  src/main/scala/oathdigital/gameplay/powers/ActionModifiers.scala \
  src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala \
  src/test/scala/oathdigital/gameplay/powers/economy/SecretSignalSuite.scala
git commit -m "feat(batch8): Secret Signal doubles a Trade's lone favor"
```

---

### Task 5: Gates and docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Check the client**

Run: `grep -rn "modifiers\"\|\.modifiers\|as-if" frontend/src | head -20`

The site question is an ordinary choose-one decision with site options, asked between two passes of the modifier decision. If the client treats every decision during the modifier loop as the modifier decision (for example by keying a modifier panel on the walk's procedure instead of on the decision id), it must show the site question as a site choice. If you find such a case, stop and report it before changing the client.

- [ ] **Step 2: Run the gates**

1. `./sbtw "test" "frontend/test"`. Expected: PASS, the server count the baseline plus 16, the frontend count unchanged.
2. `./sbtw frontend/fullOptJS`. Expected: success.
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`. Expected: no finding.

- [ ] **Step 3: Mark the slice done**

In the design's Status line, change "Slices 1 to 6 are done." to "Slices 1 to 7 are done."

In `docs/ROADMAP.md`:
- check the box of `**7. Trade at another site:**`;
- in the Now section, change "slices 1 to 6 are done, and slice 7 is next" to "slices 1 to 7 are done, and slice 8 is next".

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md \
  docs/ROADMAP.md
git commit -m "docs(batch8): slice 7 done"
```
