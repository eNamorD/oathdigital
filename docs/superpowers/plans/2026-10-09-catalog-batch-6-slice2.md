# Catalog Batch 6, Slice 2 (Plan Rewrites) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Peace Envoy (125) and Wild Mounts (153). Peace Envoy decides a battle for its user's side. Wild Mounts replaces the end-of-Campaign discard of its user's Nomad battle plans.

**Architecture:**

- **Peace Envoy is a `BattlePlan`.**
  - Its gift to the enemy is a `CampaignPlanEffect.Run`, so the plan window's dry run prices it. A gift the user cannot pay fails with `InsufficientFavor`, which `PlanPrice.shortfall` words.
  - `PlanPrice` also counts a favor `Give` in the price. This is a plan-time ruling.
  - It shares Code of Honor's `SolePlan` restrictions, and adds one restriction of its own at the defender's window.
- **"You're victorious now" is a new helper, `VictoriousNow`.** Hearts and Minds can reuse it later.
  - It is a set of `wrapping`s: the attack roll, the sacrifice decision and the defense roll fold to nothing.
  - `RecordCampaignResult` is rewritten through `LossRewrite`, so the user's side wins.
  - Peace Envoy's own losses `wrapping` kills no warband.
- **Plan discards are marked.** `PlanDiscard.afterCampaign` wraps what it appends in a `PlanDiscard.Marked` node that names the plan's card and user.
- **Wild Mounts finds the marked discards.** A new hook, `BattlePlan#rewrites`, holds a Transform that runs whether or not the plan was chosen.
  - Wild Mounts folds at a high priority at the Campaign's root.
  - It strips its user's marked Nomad discards out of the plans' own `Branch`es.
  - It appends one `Branch` that asks the user, or applies the bandit FAQ rule, or runs the stripped discards after all.
- **Registration.** Both plans register in a new `PlanRewrites` object.

**Tech Stack:** Scala 3 on the JVM, munit, built through `./sbtw`. No frontend change: the new decision is an ordinary `ChooseOne` over cards and a button.

**Spec:** Read both documents before starting:

- `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`: "Goal and scope", "The bar", "Slice 2: Plan rewrites", "Seams used for the first time" and "Testing".
- `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`: "Rules that apply to the whole batch" and "Slice 2: Plan rewrites".

Task 1 amends both with the rulings made at plan time, so read them after Task 1 too. The log line rules are in `docs/superpowers/specs/2026-09-26-power-log-lines-design.md`.

**Starts from:** `main` at `81ce37e7` or later (slice 1 merged).

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`. An unused import or private member fails it.
  - Each file below lists the imports it needs.
  - A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`).
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`. Test suites may.
  - No power name appears in walker sources.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` into the worktree before the first `./sbtw`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Texts, exactly.**
  - A hover reason reads as the Game Log words its note: the card's name, a colon, then the sentence.
  - A line names the acting player only when the power runs off turn, which means a defender's plan.
  - Peace Envoy:
    - The pawn reason is written, not noted: "Your enemy's pawn must be at your site". This matches Encirclement's force reason.
    - Key `not-with-others`: "Cannot be used with other battle plans."
    - Key `no-others`: "No other battle plans."
    - Key `decided`: "The battle is decided."
    - Key `gave`: "Gave {2 favor} to {Blue}." Key `gave-off-turn`: "{Blue} gave {2 favor} to {Red}."
    - Key `won`: "Won the battle without a roll." Key `won-off-turn`: "{Blue} won the battle without a roll."
  - Wild Mounts:
    - Decision `campaign.wild-mounts`, heading "Wild Mounts: discard a Beast card you rule instead of your Nomad battle plans". Its options are each Beast card, then the button `skip`, "Skip Wild Mounts".
    - Key `swapped`: "Discarded {card} instead of the battle plans." It is also used for bandits, which are no player.
    - Key `swapped-off-turn`: "{Blue} discarded {card} instead of the battle plans."
- **Baseline.** Record the server and frontend test counts from your first full `./sbtw "test" "frontend/test"` run in the worktree, and compare them at the end.
  - At `81ce37e7` the counts are 3420 server and 653 frontend.
  - This plan adds 18 server tests and no frontend test.
  - It rewrites no existing test. One suite's filler card changes (Task 4).
- **Gates**, in order, before the final commit:
  1. `./sbtw "test" "frontend/test"`
  2. `./sbtw frontend/fullOptJS`
  3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

## Rulings made at plan time

The product owner settled rulings 1 to 4 on 2026-10-09. Task 1 writes all of them into the spec.

1. **A Conquest won with Peace Envoy.** No warband dies, but a site holds one force. The defender's warbands at the targets all leave them and all return to the defender's board, as Billowing Fog spares a defeated Conquest defender. The victor then places warbands as usual.
2. **Bandits and Wild Mounts (FAQ).** "If they would discard two or more Nomads, they discard the first Beast card they rule starting at the top of the Cradle and going down through the regions." That is the first faceup Beast denizen in map order (`map.inPlay`: Cradle, Provinces, Hinterland), and within a site in card order. With a single Nomad discard, bandits discard it as usual.
3. **Wording.** The texts in Global Constraints. Wild Mounts' button is "Skip Wild Mounts", not "Discard the battle plans".
4. **Peace Envoy's price shows its gift.** `PlanPrice` counts a favor `Give` that the dry run recorded. With two defense dice, the option is priced 3 favor. This is an approved change outside `gameplay/powers/`.

Also settled at plan time:

5. **Wild Mounts prints no discard of its own,** so the rulings' "Wild Mounts' own discard included" can never apply. Task 1 drops it.
6. **The gift's shortfall** counts the favor already placed onto the card. "Needs 3 favor; you have 2" means 1 printed plus 2 gift, against 2 on the board. When the user cannot pay even the printed favor, the payment fails first and the reason reads "Needs 1 favor; you have 0".
7. **Peace Envoy is never used by or against bandits.** Bandits have no pawn, so the pawn rule withholds it. It still sets `alone`, like Code of Honor.
8. **`BattlePlan#rewrites`** is a power-side hook in `gameplay/powers/campaign`, not an engine change. It is a Transform that runs whether or not the plan was chosen. It keeps the children's shape the same on every fold, and it learns inside a `Branch`, at walk time, whether the plan was used. A bandit's use is recorded in a pool during the walk, so a fold-time read (`wrapping`) would miss it.

## File map

| File | Change | Task |
|---|---|---|
| `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md` | modify | 1 |
| `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md` | modify | 1 |
| `src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala` | create | 2, 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala` | create | 2, 4 |
| `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala` | modify | 2 |
| `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` | modify | 2, 4 |
| `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala` | modify | 2 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoySuite.scala` | create | 2, 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala` | create | 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala` | modify | 3 |
| `src/main/scala/oathdigital/model/CampaignTypes.scala` | modify (doc only) | 3 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala` | modify | 4 |
| `src/main/scala/oathdigital/gameplay/powers/campaign/WildMounts.scala` | create | 4 |
| `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` | modify | 4 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala` | create | 4 |
| `src/test/scala/oathdigital/gameplay/powers/campaign/GreatCrusadeSuite.scala` | modify | 4 |
| `docs/ROADMAP.md` | modify | 5 |

### Moving a card out of holding

Tasks 2 and 4 each move a card object from `src/main/scala/oathdigital/catalog/holding/` into its power's file, as slice 1 did:

1. Delete the card's `object ...Card extends Denizen(...)` block from the holding file. The block is the `object` line, its `val power`, its `val powers` and the blank line after it.
2. Paste it unchanged at the top of the power's file. Each task shows the block.
3. In `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`, add the card object to the `import oathdigital.gameplay.powers.campaign.{...}` list, after `TrueNamesCard`, wrapped at 80 columns. The `oathdigital.catalog.holding._` wildcard stops supplying it, so there is no ambiguity.

The holding files' imports stay as they are: other cards there still use each name.

---

### Task 1: Write the plan-time rulings into the spec

**Files:**
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md`
- Modify: `docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md`

- [ ] **Step 1: Amend the design's bar**

In the design, under "### The bar", after the bullet that starts `- **Plans used alone** (slice 1, approved at plan time).`, insert:

```markdown
- **A gift in a plan's price** (slice 2, approved at plan time).
  `PlanPrice` counts a favor `Give` that the plan's dry run recorded, so
  Peace Envoy's option is priced with its gift.
```

- [ ] **Step 2: Amend the design's slice 2**

In the design, under "### Peace Envoy", replace the bullet

```markdown
- The losses kill no warbands on either side, through `LossRewrite`.
```

with

```markdown
- The losses kill no warbands on either side, through `LossRewrite`. After
  a Conquest it wins, the defender's warbands at the targets all leave
  them and all return to the defender's board, so the victor can place
  warbands as usual (ruled at plan time).
```

In the same section, replace `"Peace Envoy decided the battle"` with `"Peace Envoy: The battle is decided."`.

After the paragraph that starts `The rewrite reads whether Peace Envoy was applied`, add:

```markdown
The roll removal and the result rewrite are `VictoriousNow`, a helper that
Hearts and Minds can reuse.
```

Under "### Wild Mounts", replace the paragraph that starts `The end-of-Campaign discards of plans` and ends `marked discards.` with:

```markdown
The end-of-Campaign discards of plans that say "At end, discard" are
appended at `CampaignActionEligibility` by `PlanDiscard.afterCampaign`.
That helper marks what it appends with a `PlanDiscard.Marked` node naming
the plan's card and user. Wild Mounts' rewrite at the same window, through
the `BattlePlan#rewrites` hook and at a priority after every plan that
appends, strips its user's marked discards of Nomad plans and appends one
step. That step asks the user, when they rule a Beast card: discard one of
them, or "Skip Wild Mounts". Choosing a Beast card discards that card
instead and drops the marked discards; skipping runs them. A bandit user
follows the FAQ: with two or more Nomad discards, the first Beast card the
bandits rule in map order is discarded instead.
```

- [ ] **Step 3: Amend the rulings' slice 2**

In the rulings, in the Peace Envoy entry, replace

```markdown
  - "Ignore killing warbands": the losses kill no warband on either side.
    Victory then resolves as usual.
```

with

```markdown
  - "Ignore killing warbands": the losses kill no warband on either side.
    After a Conquest the attacker wins with it, the defender's warbands at
    the targets all return to the defender's board. Victory then resolves
    as usual.
  - Its price includes the gift: with two defense dice, 3 favor.
  - Its reasons and lines: "Peace Envoy: Cannot be used with other battle
    plans.", "Peace Envoy: No other battle plans.", "Peace Envoy: The
    battle is decided.", "Gave {2 favor} to {Blue}." and "Won the battle
    without a roll.". A defender's lines name them: "{Blue} gave {2 favor}
    to {Red}." and "{Blue} won the battle without a roll.".
```

Also in that entry, replace `"Peace Envoy decided the battle"` with `"Peace Envoy: The battle is decided."`.

In the Wild Mounts entry, replace

```markdown
  - At the end of the Campaign, when any of the user's Nomad plans would be
    discarded, Wild Mounts' own discard included, the user is asked: discard
    one Beast card they rule instead, or "Discard the battle plans".
```

with

```markdown
  - At the end of the Campaign, when any of the user's Nomad plans would be
    discarded, the user is asked: discard one Beast card they rule instead,
    or "Skip Wild Mounts". Wild Mounts prints no discard of its own.
  - Bandits (FAQ): "If they would discard two or more Nomads, they discard
    the first Beast card they rule starting at the top of the Cradle and
    going down through the regions." With one Nomad discard, they discard
    it as usual.
  - Its line: "Discarded {card} instead of the battle plans.", or, for a
    defender, "{Blue} discarded {card} instead of the battle plans.".
```

- [ ] **Step 4: Check links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: pass.

```bash
git add docs/superpowers/specs/2026-10-09-catalog-batch-6-design.md docs/superpowers/specs/2026-10-09-catalog-batch-6-rulings.md
git commit -m "docs: record catalog batch 6 slice 2 plan-time rulings"
```

---

### Task 2: Peace Envoy at the plan windows

The offer, the pawn rule, the gift and its price, "no other plans", and the defender's plans withheld. The battle itself is Task 3.

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoySuite.scala`

**Interfaces:**
- Consumes:
  - `SolePlan.restrictions(plan, sides, source, notWithOthers, noOthers)` (slice 1)
  - `PlanDriver` and `CampaignFixture` (test)
- Produces:
  - `PeaceEnvoy` (case object, a `BattlePlan`), with `PeaceEnvoy.Favor: Int = 1` and `PeaceEnvoy.Apart: String`
  - Note keys `notWithOthers`, `noOthers`, `decided`, `gave` and `gaveOffTurn`
  - `PlanRewrites.forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower]`

- [ ] **Step 1: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoySuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Peace Envoy: one favor placed and one favor given per defense die, used
  * with no other plan, while the enemy's pawn shares the user's site. */
class PeaceEnvoySuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Peace Envoy")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val longbows = CatalogNames.denizen("Longbows")
  private val longbowsRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(longbows)
  private val source = PowerSourceRef.Card(card)

  /** The rival rules the origin with two warbands and the Oathkeeper
    * title, and stands there with no favor. The actor holds Peace Envoy
    * faceup and `favor` favor. Ancient City prints 2 defense dice. */
  private def envoyAttackerWithRivalAtOrigin(favor: Int): Board =
    val base = withEnemyAtOrigin(rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7)))
    on(withAdviser(base, card, Orientation.FaceUp))(
      _.favor(base.actor, favor).favor(base.rival, 0))

  /** The rival rules the origin with two warbands and the Oathkeeper title,
    * stands there, and holds Peace Envoy faceup and 5 favor. The actor has
    * no favor. */
  private def envoyDefenderAtOrigin: Board =
    val base = withEnemyAtOrigin(rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7)))
    on(withAdviserFor(base, base.rival, card, Orientation.FaceUp))(
      _.favor(base.rival, 5).favor(base.actor, 0))

  test("the attacker places one favor and gives one per defense die, and " +
      "the defender is not asked"):
    val b = envoyAttackerWithRivalAtOrigin(favor = 5)
    val run = commit(rules(losingDice), b, 2)
    assertEquals(Look(run.state).diceInPool(CampaignIds.defensePool), 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(Look(picked.state).adviserTokens(b.actor, card), Tokens(1, 0))
    assertEquals(Look(picked.state).favor(b.actor), 2)
    assertEquals(Look(picked.state).favor(b.rival), 2)
    assert(!awaits(picked, b.rival, CampaignIds.defenderPlan))
    assertEquals(said(PeaceEnvoy, picked.events), Vector(NoteText.Said(
      "gave", s"Gave 2 favor to ${b.rival.value}.", covers = false)))

  test("its price counts the gift"):
    val b = envoyAttackerWithRivalAtOrigin(favor = 5)
    val price = commit(rules(losingDice), b, 2).options(b.actor).collectFirst {
      case priced: DecisionOption.Priced if priced.ref == ref => priced.price }
    assertEquals(price.map(_.favor), Some(3))

  test("a user who cannot give it all sees the shortfall"):
    val b = envoyAttackerWithRivalAtOrigin(favor = 2)
    val run = commit(rules(losingDice), b, 2)
    assertEquals(run.withheld(b.actor, ref),
      Some(OptionReason.Written("Needs 3 favor; you have 2")))

  test("it is withheld unless the enemy's pawn is at the user's site"):
    // The rival rules the origin but stands elsewhere.
    val apart = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val away = on(withAdviser(apart, card, Orientation.FaceUp))(
      _.favor(apart.actor, 5))
    assertEquals(commit(rules(losingDice), away, 2).withheld(away.actor, ref),
      Some(OptionReason.Written(PeaceEnvoy.Apart)))
    // Bandits have no pawn.
    val base = board(warbands = 5, supply = 7)
    val bandits = on(withAdviser(base, card, Orientation.FaceUp))(
      _.favor(base.actor, 5))
    assertEquals(
      commit(rules(losingDice), bandits, 2).withheld(bandits.actor, ref),
      Some(OptionReason.Written(PeaceEnvoy.Apart)))

  test("it is used with no other plan"):
    val b = withAdviser(envoyAttackerWithRivalAtOrigin(favor = 5), longbows,
      Orientation.FaceUp)
    val run = commit(rules(losingDice), b, 2)
    // Longbows is withheld once Peace Envoy is chosen, so nothing is asked.
    assert(!awaits(run.pick(b.actor, CampaignIds.attackerPlan, ref), b.actor,
      CampaignIds.attackerPlan))
    val other = run.pick(b.actor, CampaignIds.attackerPlan, longbowsRef)
    assertEquals(other.withheld(b.actor, ref), Some(OptionReason.Noted(
      PeaceEnvoy.id, PeaceEnvoy.notWithOthers(source))))

  test("a defender gives the attacker one favor per defense die"):
    val b = envoyDefenderAtOrigin
    val run = commit(rules(winningDice), b, 4).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
    assertEquals(Look(run.state).favor(b.actor), 2)
    // Five, less the favor placed (settled off turn) and the two given.
    assertEquals(Look(run.state).favor(b.rival), 2)
    assertEquals(said(PeaceEnvoy, run.events), Vector(NoteText.Said(
      "gave-off-turn",
      s"${b.rival.value} gave 2 favor to ${b.actor.value}.",
      covers = false)))

  test("its reasons read as ruled"):
    def sentence(note: PowerNote): String =
      NoteText.sentence(PeaceEnvoy.noteKeys, note)
    assertEquals(sentence(PeaceEnvoy.notWithOthers(source)),
      "Cannot be used with other battle plans.")
    assertEquals(sentence(PeaceEnvoy.noOthers(source)),
      "No other battle plans.")
    assertEquals(sentence(PeaceEnvoy.decided(source)),
      "The battle is decided.")
```

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.PeaceEnvoySuite"`
Expected: compile failure, `Not found: PeaceEnvoy`.

- [ ] **Step 3: Price a favor `Give`**

In `src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala`, replace the doc's first sentence block

```scala
/** What a battle plan costs its user, for the option that offers it. The payments
  * are read from the operations a dry run of the plan's application recorded,
  * not from the offer, so a power that adds to the cost (a surcharge on the
  * enemy's plans) shows in it: a payment is a `PayCost` whether it is placed or
  * burnt, and a secret paid by turning it facedown is a `FlipSecrets`.
```

with

```scala
/** What a battle plan costs its user, for the option that offers it. The payments
  * are read from the operations a dry run of the plan's application recorded,
  * not from the offer, so a power that adds to the cost (a surcharge on the
  * enemy's plans) shows in it: a payment is a `PayCost` whether it is placed or
  * burnt, a secret paid by turning it facedown is a `FlipSecrets`, and favor
  * the plan gives another player (Peace Envoy's gift) is a `Give`.
```

and in `of`, before `      case (price, _) => price`, insert:

```scala
      case (price, Give(Piece.Favor(count), _, _, _, _)) =>
        price.copy(favor = price.favor + count)
```

- [ ] **Step 4: Move the card and write the power**

Delete the `PeaceEnvoyCard` block from `src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala`, and add `PeaceEnvoyCard` to the campaign import in `NewFoundations.scala` (see "Moving a card out of holding").

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, PrintedPower}
import oathdigital.gameplay.actions.campaign.{CampaignAnswers, CampaignIds}
import oathdigital.gameplay.powerresolver.{OptionRestriction, PowerCtx}
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object PeaceEnvoyCard extends Denizen(DenizenId("125"), "Peace Envoy", Suit.Order):
  val power = PrintedPower(PowerId("denizen.peace-envoy"),
    persistent = false, cost = Cost(favor = 1),
    text = "You **cannot** use other battle plans. If your enemy's pawn " +
      "is at your site, give them one [favor] per [defense-die] in " +
      "the pool. You're victorious now. **Ignore** killing " +
      "warbands.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Peace Envoy (card 125), a battle plan for either side: "[favor] You
  * cannot use other battle plans. If your enemy's pawn is at your site,
  * give them one [favor] per [defense-die] in the pool. You're victorious
  * now. Ignore killing warbands."
  *
  * One favor is placed onto the card. The gift is part of the plan's
  * application: one favor per die the defense pool holds then goes from the
  * user's board to the enemy's. The plan window's dry run therefore prices
  * it, and a user who cannot give it all sees the plan unavailable with the
  * shortfall. The shortfall counts the favor already placed.
  *
  * It is withheld, with "Your enemy's pawn must be at your site", unless
  * both pawns stand at one site. Bandits have no pawn, so it is never used
  * by or against them. It is used with no other plan (`SolePlan`). When the
  * attacker chose it, every defender plan is withheld with "Peace Envoy:
  * The battle is decided.", so the defender is not asked.
  *
  * The gift writes "Gave {2 favor} to {Blue}.", or, for a defender, "{Blue}
  * gave {2 favor} to {Red}.".
  */
case object PeaceEnvoy extends BattlePlan:
  val cardId: DenizenId = PeaceEnvoyCard.id
  val id: PowerId = PeaceEnvoyCard.power.id
  /** The favor placed onto the card. */
  val Favor: Int = 1
  /** Why the plan is shown but not offered. */
  val Apart: String = "Your enemy's pawn must be at your site"
  /** "Cannot be used with other battle plans." */
  val notWithOthers: NoteKey = NoteKey("not-with-others",
    Vector(NotePart.Text("Cannot be used with other battle plans.")))
  /** "No other battle plans." */
  val noOthers: NoteKey = NoteKey("no-others",
    Vector(NotePart.Text("No other battle plans.")))
  /** "The battle is decided.", on the defender's plans. */
  val decided: NoteKey = NoteKey("decided",
    Vector(NotePart.Text("The battle is decided.")))
  /** "Gave {2 favor} to {Blue}." */
  val gave: NoteKey = NoteKey("gave", Vector(NotePart.Text("Gave "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "{Blue} gave {2 favor} to {Red}.", for a defender. */
  val gaveOffTurn: NoteKey = NoteKey("gave-off-turn", Vector(NotePart.Arg(0),
    NotePart.Text(" gave "), NotePart.Arg(1), NotePart.Text(" to "),
    NotePart.Arg(2), NotePart.Text(".")))

  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def noteKeys: Vector[NoteKey] = Vector(PeaceEnvoy.notWithOthers,
    PeaceEnvoy.noOthers, PeaceEnvoy.decided, PeaceEnvoy.gave,
    PeaceEnvoy.gaveOffTurn)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map { source =>
      val offer = CampaignPlanOffer(source,
        "Peace Envoy: give one favor per defense die and win",
        Vector(CampaignPlanCost.Favor(PeaceEnvoy.Favor)), Vector.empty,
        alone = true)
      parties(context) match
        case Some((user, enemy)) => offer.copy(effects = Vector(
          CampaignPlanEffect.Run(Vector(gift(user, enemy),
            giftNote(context.side, user, enemy)))))
        case None => offer.copy(withheld =
          Some(OptionReason.Written(PeaceEnvoy.Apart)))
    }

  override def restrictions: Map[PowerWindow, Vector[OptionRestriction]] =
    val sole = SolePlan.restrictions(cardRef, sides,
      PowerSourceRef.Card(cardId), PeaceEnvoy.notWithOthers,
      PeaceEnvoy.noOthers)
    val window = PowerWindow.CampaignDefenderBattlePlans
    sole.updated(window, sole.getOrElse(window, Vector.empty) :+ settled)

  /** Every defender plan, once the attacker chose this one. */
  private def settled: OptionRestriction = OptionRestriction(
    (ctx, _) => Option.when(attackerChose(ctx))(
      OathViolation.CampaignUnavailable("Peace Envoy decided the battle")),
    (ctx, _) => Option.when(attackerChose(ctx))(
      PeaceEnvoy.decided(PowerSourceRef.Card(cardId))))

  private def attackerChose(ctx: PowerCtx): Boolean = CampaignAnswers.picks(
    PendingTree(Vector.empty, ctx.answered), CampaignIds.attackerPlan)
    .contains(cardRef)

  /** The user and the enemy, when both are players whose pawns stand at
    * one site. */
  private def parties(context: PlanContext): Option[(PlayerId, PlayerId)] =
    val enemy = context.enemy match
      case CampaignDefender.Player(player) => Some(player)
      case CampaignDefender.Bandits => None
    for
      user <- context.user
      other <- enemy
      site <- pawnSite(context.ready, user)
      if pawnSite(context.ready, other).contains(site)
    yield (user, other)

  private def pawnSite(ready: ReadyGame, player: PlayerId): Option[SiteId] =
    ready.game.current.players.find(_.player == player).flatMap(_.pawnSite)

  /** One favor per die in the defense pool, from `user` to `enemy`. It
    * runs after the payment, so the favor placed onto the card is added
    * back into the shortfall it reports. */
  private def gift(user: PlayerId, enemy: PlayerId): Operation =
    BuildOps((ready, _) => {
      val dice = ready.game.current.rollPools.get(CampaignIds.defensePool)
        .fold(0)(_.count)
      val held = ready.game.current.players.find(_.player == user)
        .fold(0)(_.board.favor)
      if dice == 0 then Right(Vector.empty)
      else if held < dice then Left(OathViolation.InsufficientFavor(
        PeaceEnvoy.Favor + dice, PeaceEnvoy.Favor + held))
      else Right(Vector[CoreOperation](Give(Piece.Favor(dice), user,
        Location.PlayArea(user), Location.PlayArea(enemy), required = true)))
    })

  /** The gift's line, from what the enemy gained. A defender is named. */
  private def giftNote(side: CampaignPlanSide, user: PlayerId,
      enemy: PlayerId): Note = Note(id, states => states.previous
    .map(NoteSupport.favor(_, enemy)).filter(_ > 0).map { count =>
      val source = PowerSourceRef.Card(cardId)
      val amount = NoteArg.Amount(count, NoteUnit.Favor)
      side match
        case CampaignPlanSide.Attacker =>
          PeaceEnvoy.gave(source, amount, NoteArg.Player(enemy))
        case CampaignPlanSide.Defender => PeaceEnvoy.gaveOffTurn(source,
          NoteArg.Player(user), amount, NoteArg.Player(enemy))
    })
```

- [ ] **Step 5: Register it**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.powerresolver.ContributingPower

/** The battle plans that rewrite the Campaign around them (catalog batch
  * 6, slice 2), registered together: Peace Envoy decides the battle, and
  * Wild Mounts replaces the discard of Nomad plans. Each names its card, so
  * all are always present.
  */
object PlanRewrites:
  def forCatalog(catalog: ExecutableCatalog): Vector[ContributingPower] =
    Vector(PeaceEnvoy)
```

In `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`:

- Make the campaign import

```scala
import oathdigital.gameplay.powers.campaign.{BattlePlans, PlanRestrictions,
  PlanRewrites, PlanRules, SimplePlans, VowOfPeaceContribution}
```

- After `      PlanRestrictions.forCatalog(catalog) ++`, insert `      PlanRewrites.forCatalog(catalog) ++`.

Task 4 uses `catalog` in `forCatalog`. Until then the parameter is unused, which `-Wunused` does not check for parameters.

- [ ] **Step 6: Run the suite**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.PeaceEnvoySuite"`
Expected: 7 tests pass.

If the shortfall test reads another sentence, check that the dry run's `Left` reaches `PlanPrice.shortfall`, as Insect Swarm's `InsufficientFavor` does. Do not change `shortfall`.

- [ ] **Step 7: Run the Campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.actions.campaign.*"`
Expected: pass. No other plan's dry run records a favor `Give`, so no other price changes.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/actions/campaign/PlanPrice.scala src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/main/scala/oathdigital/catalog/holding/HoldingOrderDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoySuite.scala
git commit -m "feat(powers): Peace Envoy gives favor per defense die and is used alone"
```

---

### Task 3: Peace Envoy decides the battle

Nothing is rolled or sacrificed, the user's side wins, and no warband dies.

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala` (doc)
- Modify: `src/main/scala/oathdigital/model/CampaignTypes.scala` (doc)
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoySuite.scala`

**Interfaces:**
- Consumes:
  - `LossRewrite.apply(children)(f: CoreOperation => Vector[CoreOperation])`
  - `BattlePlan#wrapping`
  - Task 2's `PeaceEnvoy`
- Produces:
  - `VictoriousNow.wrapping(line: PlanUse => Note): Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]]`
  - `PeaceEnvoy.won` and `PeaceEnvoy.wonOffTurn: NoteKey`

- [ ] **Step 1: Add the failing tests**

In `PeaceEnvoySuite.scala`, add these helpers after `envoyDefenderAtOrigin`:

```scala
  /** Whether `ops` kill any warband from `player`'s board. */
  private def killsFromBoard(ops: Vector[CoreOperation], player: PlayerId)
      : Boolean = ops.exists {
    case Kill(Piece.Warbands(_, _),
        PositionedLocation(Location.PlayArea(`player`), _)) => true
    case _ => false
  }
```

Then append these tests at the end of the class:

```scala
  test("an attacker wins a Conquest without a roll, and the defender's " +
      "warbands all return to their board"):
    val b = envoyAttackerWithRivalAtOrigin(favor = 5)
    val done = commit(rules(losingDice), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(done.result.attackFaces, Vector.empty)
    assertEquals(done.result.defenseFaces, Vector.empty)
    assertEquals(done.result.sacrificed, 0)
    assertEquals(Look(done.state).boardWarbands(b.rival),
      Look(b.ready).boardWarbands(b.rival) + 2)
    assertEquals(Look(done.state).forces(b.origin), SiteForces.Empty)
    assert(!killsFromBoard(done.ops, b.actor))
    assertEquals(said(PeaceEnvoy, done.events), Vector(
      NoteText.Said("gave", s"Gave 2 favor to ${b.rival.value}.",
        covers = false),
      NoteText.Said("won", "Won the battle without a roll.",
        covers = false)))

  test("a defender wins without a roll, and the attacker loses no warband"):
    val b = envoyDefenderAtOrigin
    val done = commit(rules(winningDice), b, 4).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(done.result.attackFaces, Vector.empty)
    assert(!killsFromBoard(done.ops, b.actor))
    assertEquals(Look(done.state).boardWarbands(b.actor), 5)
    assertEquals(said(PeaceEnvoy, done.events).map(_.text), Vector(
      s"${b.rival.value} gave 2 favor to ${b.actor.value}.",
      s"${b.rival.value} won the battle without a roll."))

  test("a Raid won with it kills none of the defender's board"):
    val raid = raidBoard(defenderWarbands = 3)
    val b = on(withAdviser(raid.board, card, Orientation.FaceUp))(
      _.favor(raid.board.actor, 5))
    val done = commit(rules(losingDice), b, 2, raid = true)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assert(!killsFromBoard(done.ops, b.rival))
    assertEquals(Look(done.state).boardWarbands(b.rival), 3)
```

The Raid may stop at a later Raid decision after `finishPlanWindows`. The losses have run by then, so the assertions hold.

- [ ] **Step 2: Run the suite to see them fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.PeaceEnvoySuite"`
Expected: the three new tests fail. For example, `done.winner` is `Some(false)` after the losing roll. The seven Task 2 tests still pass.

- [ ] **Step 3: Write `VictoriousNow`**

Create `src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** "You're victorious now" (Peace Envoy; Hearts and Minds can reuse it):
  * a used plan decides the battle for its user's side. Nothing is rolled
  * and nothing is sacrificed: the attack roll, the sacrifice decision and
  * the defense roll each fold to no children. The result is recorded as
  * usual from the empty rolls, then rewritten so that the user's side
  * wins, and `line` is written after it.
  *
  * Each piece is a `wrapping` of the plan, so it acts only in a Campaign
  * whose answered plan picks chose the plan, and the walk stays a pure
  * function of its answers. The plan's losses are its own.
  */
private[campaign] object VictoriousNow:
  def wrapping(line: PlanUse => Note)
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] =
    Map(
      PowerWindow.CampaignAttackRoll -> unrolled,
      PowerWindow.CampaignSacrificeSelection -> unrolled,
      PowerWindow.CampaignDefenseRoll -> unrolled,
      PowerWindow.CampaignAfterOutcome -> ((use, children) =>
        recorded(use.side, children) :+ line(use)))

  private val unrolled: (PlanUse, Vector[Operation]) => Vector[Operation] =
    (_, _) => Vector.empty

  /** `children`, with the recorded result won by `winner`'s side. */
  private def recorded(winner: CampaignPlanSide, children: Vector[Operation])
      : Vector[Operation] = LossRewrite(children) {
    case RecordCampaignResult(result) => Vector(RecordCampaignResult(
      result.copy(attackerWins = winner == CampaignPlanSide.Attacker)))
    case other => Vector(other)
  }
```

- [ ] **Step 4: Use it in Peace Envoy, and spare the warbands**

In `PeaceEnvoy.scala`:

1. After the `gaveOffTurn` key, add:

```scala
  /** "Won the battle without a roll." */
  val won: NoteKey = NoteKey("won",
    Vector(NotePart.Text("Won the battle without a roll.")))
  /** "{Blue} won the battle without a roll.", for a defender. */
  val wonOffTurn: NoteKey = NoteKey("won-off-turn", Vector(NotePart.Arg(0),
    NotePart.Text(" won the battle without a roll.")))
```

2. Add `PeaceEnvoy.won, PeaceEnvoy.wonOffTurn` to the end of `noteKeys`.

3. After `restrictions`, add:

```scala
  override def wrapping
      : Map[PowerWindow, (PlanUse, Vector[Operation]) => Vector[Operation]] =
    VictoriousNow.wrapping(victory).updated(PowerWindow.CampaignLosses,
      (use: PlanUse, losses: Vector[Operation]) => spared(use, losses))

  /** The victory's line. A defender is named. */
  private def victory(use: PlanUse): Note = Note(id, _ => {
    val source = PowerSourceRef.Card(cardId)
    use.side match
      case CampaignPlanSide.Attacker => Some(PeaceEnvoy.won(source))
      case CampaignPlanSide.Defender => use.user.map(user =>
        PeaceEnvoy.wonOffTurn(source, NoteArg.Player(user)))
  })

  /** The losses, killing no warband. No kill from a board is left. After a
    * Conquest the attacker won, the warbands at the targets still leave
    * them, so the victor can place warbands, but all of them return to the
    * defender's board, not half. Read when the losses window is folded,
    * before the losses run. */
  private def spared(use: PlanUse, losses: Vector[Operation])
      : Vector[Operation] =
    val atTargets = use.result.fold(0)(result => result.targetSites
      .flatMap(use.ready.game.current.map.sites.get).map(_.forces).collect {
        case SiteForces.Occupied(_, count) => count }.sum)
    LossRewrite(losses) {
      case Kill(Piece.Warbands(_, _),
          PositionedLocation(Location.PlayArea(_), _)) => Vector.empty
      case move @ Move(Piece.Warbands(kind, _),
          PositionedLocation(Location.WarbandBank(_), _),
          PositionedLocation(Location.PlayArea(_), _), None) =>
        Vector(move.copy(piece = Piece.Warbands(kind, atTargets)))
      case other => Vector(other)
    }
```

4. Extend the class doc. After the paragraph that ends `so the defender is not asked.`, add:

```scala
  * Once used, it decides the battle (`VictoriousNow`): nothing is rolled
  * or sacrificed, and its user's side wins, with "Won the battle without
  * a roll.", or, for a defender, "{Blue} won the battle without a roll.".
  * The losses kill no warband. After a Conquest the attacker wins with it,
  * the defender's warbands at the targets all return to their board.
  *
```

- [ ] **Step 5: Update the two docs**

In `src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala`, replace

```scala
  * Billowing Fog and Traveling Doctor rewrite the losses with it. `counted`
```

with

```scala
  * Billowing Fog, Traveling Doctor and Peace Envoy rewrite the losses with
  * it, and `VictoriousNow` the recorded result. `counted`
```

In `src/main/scala/oathdigital/model/CampaignTypes.scala`, replace

```scala
  * `attackerWins` records that fact: it is true for an attacker victory and
  * false for a defender victory, whichever side a battle plan's user is on.
```

with

```scala
  * `attackerWins` records that fact: it is true for an attacker victory and
  * false for a defender victory, whichever side a battle plan's user is on.
  * A plan that decides the battle (Peace Envoy) sets it whatever the
  * scores, with nothing rolled.
```

- [ ] **Step 6: Run the suite and the Campaign suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.actions.campaign.* oathdigital.application.gamelog.*"`
Expected: pass. `PeaceEnvoySuite` has 10 tests.

If the attacker's test parks on the sacrifice decision, check two things:

- the `CampaignSacrificeSelection` wrapping folds the `Decide` leaf to nothing, as Vow of Peace's Transform does;
- `PlanUse.chosen` finds the attacker's pick there.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/VictoriousNow.scala src/main/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoy.scala src/main/scala/oathdigital/gameplay/powers/campaign/LossRewrite.scala src/main/scala/oathdigital/model/CampaignTypes.scala src/test/scala/oathdigital/gameplay/powers/campaign/PeaceEnvoySuite.scala
git commit -m "feat(powers): Peace Envoy decides the battle and kills no warbands"
```

---

### Task 4: Wild Mounts

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/campaign/WildMounts.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala`
- Create: `src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala`
- Modify: `src/test/scala/oathdigital/gameplay/powers/campaign/GreatCrusadeSuite.scala`

**Interfaces:**
- Consumes:
  - `PlanUse.chosen`
  - `PlanDiscard.denizen`
  - `RuledCards.of(catalog, ready, ruler, suit): Vector[CardId]`
  - `PowerAnswers.one`
- Produces:
  - `PlanDiscard.Marked(card: DenizenId, user: Option[PlayerId], children: Vector[Operation]) extends Operation`
  - `BattlePlan#rewrites: Map[PowerWindow, Transform]`, empty by default
  - `WildMounts.forCatalog(catalog): WildMounts`, plus `WildMounts.id`, `decisionId`, `skip`, `swapped`, `swappedOffTurn`, `Priority` and `BanditDiscards`

- [ ] **Step 1: Swap Great Crusade's filler**

Wild Mounts becomes a battle plan, so `GreatCrusadeSuite`'s `planless(...)` check would fail on it. In `src/test/scala/oathdigital/gameplay/powers/campaign/GreatCrusadeSuite.scala`, replace

```scala
  private val nomads = planless("Hospitality", "Vow of Kinship", "Wild Mounts")
```

with

```scala
  private val nomads = planless("Hospitality", "Vow of Kinship",
    "Spell Breaker")
```

Spell Breaker is a site-only Nomad card, and the suite places `nomads(2)` at a site.

- [ ] **Step 2: Write the failing suite**

Create `src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look}

/** Wild Mounts: a free plan that lets its user discard one Beast card they
  * rule instead of their Nomad battle plans at the end of the Campaign. */
class WildMountsSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Wild Mounts")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val horseArchers = CatalogNames.denizen("Horse Archers")
  private val lancers = CatalogNames.denizen("Lancers")
  private val stormCaller = CatalogNames.denizen("Storm Caller")
  private val mountedPatrol = CatalogNames.denizen("Mounted Patrol")
  private val smallFriends = planlessCard("Small Friends")
  private val python = planlessCard("Giant Python")
  private val newGrowth = planlessCard("New Growth")

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    WildMounts.id, Vector(WildMounts.swapped, WildMounts.swappedOffTurn),
    run.events)

  private def swapped(beast: DenizenId): NoteText.Said = NoteText.Said(
    "swapped", s"Discarded ${beast.value} instead of the battle plans.",
    covers = false)

  /** The actor holds Wild Mounts, Small Friends (a Beast card) and `plans`
    * faceup. Two bandits rule the origin. */
  private def mountsBeastAndPlansHeldByActor(plans: DenizenId*): Board =
    (Vector(card, smallFriends) ++ plans).foldLeft(
      board(warbands = 5, supply = 7))((b, id) =>
      withAdviser(b, id, Orientation.FaceUp))

  /** The actor chooses Wild Mounts, then `plans`, and every window is
    * finished: a won Conquest, nothing placed. */
  private def attackerChoosesMountsThen(b: Board, plans: DenizenId*): Run =
    plans.foldLeft(commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref))((run, plan) =>
      run.pick(b.actor, CampaignIds.attackerPlan,
        DecisionOptionRef.Denizen(plan))).finishPlanWindows

  test("with no Nomad plan to discard, nothing is asked"):
    val b = mountsBeastAndPlansHeldByActor()
    val done = attackerChoosesMountsThen(b)
    assert(done.winner.nonEmpty)
    assert(!awaits(done, b.actor, WildMounts.decisionId))
    assert(Look(done.state).advisers(b.actor).contains(smallFriends))
    assertEquals(lines(done), Vector.empty)

  test("a Nomad plan's discard is replaced by a Beast card the user rules"):
    val b = mountsBeastAndPlansHeldByActor(horseArchers)
    val asked = attackerChoosesMountsThen(b, horseArchers)
    assert(awaits(asked, b.actor, WildMounts.decisionId))
    assertEquals(asked.options(b.actor).map(_.ref),
      Vector(DecisionOptionRef.Denizen(smallFriends), WildMounts.skip))
    val done = asked.pick(b.actor, WildMounts.decisionId,
      DecisionOptionRef.Denizen(smallFriends))
    assert(Look(done.state).inRegionalDiscard(smallFriends))
    assert(Look(done.state).advisers(b.actor).contains(horseArchers))
    assertEquals(lines(done), Vector(swapped(smallFriends)))

  test("Skip Wild Mounts discards the plans as usual"):
    val b = mountsBeastAndPlansHeldByActor(horseArchers)
    val done = attackerChoosesMountsThen(b, horseArchers)
      .pick(b.actor, WildMounts.decisionId, WildMounts.skip)
    assert(Look(done.state).inRegionalDiscard(horseArchers))
    assert(Look(done.state).advisers(b.actor).contains(smallFriends))
    assertEquals(lines(done), Vector.empty)

  test("one Beast card replaces two Nomad plans"):
    val b = mountsBeastAndPlansHeldByActor(horseArchers, lancers)
    val done = attackerChoosesMountsThen(b, horseArchers, lancers)
      .pick(b.actor, WildMounts.decisionId,
        DecisionOptionRef.Denizen(smallFriends))
    val advisers = Look(done.state).advisers(b.actor)
    assert(advisers.contains(horseArchers) && advisers.contains(lancers))
    assert(Look(done.state).inRegionalDiscard(smallFriends))

  test("a user who rules no Beast card is not asked"):
    val b = Vector(card, horseArchers).foldLeft(
      board(warbands = 5, supply = 7))((b, id) =>
      withAdviser(b, id, Orientation.FaceUp))
    val done = attackerChoosesMountsThen(b, horseArchers)
    assert(!awaits(done, b.actor, WildMounts.decisionId))
    assert(Look(done.state).inRegionalDiscard(horseArchers))

  test("a defender is asked, and the line names them"):
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val b = Vector(card, stormCaller, smallFriends).foldLeft(base)(
      (b, id) => withAdviserFor(b, base.rival, id, Orientation.FaceUp))
    val asked = commit(rules(losingDice), b, 4).finishAttackerPlans(b.actor)
      .pick(b.rival, CampaignIds.defenderPlan, ref)
      .pick(b.rival, CampaignIds.defenderPlan,
        DecisionOptionRef.Denizen(stormCaller)).finishPlanWindows
    assert(awaits(asked, b.rival, WildMounts.decisionId))
    val done = asked.pick(b.rival, WildMounts.decisionId,
      DecisionOptionRef.Denizen(smallFriends))
    assert(Look(done.state).advisers(b.rival).contains(stormCaller))
    assertEquals(lines(done), Vector(NoteText.Said("swapped-off-turn",
      s"${b.rival.value} discarded ${smallFriends.value} instead of the " +
        "battle plans.", covers = false)))

  test("bandits discard the first Beast card they rule, in map order, " +
      "instead of two Nomad plans"):
    val base = board(extras = 2, warbands = 5, supply = 7)
    val first = base.extras(0)
    val second = base.extras(1)
    // Wild Mounts, Mounted Patrol and Storm Caller are free defender plans
    // the bandits apply at the origin.
    val b = withSiteCard(withSiteCard(Vector(card, mountedPatrol,
      stormCaller).foldLeft(base)((b, id) => withSiteCard(b, base.origin,
      id)), second, newGrowth), first, python)
    val inOrder = b.ready.game.current.map.inPlay
    val expected = if inOrder.indexOf(first) < inOrder.indexOf(second)
      then python else newGrowth
    val done = commit(rules(losingDice), b, 4).finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(expected))
    assert(!Look(done.state).inRegionalDiscard(
      if expected == python then newGrowth else python))
    val atOrigin = Look(done.state).cardsAt(base.origin)
    assert(atOrigin.contains(mountedPatrol) && atOrigin.contains(stormCaller))
    assertEquals(lines(done), Vector(swapped(expected)))

  test("bandits with one Nomad plan discard it as usual"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val b = withSiteCard(withSiteCard(withSiteCard(base, base.origin, card),
      base.origin, mountedPatrol), base.extras.head, python)
    val done = commit(rules(losingDice), b, 4).finishPlanWindows
    assert(Look(done.state).inRegionalDiscard(mountedPatrol))
    assert(!Look(done.state).inRegionalDiscard(python))
    assertEquals(lines(done), Vector.empty)
```

If `planlessCard` fails for Giant Python or New Growth because one now has an implemented power, use another unimplemented Beast denizen from `HoldingBeastDenizens.scala`, such as Wild Allies.

- [ ] **Step 3: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.WildMountsSuite"`
Expected: compile failure, `Not found: WildMounts`.

- [ ] **Step 4: Mark the plan discards**

In `src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala`:

1. In the object doc, replace

```scala
  * `afterCampaign` is the discard of a plan that says "At end, discard" (Horse
  * Archers, Storm Caller), with its line.
```

with

```scala
  * `afterCampaign` is the discard of a plan that says "At end, discard" (Horse
  * Archers, Storm Caller), with its line, marked so that a power can replace
  * it (Wild Mounts).
```

2. After `val discarded`, add:

```scala
  /** A discard `afterCampaign` appended, marked with the plan's `card` and
    * its `user`, `None` for a bandit defender. A power that replaces the
    * discard of a plan (Wild Mounts) finds it by this mark among the last
    * children of the Campaign. It runs its children as they are. */
  final case class Marked(card: DenizenId, user: Option[PlayerId],
      children: Vector[Operation]) extends Operation
```

3. Replace the body of `afterCampaign`

```scala
      card: DenizenId): Vector[Operation] = Vector(
    denizen(catalog, use.user.getOrElse(use.actor), card),
    Note(power, states => Option.when(states.previous.exists {
      case (before, after) => !inDiscard(before, card) && inDiscard(after, card)
    })(discarded(PowerSourceRef.Card(card)))))
```

with

```scala
      card: DenizenId): Vector[Operation] = Vector(Marked(card, use.user,
    Vector(denizen(catalog, use.user.getOrElse(use.actor), card),
      Note(power, states => Option.when(states.previous.exists {
        case (before, after) =>
          !inDiscard(before, card) && inDiscard(after, card)
      })(discarded(PowerSourceRef.Card(card)))))))
```

Then run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.*"`
Expected: everything passes except `WildMountsSuite`, which does not compile yet. To get a clean run, move `WildMountsSuite.scala` aside for this run and restore it afterwards. Every plan that discards still discards, with its line.

- [ ] **Step 5: Add the `rewrites` hook**

In `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`:

1. In the trait doc's bullet list, after the `restrictions` bullet, add:

```scala
  *  - `rewrites` change a window's children whether or not the plan was
  *    chosen. Such a rewrite keeps the children's shape the same on every
  *    fold and learns inside a `Branch`, at walk time, whether the plan was
  *    used, which also sees a bandit's use (Wild Mounts).
```

2. After `def restrictions ... = Map.empty`, add:

```scala
  /** What the plan's power does to a window's children, chosen or not. */
  def rewrites: Map[PowerWindow, Transform] = Map.empty
```

3. In `contributions`, replace

```scala
    (offers.keySet ++ afterwards.keySet ++ around.keySet ++
      restrictions.keySet).map(window =>
      window -> (offers.getOrElse(window, Vector.empty) ++
        afterwards.getOrElse(window, Vector.empty) ++
        around.getOrElse(window, Vector.empty) ++
        restrictions.getOrElse(window, Vector.empty))).toMap
```

with

```scala
    (offers.keySet ++ afterwards.keySet ++ around.keySet ++ rewrites.keySet ++
      restrictions.keySet).map(window =>
      window -> (offers.getOrElse(window, Vector.empty) ++
        afterwards.getOrElse(window, Vector.empty) ++
        around.getOrElse(window, Vector.empty) ++
        rewrites.get(window).toVector ++
        restrictions.getOrElse(window, Vector.empty))).toMap
```

`Transform` is already imported there.

- [ ] **Step 6: Move the card and write the power**

Delete the `WildMountsCard` block from `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala`, and add `WildMountsCard` to the campaign import in `NewFoundations.scala`.

Create `src/main/scala/oathdigital/gameplay/powers/campaign/WildMounts.scala`:

```scala
package oathdigital.gameplay.powers.campaign

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.actions.campaign.CampaignProcedure
import oathdigital.gameplay.powerresolver.Transform
import oathdigital.gameplay.powers.{PowerAnswers, RuledCards}
import oathdigital.model._

object WildMountsCard extends Denizen(DenizenId("153"), "Wild Mounts", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.wild-mounts"),
    persistent = false, cost = Cost.free,
    text = "If you would discard any number of [suit-nomad] battle " +
      "plans, you may instead discard any one [suit-beast] card " +
      "you rule.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Wild Mounts (card 153), a battle plan for either side: "If you would
  * discard any number of [suit-nomad] battle plans, you may instead discard
  * any one [suit-beast] card you rule."
  *
  * It is free, and choosing it does nothing at the time. At the end of the
  * Campaign, its rewrite at the root strips its user's marked discards of
  * Nomad plans (`PlanDiscard.Marked`) from the plans' own steps and appends
  * one step of its own. It folds after every plan that appends a discard
  * there. That step:
  *
  *  - asks a player user who rules a Beast card, their faceup advisers and
  *    the faceup denizens at sites they rule, to discard one of them or to
  *    "Skip Wild Mounts". A Beast card chosen is discarded instead of the
  *    plans; skipping runs the plans' discards;
  *  - for a bandit user, follows the FAQ: with two or more Nomad discards,
  *    the first Beast card the bandits rule in map order is discarded
  *    instead;
  *  - otherwise runs the plans' discards.
  *
  * Whether the plan was used is read at walk time, so a bandit's use,
  * recorded during the walk, counts. The rewrite keeps the root's shape on
  * every fold, used or not.
  *
  * The swap writes "Discarded {card} instead of the battle plans.", or,
  * for a defender, "{Blue} discarded {card} instead of the battle plans.".
  */
final case class WildMounts private (catalog: ExecutableCatalog)
    extends BattlePlan:
  val cardId: DenizenId = WildMountsCard.id
  def id: PowerId = WildMounts.id
  def cardRef: DecisionOptionRef = DecisionOptionRef.Denizen(cardId)
  def sides: Set[CampaignPlanSide] =
    Set(CampaignPlanSide.Attacker, CampaignPlanSide.Defender)
  override def priority: Int = WildMounts.Priority
  override def noteKeys: Vector[NoteKey] =
    Vector(WildMounts.swapped, WildMounts.swappedOffTurn)

  def plan(context: PlanContext): Option[CampaignPlanOffer] =
    context.denizen(cardId).map(source => CampaignPlanOffer(source,
      "Wild Mounts: discard a Beast card instead of Nomad battle plans",
      Vector.empty, Vector.empty))

  override def rewrites: Map[PowerWindow, Transform] = Map(
    PowerWindow.CampaignActionEligibility -> Transform((ctx, children) =>
      children.map(withoutSwappable(ctx.activePlayer, _)) :+
        swap(ctx.activePlayer, children)))

  /** The plan, when it was used in this Campaign. */
  private def used(ready: ReadyGame, pending: PendingTree, actor: PlayerId)
      : Option[PlanUse] =
    PlanUse.chosen(ready, pending, actor, cardRef, sides, afterOutcome = true)

  /** Whether `operation` is the marked discard of a Nomad plan of `use`'s
    * user. */
  private def swappable(use: PlanUse)(operation: Operation): Boolean =
    operation match
      case marked: PlanDiscard.Marked => marked.user == use.user &&
        catalog.suitOf(marked.card).contains(Suit.Nomad)
      case _ => false

  /** `child`, without the swappable discards it selects once the plan was
    * used. Only a `Branch` selects a plan's discard. */
  private def withoutSwappable(actor: PlayerId, child: Operation): Operation =
    child match
      case branch: Branch => Branch((ready, pending) => {
        val selected = branch.select(ready, pending)
        used(ready, pending, actor).fold(selected)(use =>
          selected.filterNot(swappable(use)))
      })
      case other => other

  /** The step that asks, swaps, or runs the stripped discards. It selects
    * them again from the original steps, which read only the answers and
    * the plans' records, not what earlier discards moved. */
  private def swap(actor: PlayerId, children: Vector[Operation]): Operation =
    Branch((ready, pending) =>
      used(ready, pending, actor).fold(Vector.empty[Operation]) { use =>
        val discards = children.collect { case branch: Branch => branch }
          .flatMap(_.select(ready, pending)).filter(swappable(use))
        if discards.isEmpty then Vector.empty
        else use.user match
          case Some(user) => ask(use, user, discards,
            beasts(ready, SiteRuler.Player(user)))
          case None if discards.size >= WildMounts.BanditDiscards =>
            beasts(ready, SiteRuler.Bandits).headOption
              .fold(discards)(card => instead(use, use.actor, card))
          case None => discards
      })

  /** The faceup Beast denizens `ruler` rules, in map order. */
  private def beasts(ready: ReadyGame, ruler: SiteRuler): Vector[DenizenId] =
    RuledCards.of(catalog, ready, ruler, Suit.Beast).collect {
      case card: DenizenId => card }

  private def ask(use: PlanUse, user: PlayerId, discards: Vector[Operation],
      ruled: Vector[DenizenId]): Vector[Operation] =
    if ruled.isEmpty then discards
    else Vector(
      Decide(WildMounts.decisionId, user, DecisionQuery.ChooseOne(
        ruled.flatMap(card =>
          DecisionOption.forRef(DecisionOptionRef.Denizen(card))) :+
          DecisionOption.Button(WildMounts.skip, "Skip Wild Mounts"),
        heading = Some("Wild Mounts: discard a Beast card you rule " +
          "instead of your Nomad battle plans"))),
      Branch((_, pending) =>
        PowerAnswers.one(pending, WildMounts.decisionId) match {
          case Some(DecisionOptionRef.Denizen(card)) if ruled.contains(card) =>
            instead(use, user, card)
          case _ => discards
        }))

  /** The discard of `card` in place of the plans', by `acting`, and its
    * line. A player defender is named; bandits are no player. */
  private def instead(use: PlanUse, acting: PlayerId, card: DenizenId)
      : Vector[Operation] =
    val source = PowerSourceRef.Card(cardId)
    val line = (use.side, use.user) match
      case (CampaignPlanSide.Defender, Some(user)) => WildMounts
        .swappedOffTurn(source, NoteArg.Player(user), NoteArg.Card(card))
      case _ => WildMounts.swapped(source, NoteArg.Card(card))
    Vector(PlanDiscard.denizen(catalog, acting, card),
      Note(id, _ => Some(line)))

object WildMounts:
  val id: PowerId = WildMountsCard.power.id
  /** After every plan that appends a discard at the end of the Campaign,
    * whatever its priority (Traveling Doctor's is 1), so the rewrite sees
    * them all among the root's children. */
  val Priority: Int = 10
  /** Bandits swap only when they would discard this many Nomad plans
    * (FAQ). */
  val BanditDiscards: Int = 2
  /** Under the Campaign's prefix, so a parked question is a Campaign
    * decision. */
  val decisionId: String = CampaignProcedure.decisionPrefix + "wild-mounts"
  val skip: DecisionOptionRef.Button = DecisionOptionRef.Button("skip")
  /** "Discarded {card} instead of the battle plans." */
  val swapped: NoteKey = NoteKey("swapped", Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0),
    NotePart.Text(" instead of the battle plans.")))
  /** "{Blue} discarded {card} instead of the battle plans.", for a
    * defender. */
  val swappedOffTurn: NoteKey = NoteKey("swapped-off-turn", Vector(
    NotePart.Arg(0), NotePart.Text(" discarded "), NotePart.Arg(1),
    NotePart.Text(" instead of the battle plans.")))

  def forCatalog(catalog: ExecutableCatalog): WildMounts =
    new WildMounts(catalog)
```

- [ ] **Step 7: Register it**

In `PlanRewrites.scala`, replace `    Vector(PeaceEnvoy)` with:

```scala
    Vector(PeaceEnvoy, WildMounts.forCatalog(catalog))
```

- [ ] **Step 8: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.actions.campaign.*"`
Expected: pass. `WildMountsSuite` has 8 tests, and `GreatCrusadeSuite` passes with Spell Breaker.

If a test parks somewhere unexpected:

- An attacker's suite that stops before the question: `finishPlanWindows` answers placement with 0 and then stops at the first decision it does not know. That stop is the Wild Mounts question.
- A bandit test that swaps nothing: check that `RuledCards.of(..., SiteRuler.Bandits, ...)` lists the extra sites. Those sites are bandit-ruled by the board, and the Campaign targets only the origin.

- [ ] **Step 9: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/PlanDiscard.scala src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala src/main/scala/oathdigital/gameplay/powers/campaign/WildMounts.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanRewrites.scala src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/test/scala/oathdigital/gameplay/powers/campaign/WildMountsSuite.scala src/test/scala/oathdigital/gameplay/powers/campaign/GreatCrusadeSuite.scala
git commit -m "feat(powers): Wild Mounts discards a Beast card instead of Nomad plans"
```

---

### Task 5: Record the slice and run the gates

**Files:**
- Modify: `docs/ROADMAP.md` ("Now" paragraph; Phase - Catalog batch 6)

- [ ] **Step 1: Run the gates**

Run, in order:
1. `./sbtw "test" "frontend/test"`
2. `./sbtw frontend/fullOptJS`
3. `python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py`

Expected: all pass. The server count is the baseline plus 18, and the frontend count is unchanged.

- [ ] **Step 2: Update the roadmap**

In `docs/ROADMAP.md`:

- Delete the bullet `- [ ] Slice 2, plan rewrites: Peace Envoy, Wild Mounts.` under "### Phase - Catalog batch 6".
- In "## Now", replace `**Phase - Catalog batch 6** is in progress: slice 1\nis done.` with `**Phase - Catalog batch 6** is in progress: slices 1 and 2\nare done.`, re-wrapped at the paragraph's width.

Run `python3 scripts/check-markdown-links.py` again. Expected: pass.

- [ ] **Step 3: Commit**

```bash
git add docs/ROADMAP.md
git commit -m "docs: record catalog batch 6 slice 2"
```
