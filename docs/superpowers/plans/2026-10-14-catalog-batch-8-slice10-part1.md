# Catalog Batch 8, Slice 10, Part 1 (Power-Use Filter and Spell Breaker) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the power-use filter and implement 162 Spell Breaker: "Enemies of Spell Breaker's ruler **cannot** use powers that cost any [secret] or [secret-burnt]."

**Architecture:**

- **Slice 10 is planned in three parts.** Each ships whole cards:
  - Part 1, this plan: the power-use filter, and Spell Breaker on every path a secret-costing power is used.
  - Part 2: the placement bans and the automatic powers, with Forest Council and City Wall.
  - Part 3: Giant Python, and Roving Terror moved onto the same slot question.
- **One description of a use.** `PowerUse` says who uses a power, the card it is printed on, whether it is printed persistent, whether using it costs a secret, and whether a refusal may be asked of a ruler. A `PowerUseRestriction` refuses a use with a `PowerUseRefusal`: the restriction's power, its hover reason, and an `OperationReason` whose waiver, if any, makes it askable.
- **The engine never names a restriction.**
  - A walker power declares its restrictions as `powerUseRestrictions`, beside `operationRestrictions`, and `WalkerPowers.useRestrictions` collects them.
  - `PhasePowers` carries the same list, which `PhasePowerCatalog.default` takes from `WalkerPowerCatalog`.
  - A power states its own use through `ContributingPower.use(ctx)` and `PhasePower.costsSecret`.
- **The paths that read the filter in part 1:**
  - **The phase menu and `PhasePowerProcedure.check`:** a refused ACTION is askable or unavailable, and its start is refused.
  - **The modifier picker (`ModifierChoice`):** a refused modifier is askable or unavailable.
  - **The battle-plan window:** a refused plan is withheld with its reason. There is no consent inside a Campaign.
  - **The reroll choice:** a refused reroll is withheld, and askable outside a Campaign's own dice.
- **Consent.** A grant is remembered under `ConsentKey(restriction, user, "power.use", card)` for the turn, so it covers that card's use on every path. `PowerUseRules.refusals` drops a refusal whose grant is remembered, as `ConsentedRestriction` does for operations.

**Tech Stack:** Scala 3 on the JVM and Scala.js, munit, built through `./sbtw`.

**Spec:** Read these documents before starting:

- `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`, sections:
  - "Goal and scope";
  - "The bar" (the slice 10 bullet);
  - "Slice 10: Power-use restrictions";
  - "Testing".
- `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`: "Rules that apply to the whole batch" and the slice 10 entries.
- `docs/superpowers/specs/2026-10-08-consent-system-design.md`, "Rulings" 1 to 8.

Task 1 amends the batch 8 documents with this slice's rulings. Read them again after Task 1.

**Starts from:** `main` at `6f374302` or later. Slice 9 (`07d6f06c`) and the note-keys change (`b4a6d331`) are in. A note key is now made only by declaring it through a `NoteSet` (`model/PowerNotes.scala`), and `src/test/resources/notes/declared-keys.txt` snapshots every declared key; Task 3 adds Spell Breaker's two. Where this plan quotes code, read the file as it is on `main` and apply the change to what is there.

## Global Constraints

- **Build rules.** The build runs with `-Werror` and `-Wunused:imports,privates,locals,implicits,nowarn`, so an unused import or private member fails it. Each new file below lists the imports it needs. A refutable pattern in a `val` also fails the build, so bind each value separately.
- **File size.** Production Scala files stay at or under 800 lines (`scripts/check-architecture.py`). Check with `wc -l`. `WalkerPowerGather.scala` and `ModifierChoice.scala` are already long; if a change pushes one past 800, move the new helper into its own file in the same package.
- **Import rules:**
  - `gameplay` never imports `application`, `serialization` or `server`.
  - A walker power (anything under `gameplay/powers`) never imports `gameplay.walker`.
  - The engine (`gameplay/walker`, `gameplay/operations`) never names a power: `BackendArchitectureSuite` scans their sources for every power's name, lowercased. So no file there may contain "spellbreaker", "spell-breaker" spelled as an identifier, or any other power name.
  - Nothing under `gameplay/actions` imports `gameplay/powers`.
- **Tree addresses.** No procedure tree gains, loses or moves a node. Refused options keep their place in their decision.
- **Line width.** Scala lines stay at or under 80 characters, as in the files around them. Re-wrap any line in this plan that comes out longer.
- **Never touch the live database `var/oathdigital`.**
- **Concurrent sessions.** Other sessions commit to `main` while you work.
  - Stage explicit paths only; never `git add -A`.
  - Re-check `git log --oneline -1` before any amend, reset or rebase.
- **Worktree.** Work in a git worktree.
  - `EnterWorktree` branches from `origin`, which lags local `main`. Fast-forward the new branch to local `main` first (`git merge --ff-only main`).
  - Then symlink the main checkout's `.tooling` and `node_modules` into the worktree before the first `./sbtw`.
  - Run `ExitWorktree` with `keep` before merging into `main`.
- **Commit trailer:** the committing model's own `Co-Authored-By` line.
- **Test helper names** say what they build. A doc comment does not excuse a vague name.
- **Log lines omit the actor.** Spell Breaker writes no line: a refused power is not used.
- **Hover reasons, exactly.** The projector prefixes a power note with its card's name and a colon.
  - Plain: "Spell Breaker: Cannot use powers that cost secrets."
  - Askable: "Spell Breaker: Cannot use powers that cost secrets without ruler's consent."
- **Names, exactly:**
  - File `src/main/scala/oathdigital/gameplay/powerresolver/PowerUse.scala`: `final case class PowerUse`, `object PowerUse` with `def of`, `final case class PowerUseRefusal`, `trait PowerUseRestriction`.
  - File `src/main/scala/oathdigital/gameplay/operations/PowerUseRules.scala`: `object PowerUseRules` with `useKey`, `target`, `refusals`, `shown`.
  - `ContributingPower.use(ctx: PowerCtx): Option[PowerUse]` and `ContributingPower.powerUseRestrictions: Vector[PowerUseRestriction]`.
  - `WalkerPowers.useRestrictions`, `PhasePowers.useRestrictions`, `PhasePower.costsSecret`.
  - File `src/main/scala/oathdigital/gameplay/powers/poweruse/RulerBans.scala`: `private[powers] object RulerBans`.
  - File `src/main/scala/oathdigital/gameplay/powers/poweruse/SpellBreaker.scala`: `SpellBreakerCard` (moved unchanged from `HoldingNomadDenizens.scala`) and `case object SpellBreaker`.

---

### Task 1: Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md`
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`

- [ ] **Step 1: Replace the design's "A power-use filter" bullet in "The bar"**

Replace the bullet that starts "**A power-use filter** (slice 10)." with:

```markdown
- **A power-use filter** (slice 10, detailed at plan time). A `PowerUse`
  says who uses a power, its card, whether it is printed persistent,
  whether it costs a secret, and whether a refusal may be asked of a
  ruler. A walker power declares its `powerUseRestrictions`, which
  `WalkerPowers` and `PhasePowers` both carry, and states its own use
  through `ContributingPower.use`. The filter is read by:
  - the phase menu and `PhasePowerProcedure.check`, for phase powers and
    Hunger's forced Wake;
  - the modifier picker, for modifiers;
  - the battle-plan window and the reroll choice, which withhold a refused
    offer with its reason;
  - the `AutomaticUse` node, which judges an automatic power where its
    effect runs, and the contribution collector, which judges Keep where
    its window folds (part 2).

  A grant is remembered under the key `"power.use"` and the card, so it
  covers the card's use by that player for the turn on every path.
- **Placement bans** (slice 10, added at plan time). Forest Council, City
  Wall and Giant Python refuse a placement of favor or secrets onto a card
  as printed operation restrictions: a `PayCost` that places onto the card,
  or a `Give` onto it, with its payer or giver as the placer. An off-turn
  payment settles to the bank and is not a placement.
- **A slot question for moving a site card** (slice 10, added at plan
  time). Giant Python and Roving Terror ask one question over site slots,
  as card play does: an empty slot, or a card marked Replace.
```

- [ ] **Step 2: Add the slice's plan-time rulings to the design**

Under "## Slice 10: Power-use restrictions", after its paragraphs, add:

```markdown
### Plan-time rulings

- **Using a non-persistent power** means using any power printed
  `persistent: false`: phase powers, Hunger's forced Wake, modifiers and
  Catacombs, battle plans and their rerolls, When Played powers, and the
  automatic rules that make something happen (Tyrant, Hospitality, Keep,
  Vow of Beastkin's and Vow of Wandering's extra warbands). Restrictions,
  limits and cost changes are not uses, so they still hold: a lasting limit
  printed under a phase power's id (Assassin, Insomnia, Silver Tongue, Pied
  Piper, Vow of Obedience, Vow of Poverty), Vow of Beastkin's source limit
  and Initiation Rite. The edifice setup rules run during Setup, where
  nobody uses a power.
- **A power that costs a secret** has a secret or a burnt secret in its
  printed cost, Jinx and Spiteful Mirror's free later picks included. Ward
  of Silence and Arcane Armor pay X secrets, at least one, and count too, as
  does every battle plan Gleaming Armor taxes, the title's included: the
  title's plan is a power, though no card prints it. Initiation Rite costs no
  secret, and mustering is not a power. Unstable Summon burns from the
  Darkest Secret, which is not a cost.
- **A Beast card** is judged by `EffectiveSuit.of` for a faceup adviser,
  and by its printed suit at a site.
- **Placing favor or secrets on a card** is a cost paid onto it (a power's
  cost, a Trade's or Muster's source card, Forge, a battle plan's cost,
  Jinx) or a `Give` onto it (Whispering Leaves' target, Dissent). The placer
  is the payer or the giver. An off-turn payment settles to the bank and
  places nothing.
- **Enemies.** Every other player is an enemy of a player ruler. At a site
  bandits rule, every player is an enemy and nobody may consent. At an
  unruled site, or one the Empire rules, nobody is restricted. City Wall's
  "while at this site" reads the physical pawn, not an as-if site.
- **Consent.** Spell Breaker, Forest Council and City Wall are waivable;
  Giant Python is not ("Other cards"). Outside a Campaign every refusal they
  make is askable of the ruler: the phase menu, the modifier picker, the
  placements and the rerolls. Inside a Campaign (its modifiers, its battle
  plans and the rerolls of its dice) nobody is asked.
- **A refused automatic power** does not apply. Nobody is asked and no
  line is written. It is judged where its effect runs: Tyrant arriving at
  City Wall's site is refused. The roadmap's item 10b replaces this: the restriction's
  ruler chooses, each time, whether to deactivate the power ("Use City Wall
  to deactivate Tyrant?"), and the power's user is not asked.
- **Hover reasons**, in the consent design's format:
  - "Spell Breaker: Cannot use powers that cost secrets."
  - "Forest Council: Cannot use non-persistent powers of Beast cards." and
    "Forest Council: Cannot place favor or secrets on Beast cards."
  - "City Wall: Cannot use non-persistent powers while at its site." and
    "City Wall: Cannot place favor or secrets on cards while at its site."
  - "Giant Python: Cards at its site cannot be used for non-persistent
    powers." and "Giant Python: Cannot place favor or secrets on cards at
    its site."
  - An askable reason ends " without ruler's consent." instead of ".".
- **Giant Python's ACTION** is usable when its user holds the Darkest
  Secret through `CardPowerBanners.holdsDarkestSecret` and some other site
  can take it. It asks one question, "Giant Python: move to which slot?":
  each other site's first empty slot when it has room, and each faceup
  denizen and ruined edifice there marked Replace, judged by
  `CardSlots.judge`. A Replace discards the card the standard way; Giant
  Python then takes the site's last slot with its tokens. Its lines are
  "Moved Giant Python to {site}." and "Replaced {card} at {site}.".
- **Roving Terror** asks the same slot question with Replace options only.
- **Three plans.** Part 1 builds the filter with Spell Breaker. Part 2
  adds the placement bans and the automatic powers with Forest Council and
  City Wall. Part 3 adds Giant Python and moves Roving Terror.
```

- [ ] **Step 3: Rewrite the rulings appendix's slice 10 entry**

In `docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md`, replace everything from "## Slice 10: Power-use restrictions" up to "## Slice 11" with:

```markdown
## Slice 10: Power-use restrictions

- **162 Spell Breaker.** "Enemies of Spell Breaker's ruler cannot use
  powers that cost any [secret] or [secret-burnt]."
  - Reached: every power whose printed cost has a secret or a burnt
    secret, of any kind (ACTION, modifier, battle plan, Jinx's reroll);
    Ward of Silence and Arcane Armor, which pay X secrets; and every
    battle plan Gleaming Armor taxes, the title's plan included.
  - Not reached: Initiation Rite, Unstable Summon's burn from the Darkest
    Secret.
  - The ruler may consent outside a Campaign.
- **194 Forest Council.** "Enemies of Forest Council's ruler cannot place
  [favor] / [secret] on [suit-beast] cards or use their non-persistent
  powers." Both bans cover Beast cards anywhere: a faceup adviser by its
  effective suit, a site card by its printed suit. The ruler may consent
  outside a Campaign.
- **252 City Wall.** "While at this site, enemies of City Wall's ruler
  cannot place [favor] / [secret] on cards or use non-persistent powers."
  While an enemy's pawn is at City Wall's site, the enemy cannot place favor
  or secrets on any card or use any non-persistent power, wherever the card
  is. The ruler may consent outside a Campaign.
- **186 Giant Python.** "[secret] ACTION: If you hold the Darkest Secret,
  move Giant Python to another site, and you may discard a card there
  first." and "Other cards at this site cannot be used for non-persistent
  powers or placing [favor] / [secret]."
  - The holding is read through Yew Staff's lens.
  - The move goes to any other site, through one slot question. At a full
    site the discard is required.
  - Its restriction moves with it, and its ruler becomes the ruler of its
    new site.
  - The restriction holds for every player, its ruler included, and is
    not waivable.
- **For every card:** a non-persistent power is any power printed
  `persistent: false`, automatic or chosen; a refused automatic power does
  not apply, silently. A placement is a cost paid onto a card or a `Give`
  onto it; an off-turn payment is not one. Enemies are every player but a
  player ruler, every player at a bandit site, and nobody at an unruled or
  Empire site.
```

- [ ] **Step 4: Check the links and commit**

Run: `python3 scripts/check-markdown-links.py`
Expected: it passes.

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/superpowers/specs/2026-10-12-catalog-batch-8-rulings.md
git commit -m "docs(batch8): record slice 10's plan-time rulings"
```

---

### Task 2: The power-use seam

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powerresolver/PowerUse.scala`
- Create: `src/main/scala/oathdigital/gameplay/operations/PowerUseRules.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala` (trait `ContributingPower`)
- Modify: `src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala`
- Test: `src/test/scala/oathdigital/gameplay/operations/PowerUseRulesSuite.scala`

**Interfaces:**
- Produces:
  - `PowerUse(user: PlayerId, power: PowerId, card: Option[CardId], persistent: Boolean, costsSecret: Boolean, consent: Boolean = true, places: Boolean = false)`
  - `PowerUse.of(user: PlayerId, printed: PrintedPower, card: CardId, secret: Boolean = false, consent: Boolean = true, places: Boolean = false): PowerUse`
  - `PowerUseRefusal(power: PowerId, note: PowerNote, reason: OperationReason)`
  - `trait PowerUseRestriction { def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal]; def waiver: Option[Waiver] = None }`
  - `PowerUseRules.useKey: String = "power.use"`
  - `PowerUseRules.target(use: PowerUse): DecisionOptionRef`
  - `PowerUseRules.refusals(ready: ReadyGame, use: PowerUse, restrictions: Vector[PowerUseRestriction]): Vector[PowerUseRefusal]`
  - `PowerUseRules.shown(ready: ReadyGame, actor: PlayerId, refusals: Vector[PowerUseRefusal], consent: Boolean): OptionReason` (requires `refusals.nonEmpty`)
  - `ContributingPower.use(ctx: PowerCtx): Option[PowerUse] = None`
  - `ContributingPower.powerUseRestrictions: Vector[PowerUseRestriction] = Vector.empty`
  - `PhasePower.costsSecret: Boolean`
  - `PhasePowers(powers: Vector[PhasePower], useRestrictions: Vector[PowerUseRestriction] = Vector.empty)`
  - `WalkerPowers.useRestrictions: Vector[PowerUseRestriction]`

- [ ] **Step 1: Write the failing seam tests**

Create `src/test/scala/oathdigital/gameplay/operations/PowerUseRulesSuite.scala`:

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.{PowerUse, PowerUseRefusal,
  PowerUseRestriction}
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

/** The power-use filter's consent rules (catalog batch 8, slice 10): a
  * remembered grant lifts a refusal, and a refused option is askable only
  * when consent may lift every refusal on it. */
class PowerUseRulesSuite extends munit.FunSuite:
  private val ruled = PowerId("test.ruled-ban")
  private val banned = PowerId("test.banned-power")
  private val card = DenizenId("1")
  private val use = PowerUse(p1, banned, Some(card), persistent = false,
    costsSecret = true)
  private val covers = ConsentKey(ruled, p1, PowerUseRules.useKey,
    DecisionOptionRef.Denizen(card))
  private val notes = NoteSet(ruled)
  private val plain = notes.key("test-ban",
    Vector(NotePart.Text("Banned.")))
  private val asked = notes.key("test-ban-consent",
    Vector(NotePart.Text("Banned without ruler's consent.")))
  private val source = PowerSourceRef.Card(card)

  /** A ban on every use, waivable by p2 when `granter` names them. */
  private def banGrantedBy(granter: Option[PlayerId]): PowerUseRestriction =
    new PowerUseRestriction:
      def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
        Some(PowerUseRefusal(ruled, plain(source), OperationReason(
          "test-ban", "banned", OperationReasonKind.Impossible,
          granter.map(player => OperationWaiver(ruled, Some(player),
            covers, asked(source))))))

  private val quiet: ReadyGame = Table.start.ready

  private def remembering(answer: ConsentAnswer): ReadyGame =
    quiet.copy(consent = ConsentState(answers = Map(covers -> answer),
      turn = Some(ConsentTurn.of(quiet))))

  test("the key names the restriction's slot and the card"):
    assertEquals(PowerUseRules.target(use), DecisionOptionRef.Denizen(card))

  test("a grant remembered this turn lifts the refusal; a refusal does " +
      "not"):
    val ban = Vector(banGrantedBy(Some(p2)))
    assertEquals(PowerUseRules.refusals(remembering(ConsentAnswer.Granted),
      use, ban), Vector.empty)
    assertEquals(PowerUseRules.refusals(remembering(ConsentAnswer.Refused),
      use, ban).map(_.power), Vector(ruled))

  test("a refusal its ruler may waive is askable of them"):
    val refused = PowerUseRules.refusals(quiet, use,
      Vector(banGrantedBy(Some(p2))))
    assertEquals(PowerUseRules.shown(quiet, p1, refused, consent = true),
      OptionReason.Askable(ruled, asked(source),
        Vector(ConsentAsk(p2, Vector(ruled))), Vector(covers)))

  test("without consent, or with nobody to grant it, the plain reason is " +
      "shown"):
    val waivable = PowerUseRules.refusals(quiet, use,
      Vector(banGrantedBy(Some(p2))))
    assertEquals(PowerUseRules.shown(quiet, p1, waivable, consent = false),
      OptionReason.Noted(ruled, plain(source)))
    val bandits = PowerUseRules.refusals(quiet, use,
      Vector(banGrantedBy(None)))
    assertEquals(PowerUseRules.shown(quiet, p1, bandits, consent = true),
      OptionReason.Noted(ruled, plain(source)))
```

`ConsentState` is the consent ledger in `model/Consent.scala`; the Hall of Ministers suite builds it the same way. If its constructor differs on `main`, follow `HallOfMinistersConsentSuite.remembering`.

- [ ] **Step 2: Run the suite to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.PowerUseRulesSuite"`
Expected: compilation fails, because `PowerUse` and `PowerUseRules` do not exist.

- [ ] **Step 3: Create `PowerUse.scala`**

```scala
package oathdigital.gameplay.powerresolver

import oathdigital.catalog.PrintedPower
import oathdigital.model.{CardId, OperationReason, PlayerId, PowerId,
  PowerNote, ReadyGame}

/** One use of a power, as the power-use restrictions judge it (catalog
  * batch 8, slice 10): who uses it, the card it is printed on, whether it
  * is printed persistent, and whether using it costs a secret. `consent` is
  * whether a refusal may be asked of a ruler: never inside a Campaign
  * (consent design, ruling 2). `places` is whether its cost places favor
  * or secrets on its card, which the placement bans of part 2 read; the
  * payment settles to the bank off its user's turn, so a ban reads it only
  * on that turn.
  */
final case class PowerUse(user: PlayerId, power: PowerId,
    card: Option[CardId], persistent: Boolean, costsSecret: Boolean,
    consent: Boolean = true, places: Boolean = false)

object PowerUse:
  /** A use of `printed` from `card`. It costs a secret when its printed
    * cost has a secret or a burnt secret, or when `secret` says it pays one
    * another way: an X secret cost, or a tax on battle plans. It places on
    * the card when its printed cost has favor or secrets, or when `places`
    * says its payment does. */
  def of(user: PlayerId, printed: PrintedPower, card: CardId,
      secret: Boolean = false, consent: Boolean = true,
      places: Boolean = false): PowerUse =
    PowerUse(user, printed.id, Some(card), printed.persistent,
      secret || printed.cost.secret > 0 || printed.cost.secretBurnt > 0,
      consent,
      places || printed.cost.favor > 0 || printed.cost.secret > 0)

/** Why a restriction refuses a use. `power` is the restriction's and
  * `note` the reason shown when nobody may be asked. `reason` is the
  * refusal on the consent path: its waiver, when it has one, names who may
  * lift it and the key a grant is remembered under. */
final case class PowerUseRefusal(power: PowerId, note: PowerNote,
    reason: OperationReason)

/** A rule that forbids some uses of powers (Spell Breaker, Forest Council,
  * City Wall, Giant Python). A walker power declares it as
  * `powerUseRestrictions`; the engine reads it without naming it. `waiver`
  * is how its ruler may waive it, for the consent grid, the preferences and
  * the consent log (`ConsentPowers`); `None` when nothing waives it. */
trait PowerUseRestriction:
  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal]
  def waiver: Option[Waiver] = None
```

`Waiver` is declared in `ContributingPower.scala`, in the same package.

- [ ] **Step 4: Create `PowerUseRules.scala`**

```scala
package oathdigital.gameplay.operations

import oathdigital.gameplay.powerresolver.{PowerUse, PowerUseRefusal,
  PowerUseRestriction}
import oathdigital.model._

/** The power-use filter (catalog batch 8, slice 10): the refusals a use
  * meets, with consent applied, and how an option for a refused use is
  * shown. Every path that uses a power reads it: the phase menu and the
  * start check, the modifier picker, the battle-plan and reroll offers, and
  * the contribution collector. */
object PowerUseRules:
  /** The decision slot of a power-use grant's key. A grant covers the
    * card's use by its requester for the turn, on whichever path uses it,
    * so the key names the use instead of a decision. */
  val useKey: String = "power.use"

  /** The target of a grant's key: the card, or the power's own button
    * when it has none. */
  def target(use: PowerUse): DecisionOptionRef = use.card match
    case Some(id: DenizenId) => DecisionOptionRef.Denizen(id)
    case Some(id: RelicId) => DecisionOptionRef.Relic(id)
    case Some(id: EdificeId) => DecisionOptionRef.Edifice(id)
    case _ => DecisionOptionRef.Button(use.power.value)

  /** Every refusal `restrictions` give `use`, less those a grant
    * remembered this turn lifts. */
  def refusals(ready: ReadyGame, use: PowerUse,
      restrictions: Vector[PowerUseRestriction]): Vector[PowerUseRefusal] =
    restrictions.flatMap(_.refusal(ready, use)).filterNot(refusal =>
      refusal.reason.waiver.exists(waiver => ready.consent.remembered(
        waiver.covers, ConsentTurn.of(ready))
        .contains(ConsentAnswer.Granted)))

  /** How an option for a use `refusals` stop is shown: askable of its
    * granters when `consent` allows it and consent may lift every refusal
    * (ruling 8), else unavailable with the first refusal's reason. */
  def shown(ready: ReadyGame, actor: PlayerId,
      refusals: Vector[PowerUseRefusal], consent: Boolean): OptionReason =
    require(refusals.nonEmpty, "an option with no refusal is not refused")
    val first = refusals.head
    Option.when(consent)(OperationConsent.ask(ready, actor,
      refusals.map(_.reason))).flatten
      .getOrElse(OptionReason.Noted(first.power, first.note))
```

`CardId` is a union of the id types on `main`. If the match on it does not compile as written, match on the concrete id classes the way `PowerSourceRef.of` or `ModifierPicks.cardRef` does.

- [ ] **Step 5: Add the members to `ContributingPower` and `PhasePower`**

In `ContributingPower.scala`, in `trait ContributingPower`, after `operationRestrictions`, add:

```scala
  /** The rules this power holds over other powers' uses (catalog batch 8,
    * slice 10). The engine reads them without naming them. None by
    * default. */
  def powerUseRestrictions: Vector[PowerUseRestriction] = Vector.empty
  /** This power's use at `ctx`, as the power-use restrictions judge it.
    * `None` for a power nobody uses: a game rule, or a lasting limit
    * printed under a phase power's id. A player-selected power is judged
    * at its pick; an automatic one at each fold of its windows, and its
    * use must read only what holds at the node it is gathered for. */
  def use(ctx: PowerCtx): Option[PowerUse] = None
```

In `PhasePower.scala`, add to `trait PhasePower`, after `cost`:

```scala
  /** Whether using the power costs a secret (catalog batch 8, slice 10):
    * its cost has a secret or a burnt secret. A power that pays X secrets
    * from its tree overrides it. */
  def costsSecret: Boolean = cost.secret > 0 || cost.secretBurnt > 0
```

and change `PhasePowers` to:

```scala
/** `useRestrictions` are the power-use restrictions the walker powers
  * declare (catalog batch 8, slice 10), so a phase power is judged by the
  * same rules a walker power is. */
final case class PhasePowers(powers: Vector[PhasePower],
    useRestrictions: Vector[PowerUseRestriction] = Vector.empty):
```

- [ ] **Step 6: Collect them in `WalkerPowers`**

In `WalkerPowers.scala`, after `operationRestrictions`, add:

```scala
  /** The power-use restrictions these powers declare (catalog batch 8,
    * slice 10). */
  lazy val useRestrictions: Vector[PowerUseRestriction] =
    powers.flatMap(_.powerUseRestrictions)
```

Add `PowerUseRestriction` to the `powerresolver` import.

- [ ] **Step 7: Run the suite and the build**

Run: `./sbtw "testOnly oathdigital.gameplay.operations.PowerUseRulesSuite"`
Expected: 4 tests pass.

Run: `./sbtw "Test/compile"`
Expected: it compiles. A `PhasePowers(...)` built positionally still compiles, because the new field has a default.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powerresolver/PowerUse.scala src/main/scala/oathdigital/gameplay/operations/PowerUseRules.scala src/main/scala/oathdigital/gameplay/powerresolver/ContributingPower.scala src/main/scala/oathdigital/gameplay/powerresolver/PhasePower.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowers.scala src/test/scala/oathdigital/gameplay/operations/PowerUseRulesSuite.scala
git commit -m "feat(batch8): a power-use filter with consent"
```

---

### Task 3: Spell Breaker

**Files:**
- Create: `src/main/scala/oathdigital/gameplay/powers/poweruse/RulerBans.scala`
- Create: `src/main/scala/oathdigital/gameplay/powers/poweruse/SpellBreaker.scala`
- Modify: `src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala` (remove `SpellBreakerCard`)
- Modify: `src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala` (import the new package)
- Modify: `src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala`
- Modify: `src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerSuite.scala`

**Interfaces:**
- Consumes: Task 2's `PowerUse`, `PowerUseRefusal`, `PowerUseRestriction`, `PowerUseRules`.
- Produces:
  - `RulerBans.ruler(ready: ReadyGame, card: DenizenId): Option[SiteRuler]`, the ruler of the card's site when that is a player or bandits.
  - `RulerBans.enemy(ruler: SiteRuler, player: PlayerId): Boolean`.
  - `RulerBans.refusal(power: PowerId, card: DenizenId, ruler: SiteRuler, requester: PlayerId, covers: ConsentKey, plain: NoteKey, asked: NoteKey, code: String): PowerUseRefusal`.
  - `RulerBans.waiver(card: DenizenId, asked: NoteKey): Waiver`, whose granter is the card's site's ruler when that is a player.
  - `SpellBreaker.id`, `SpellBreaker.plain`, `SpellBreaker.asked`, `SpellBreaker.waiver`.
  - `WalkerPowerCatalog.useRestrictions(catalog: ExecutableCatalog): Vector[PowerUseRestriction]`.
  - `ConsentPowers` lists a power whose `powerUseRestrictions` declare a waiver.

- [ ] **Step 1: Write the failing restriction tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerSuite.scala`:

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.powerresolver.PowerUse
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Spell Breaker (card 162): "Enemies of Spell Breaker's ruler cannot use
  * powers that cost any [secret] or [secret-burnt]." (catalog batch 8,
  * slice 10). This part tests the restriction alone; the paths that read
  * it are tested in the suites after it. */
class SpellBreakerSuite extends munit.FunSuite:
  private val breaker = CatalogNames.denizen("Spell Breaker")
  private val woods = CatalogNames.site("Deep Woods")
  private val terror = CatalogNames.denizen("Roving Terror")

  /** p1's Act, with Spell Breaker at Deep Woods, which `ruled` gives its
    * ruler: p2's warband by default. */
  private def breakerAtDeepWoods(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).denizen(breaker, at = woods))
      .ready

  private def secretUse(by: PlayerId): PowerUse = PowerUse(by,
    PowerId("denizen.roving-terror"), Some(terror), persistent = false,
    costsSecret = true)

  test("it refuses an enemy of its ruler a power that costs a secret, " +
      "waivable by the ruler"):
    val refusal = SpellBreaker.refusal(breakerAtDeepWoods(), secretUse(p1))
    assertEquals(refusal.map(_.power), Some(SpellBreaker.id))
    assertEquals(refusal.flatMap(_.reason.waiver).flatMap(_.granter),
      Some(p2))
    assertEquals(refusal.flatMap(_.reason.waiver).map(_.covers),
      Some(ConsentKey(SpellBreaker.id, p1, "power.use",
        DecisionOptionRef.Denizen(terror))))

  test("its ruler, and a power that costs no secret, are not refused"):
    assertEquals(SpellBreaker.refusal(breakerAtDeepWoods(), secretUse(p2)),
      None)
    assertEquals(SpellBreaker.refusal(breakerAtDeepWoods(),
      secretUse(p1).copy(costsSecret = false)), None)

  test("under bandits every player is refused, and nobody may consent"):
    val refusal = SpellBreaker.refusal(
      breakerAtDeepWoods(_.bandits(woods, 2)), secretUse(p1))
    assertEquals(refusal.map(_.power), Some(SpellBreaker.id))
    assertEquals(refusal.flatMap(_.reason.waiver), None)

  test("at an unruled site it refuses nobody"):
    assertEquals(SpellBreaker.refusal(breakerAtDeepWoods(identity),
      secretUse(p1)), None)

  test("the consent grid lists it, granted by its ruler"):
    val powers = ConsentPowers.default(catalog)
    assertEquals(powers.granter(breakerAtDeepWoods(), SpellBreaker.id),
      Some(p2))
    assertEquals(powers.source(SpellBreaker.id),
      Some(PowerSourceRef.Card(breaker)))
    assertEquals(powers.granter(breakerAtDeepWoods(_.bandits(woods, 2)),
      SpellBreaker.id), None)
```

Add the imports `oathdigital.gameplay.powers.ConsentPowers` and `oathdigital.gameplay.setup.FirstGameSetupFixture.catalog`.

If `Table` names its steps differently (`warbandsAt`, `bandits`), use the names `HallOfMinistersConsentSuite` uses for the same boards.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.SpellBreakerSuite"`
Expected: compilation fails, because `SpellBreaker` does not exist.

- [ ] **Step 3: Create `RulerBans.scala`**

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{PowerUseRefusal, Waiver}
import oathdigital.model._

/** The ban a site card holds over its ruler's enemies (Spell Breaker,
  * Forest Council, City Wall; catalog batch 8, slice 10). The ruler is the
  * ruler of the card's site, when that is a player or bandits: nobody is
  * restricted at an unruled site or one the Empire rules. Every player but
  * a player ruler is an enemy, and every player is an enemy of bandits.
  * Only a player ruler may consent (consent design, ruling 4). */
private[powers] object RulerBans:
  def ruler(ready: ReadyGame, card: DenizenId): Option[SiteRuler] =
    SiteRulers.rulerOfCard(ready, card)

  def enemy(ruler: SiteRuler, player: PlayerId): Boolean =
    ruler != SiteRuler.Player(player)

  /** The ban's waiver, for the consent grid: its granter is the player who
    * rules `card`'s site, and `asked` is its reason when asked. */
  def waiver(card: DenizenId, asked: NoteKey): Waiver =
    val source = PowerSourceRef.Card(card)
    Waiver(ready => ruler(ready, card).collect {
      case SiteRuler.Player(player) => player }, source,
      (_, _) => asked(source))

  /** `power`'s refusal of `requester`, waivable by a player ruler under
    * `covers`. */
  def refusal(power: PowerId, card: DenizenId, ruler: SiteRuler,
      requester: PlayerId, covers: ConsentKey, plain: NoteKey,
      asked: NoteKey, code: String): PowerUseRefusal =
    val source = PowerSourceRef.Card(card)
    val granter = ruler match
      case SiteRuler.Player(player) => Some(player)
      case _ => None
    PowerUseRefusal(power, plain(source), OperationReason(code,
      s"${power.value} refuses ${requester.value}",
      OperationReasonKind.Impossible, granter.map(player =>
        OperationWaiver(power, Some(player), covers, asked(source)))))
```

- [ ] **Step 4: Create `SpellBreaker.scala`**

Move `SpellBreakerCard` here unchanged and remove it from `HoldingNomadDenizens.scala`.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.operations.PowerUseRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  PowerUse, PowerUseRefusal, PowerUseRestriction}
import oathdigital.model._

object SpellBreakerCard extends Denizen(DenizenId("162"), "Spell Breaker", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.spell-breaker"),
    persistent = true, cost = Cost.free,
    text = "Enemies of Spell Breaker's ruler **cannot** use powers that " +
      "cost any [secret] or [secret-burnt].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Spell Breaker (card 162, site-only): "Enemies of Spell Breaker's ruler
  * cannot use powers that cost any [secret] or [secret-burnt]."
  *
  * It refuses every use that costs a secret ([[PowerUse.costsSecret]]) to
  * the enemies of its site's ruler ([[RulerBans]]). A player ruler may
  * consent outside a Campaign; a grant covers the card's use for the turn.
  * It hooks no window and writes no line: a refused power is not used.
  */
case object SpellBreaker extends ContributingPower with PowerUseRestriction:
  val id: PowerId = SpellBreakerCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
  override def powerUseRestrictions: Vector[PowerUseRestriction] =
    Vector(this)
  private val notes = NoteSet(id)
  override def noteKeys: Vector[NoteKey] = notes.declared

  /** "Cannot use powers that cost secrets." */
  val plain: NoteKey = notes.key("no-secret-powers", Vector(NotePart.Text(
    "Cannot use powers that cost secrets.")))
  /** "Cannot use powers that cost secrets without ruler's consent." */
  val asked: NoteKey = notes.key("no-secret-powers-consent", Vector(
    NotePart.Text("Cannot use powers that cost secrets without ruler's " +
      "consent.")))

  def refusal(ready: ReadyGame, use: PowerUse): Option[PowerUseRefusal] =
    for
      ruler <- RulerBans.ruler(ready, SpellBreakerCard.id)
      if use.costsSecret && RulerBans.enemy(ruler, use.user)
    yield RulerBans.refusal(id, SpellBreakerCard.id, ruler, use.user,
      ConsentKey(id, use.user, PowerUseRules.useKey,
        PowerUseRules.target(use)), plain, asked, "spell-breaker")

  override val waiver: Option[Waiver] =
    Some(RulerBans.waiver(SpellBreakerCard.id, asked))
```

Add `Waiver` to the `powerresolver` import. Declare `waiver` after `asked`, since it reads it.

`case object SpellBreaker` would put its name in the engine scan as "SpellBreaker"; no engine file contains that word, so the scan passes.

- [ ] **Step 5: Register it**

- In `NewFoundations.scala`, add `import oathdigital.gameplay.powers.poweruse._` beside the other power-package imports. `SpellBreakerCard` keeps its place in the card list.
- In `WalkerPowerCatalog.scala`, add `Vector(SpellBreaker) ++` after `Vector(YewStaff) ++`, with its import, and add:

  ```scala
  /** The power-use restrictions the walker powers of `catalog` declare,
    * for the phase powers (catalog batch 8, slice 10). */
  def useRestrictions(catalog: ExecutableCatalog)
      : Vector[PowerUseRestriction] = default(catalog).useRestrictions
  ```

- In `PhasePowerCatalog.scala`, pass them: `PhasePowers(<the vector as it is>, WalkerPowerCatalog.useRestrictions(catalog))`.
- In `PowerKindsCatalogSuite.scala`, add `"denizen.spell-breaker"` to `persistentRules`.
- In `ConsentPowers.scala`, list the waivers the power-use restrictions declare beside the contributions' own, so the grid, the preferences and the consent log name Spell Breaker:

  ```scala
    private val waivers: Map[PowerId, Waiver] = (for
      power <- powers
      waiver <- power.contributions.values.flatten.collectFirst {
        case Restriction(_, _, Some(waiver)) => waiver
        case OptionRestriction(_, _, Some(waiver)) => waiver }
        .orElse(power.powerUseRestrictions.flatMap(_.waiver).headOption)
    yield power.id -> waiver).toMap ++ operations
  ```

  and add "and the power-use restrictions that declare one (catalog batch 8, slice 10)" to the class comment's list.
- Regenerate the note-key snapshot: `NOTE_KEYS_SNAPSHOT=write ./sbtw "testOnly oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`. Check with `git diff src/test/resources/notes/declared-keys.txt` that the only change is two added lines, for `denizen.spell-breaker`'s `no-secret-powers` and `no-secret-powers-consent`.

`notes` must be declared after `id` and before the keys, as plain `val`s, never `lazy`: `NoteSet` reads its keys as the object initialises (`PowerNotes.scala`, "declared is complete only once the owning object's initialiser has run").

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.SpellBreakerSuite oathdigital.gameplay.PowerKindsCatalogSuite oathdigital.gameplay.BackendArchitectureSuite oathdigital.gameplay.powers.DeclaredNoteKeysSuite"`
Expected: all pass. SpellBreakerSuite has 5 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/poweruse/RulerBans.scala src/main/scala/oathdigital/gameplay/powers/poweruse/SpellBreaker.scala src/main/scala/oathdigital/catalog/holding/HoldingNomadDenizens.scala src/main/scala/oathdigital/gameplay/cards/NewFoundations.scala src/main/scala/oathdigital/gameplay/powers/WalkerPowerCatalog.scala src/main/scala/oathdigital/gameplay/powers/PhasePowerCatalog.scala src/main/scala/oathdigital/gameplay/powers/ConsentPowers.scala src/test/scala/oathdigital/gameplay/PowerKindsCatalogSuite.scala src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerSuite.scala src/test/resources/notes/declared-keys.txt
git commit -m "feat(batch8): Spell Breaker refuses its ruler's enemies secret powers"
```

---

### Task 4: The phase path

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala`
- Modify: `src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/action/WardOfSilence.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerPhaseSuite.scala`

**Interfaces:**
- Consumes: `PhasePowers.useRestrictions`, `PhasePower.costsSecret`, `PowerUseRules.refusals`, `PowerUseRules.shown`.
- Produces:
  - `PhasePowerProcedure.refusals(catalog: ExecutableCatalog, ready: ReadyGame, player: PlayerId, power: PhasePower, source: PowerSourceRef, restrictions: Vector[PowerUseRestriction]): Vector[PowerUseRefusal]`
  - `PhasePowerProcedure.check(..., restrictions: Vector[PowerUseRestriction] = Vector.empty)`, which refuses a refused use with `OathViolation.StartUnavailable(<first refusal's detail>)`.

- [ ] **Step 1: Write the failing phase tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerPhaseSuite.scala`. It uses Roving Terror, a site-only ACTION that costs one secret, with p1 at Ancient City beside it, and a card at Broken Peaks for it to replace, as `HallOfMinistersConsentSuite` sets it up.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.action.{RovingTerror, WardOfSilence}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, MenuPlay, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Spell Breaker on the phase path (catalog batch 8, slice 10): the Act
  * menu shows a refused ACTION askable of the ruler, or unavailable, and
  * its start is refused until a grant is remembered. */
class SpellBreakerPhaseSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val breaker = CatalogNames.denizen("Spell Breaker")
  private val terror = CatalogNames.denizen("Roving Terror")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val home = CatalogNames.site("Ancient City")
  private val woods = CatalogNames.site("Deep Woods")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val terrorRef = DecisionOptionRef.Denizen(terror)
  private val power = RovingTerror.forCatalog(catalog)
  private val covers = ConsentKey(SpellBreaker.id, p1, "power.use",
    terrorRef)

  /** p1's Act at Ancient City beside Roving Terror, with Wrestlers at
    * Broken Peaks to replace, and Spell Breaker at Deep Woods, which
    * `ruled` gives its ruler: p2 by default. */
  private def terrorBesideBreaker(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1)): ReadyGame =
    ruled(Table.start.turn(p1, Phase.Act).pawn(p1, at = home)
      .denizen(terror, at = home).denizen(wrestlers, at = peaks)
      .denizen(breaker, at = woods)).ready

  private def remembering(ready: ReadyGame, answer: ConsentAnswer)
      : ReadyGame =
    ready.copy(consent = ConsentState(answers = Map(covers -> answer),
      turn = Some(ConsentTurn.of(ready))))

  private def terrorOption(ready: ReadyGame)(using munit.Location)
      : DecisionOption = MenuPlay.menuOptionFor(catalog, ready,
    ActionRef.UsePower(power.id), Vector(terrorRef))

  test("an enemy's secret-costing ACTION is askable of Spell Breaker's " +
      "ruler"):
    terrorOption(terrorBesideBreaker()) match
      case DecisionOption.Unavailable(_, ask: OptionReason.Askable) =>
        assertEquals(ask.power, SpellBreaker.id)
        assertEquals(ask.asks, Vector(ConsentAsk(p2,
          Vector(SpellBreaker.id))))
        assertEquals(ask.covers, Vector(covers))
      case other => fail(s"expected an askable option, got $other")

  test("its start is refused while the refusal stands"):
    assert(use(terrorBesideBreaker(), power, terrorRef).isLeft)

  test("a grant remembered this turn opens the ACTION"):
    val ready = remembering(terrorBesideBreaker(), ConsentAnswer.Granted)
    assert(terrorOption(ready).available)
    assert(awaits(accepted(use(ready, power, terrorRef)),
      RovingTerror.decisionId))

  test("under bandits the ACTION shows Spell Breaker's reason"):
    assertEquals(terrorOption(terrorBesideBreaker(_.bandits(woods, 2))),
      DecisionOption.Unavailable(DecisionOption.Denizen(terrorRef),
        OptionReason.Noted(SpellBreaker.id,
          SpellBreaker.plain(PowerSourceRef.Card(breaker)))))

  test("the ruler uses it freely"):
    assert(terrorOption(terrorBesideBreaker(_.warbandsAt(woods, p1, 1)))
      .available)

  test("Ward of Silence pays X secrets, so it is refused too"):
    assert(WardOfSilence.costsSecret)
```

Add one projection test that the askable row reads "Spell Breaker: Cannot use powers that cost secrets without ruler's consent.". Park the Act menu as `PhaseMenuSuite` does, then read the row from `queryOf(..., p1)` with its `offeredOptions`, as `HallOfMinistersConsentSuite.errandBoyRow` does. The option's id is Roving Terror's card id.

If `DecisionOption.forRef(terrorRef)` builds a different shell than `DecisionOption.Denizen(terrorRef)`, compare against `DecisionOption.forRef(terrorRef).get` instead.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.SpellBreakerPhaseSuite"`
Expected: the askable, refused, bandit and Ward tests fail. Ward of Silence's printed cost is free.

- [ ] **Step 3: Judge the use in `PhasePowerProcedure`**

Add, after `payable`:

```scala
  /** The power-use refusals `player`'s use of `power` from `source` meets
    * (catalog batch 8, slice 10). The phase menu is never inside a
    * Campaign, so every refusal may be asked of its ruler. */
  def refusals(catalog: ExecutableCatalog, ready: ReadyGame,
      player: PlayerId, power: PhasePower, source: PowerSourceRef,
      restrictions: Vector[PowerUseRestriction])
      : Vector[PowerUseRefusal] =
    if restrictions.isEmpty then Vector.empty
    else
      val card = source match
        case PowerSourceRef.Card(card) => Some(card)
        case _ => None
      val persistent = catalog.printedPower(power.id).exists(_.persistent)
      PowerUseRules.refusals(ready, PowerUse(player, power.id, card,
        persistent, power.costsSecret,
        places = power.cost.favor > 0 || power.cost.secret > 0),
        restrictions)
```

Give `check` a last parameter `restrictions: Vector[PowerUseRestriction] = Vector.empty`, and after the `found <- ...` line add:

```scala
      _ <- refusals(catalog, ready, active, power, found, restrictions)
        .headOption.map(refused => OathViolation.StartUnavailable(
          refused.reason.detail)).toLeft(())
```

In `build`, pass `powers.useRestrictions` to `check`. `rebuild` does not check, so it stays as it is. Add the imports `PowerUseRules` (from `gameplay.operations`) and `PowerUse`, `PowerUseRefusal`, `PowerUseRestriction` (from `gameplay.powerresolver`).

- [ ] **Step 4: Show a refused power in the menu**

In `PhaseMenu.offers`, keep the `PowerSourceRef` with each sourced power. Change `sourced` to yield `(power, found, ref)` from `PhasePowerProcedure.sources(...)` without `.map(_._2)`, and adjust the `sourced.count(...)` that reads the ref. Then in `used`, before calling `offered`, judge the use:

```scala
    val used: Vector[Offered] = sourced.flatMap((power, found, ref) =>
      val shown =
        if sourced.count(_._3 == ref) > 1 then
          Some(DecisionOption.Button(powerButton(power.id),
            power.buttonName.getOrElse(power.id.value)))
        else DecisionOption.forRef(ref)
      val refused = PhasePowerProcedure.refusals(catalog, ready, actor,
        power, found, phasePowers.useRestrictions)
      shown.toVector.map(option =>
        if refused.isEmpty then
          offered(ActionRef.UsePower(power.id), Vector(ref), option)
        else Offered(QueuedProcedure(actor, ActionRef.UsePower(power.id),
          Vector(ref), None, QueueKind.Run), DecisionOption.Unavailable(
          option, PowerUseRules.shown(ready, actor, refused,
            consent = true)))))
```

`onlyEnding` counts askable options as not available, so a Wake menu whose only power is askable would end by itself. In `onlyEnding`, count an askable option as something to ask: replace `!offered.option.available` with `!offered.option.available && !offered.option.askable`.

- [ ] **Step 5: Leave a refused forced Wake power out**

In `ForcedWakeProcedure.due`, keep only the powers with no refusal:

```scala
      PhasePowerProcedure.sources(catalog, ready, player, power).collect {
        case (found, ref) if power.usable(ready, player, ref) &&
            PhasePowerProcedure.refusals(catalog, ready, player, power,
              found, powers.useRestrictions).isEmpty => power -> ref
      })
```

Part 2 tests this with City Wall and Hunger. Spell Breaker cannot reach Hunger, which costs no secret.

- [ ] **Step 6: Ward of Silence costs a secret**

In `WardOfSilence.scala`, add to `case object WardOfSilence`:

```scala
  /** It pays X secrets, at least one, from its tree: a power that costs a
    * secret (catalog batch 8, slice 10). */
  override def costsSecret: Boolean = true
```

- [ ] **Step 7: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.* oathdigital.gameplay.PhaseMenuSuite oathdigital.gameplay.PhasePowerSuite oathdigital.gameplay.powers.action.WardOfSilenceSuite oathdigital.gameplay.operations.HallOfMinistersConsentSuite"`
Expected: all pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/phases/PhasePowerProcedure.scala src/main/scala/oathdigital/gameplay/phases/PhaseMenu.scala src/main/scala/oathdigital/gameplay/phases/wake/ForcedWakeProcedure.scala src/main/scala/oathdigital/gameplay/powers/action/WardOfSilence.scala src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerPhaseSuite.scala
git commit -m "feat(batch8): the phase menu asks Spell Breaker's ruler"
```

---

### Task 5: The modifier picker

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerModifierSuite.scala`

**Interfaces:**
- Consumes: `ContributingPower.use`, `WalkerPowers.useRestrictions`, `PowerUseRules`.
- Produces: `SelectedModifier.use` and `CatacombsContribution.use`, both `Some` at a modifier-selection window. `consent` is false at `PowerWindow.CampaignModifierSelection`.

- [ ] **Step 1: Write the failing picker tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerModifierSuite.scala`. It uses Mushrooms, a Search modifier that costs one secret, from `MushroomsSuite`'s board, with Spell Breaker at Deep Woods.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.gameplay.powers.search.Mushrooms
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, MenuPlay, ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Spell Breaker in the modifier picker (catalog batch 8, slice 10). */
class SpellBreakerModifierSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val breaker = CatalogNames.denizen("Spell Breaker")
  private val mushrooms = CatalogNames.denizen("Mushrooms")
  private val woods = CatalogNames.site("Deep Woods")
  private val mushroomsRef = DecisionOptionRef.Denizen(mushrooms)
  private val decisionId =
    ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)

  /** p1 at Ancient City beside Mushrooms, with 5 Supply and a faceup
    * secret, and Spell Breaker at Deep Woods, which `ruled` gives its
    * ruler: p2 by default. */
  private def mushroomsBesideBreaker(
      ruled: Table => Table = _.warbandsAt(woods, p2, 1)): ReadyGame =
    ruled(Table.start.pawn(p1, "Ancient City")
      .denizen(mushrooms, at = "Ancient City").supply(p1, 5)
      .denizen(breaker, at = woods)).ready

  /** The modifier decision's Mushrooms option, once p1 starts a Search. */
  private def mushroomsPick(ready: ReadyGame)(using munit.Location)
      : DecisionOption =
    val begun = accepted(MenuPlay.started(rules, Ready(ready), p1,
      ActionRef.Search, Vector.empty))
    ParkedNode.of(begun.state, catalog).toOption.flatten.collect {
      case ParkedNode.Decision(`decisionId`, decide, _) => decide.query
    }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .flatMap(_.find(_.ref == mushroomsRef))
      .getOrElse(fail("the modifier decision does not list Mushrooms"))

  test("an enemy's secret-costing modifier is askable of the ruler"):
    assert(mushroomsPick(mushroomsBesideBreaker()).askable)

  test("under bandits it shows Spell Breaker's reason"):
    assertEquals(mushroomsPick(mushroomsBesideBreaker(_.bandits(woods, 2))),
      DecisionOption.Unavailable(DecisionOption.Denizen(mushroomsRef),
        OptionReason.Noted(SpellBreaker.id,
          SpellBreaker.plain(PowerSourceRef.Card(breaker)))))

  test("a grant remembered this turn lets p1 pick it"):
    val ready = mushroomsBesideBreaker()
    val covers = ConsentKey(SpellBreaker.id, p1, "power.use", mushroomsRef)
    val granted = ready.copy(consent = ConsentState(
      answers = Map(covers -> ConsentAnswer.Granted),
      turn = Some(ConsentTurn.of(ready))))
    assert(mushroomsPick(granted).available)
```

Use the start helpers `MushroomsSuite` and `ModifierPlay` use; if `MenuPlay.started` needs a start argument for a Search, pass the one `SearchPlay.searched` passes. If `ParkedNode.of` takes the power catalogs, pass `PowerRules`'s, as `ModifierPlay.asking` does. Where the option shell differs, compare against `DecisionOption.forRef(mushroomsRef).get`.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.SpellBreakerModifierSuite"`
Expected: the askable and bandit tests fail; Mushrooms is shown available.

- [ ] **Step 3: State the modifiers' use**

In `SelectedModifier.scala`, add to the trait:

```scala
  /** Its use at a modifier-selection window: by the actor, from its card,
    * costing a secret when its printed cost or its `cost` has one. Inside
    * a Campaign nobody may be asked (consent design, ruling 2). */
  override def use(ctx: PowerCtx): Option[PowerUse] =
    for
      _ <- SelectedModifier.selectionAction(ctx.window)
      printed <- catalog.printedPower(id)
    yield PowerUse.of(ctx.activePlayer, printed, cardId,
      secret = cost.secret > 0 || cost.secretBurnt > 0,
      consent = ctx.window != PowerWindow.CampaignModifierSelection,
      places = cost.favor > 0 || cost.secret > 0)
```

In `CatacombsContribution.scala`, add the same for its card, with `secret = true` and `places = true`, because its pick pays one secret onto its card:

```scala
  override def use(ctx: PowerCtx): Option[PowerUse] =
    for
      _ <- SelectedModifier.selectionAction(ctx.window)
      printed <- catalog.printedPower(id)
    yield PowerUse.of(ctx.activePlayer, printed, cardId, secret = true,
      places = true)
```

Add the `PowerUse` import from `gameplay.powerresolver` to both.

- [ ] **Step 4: Judge each option in `ModifierChoice`**

In `ModifierChoice.option`, after `val ctx = ...`, judge the use before the power's own reason:

```scala
    val refused = power.use(ctx).toVector.flatMap(use =>
      PowerUseRules.refusals(pass.state, use, pass.powers.useRestrictions)
        .map(_ -> use.consent))
    if refused.nonEmpty then
      DecisionOption.Unavailable(shown, PowerUseRules.shown(pass.state,
        actor, refused.map(_._1), consent = refused.forall(_._2)))
    else power.offerReason(ctx) match
      ...  // the existing match, unchanged
```

In `expand`, a pass with only askable modifiers must still ask, so the player can ask the ruler:

```scala
    val asks = listed.exists(option => option.available || option.askable) ||
      (picked.nonEmpty && listed.nonEmpty)
```

Add the import `oathdigital.gameplay.operations.PowerUseRules`.

A grant answers the modifier decision with the card, through the consent flow that every parked choose-one has. The next pass finds the grant, so the pick pays and stands.

- [ ] **Step 5: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.* oathdigital.gameplay.powers.search.* oathdigital.gameplay.walker.*"`
Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/SelectedModifier.scala src/main/scala/oathdigital/gameplay/powers/recover/CatacombsContribution.scala src/main/scala/oathdigital/gameplay/walker/ModifierChoice.scala src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerModifierSuite.scala
git commit -m "feat(batch8): the modifier picker asks Spell Breaker's ruler"
```

---

### Task 6: Battle plans and rerolls

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/campaign/PlanSurcharge.scala`
- Modify: `src/main/scala/oathdigital/gameplay/powers/dice/Jinx.scala`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala`
- Test: `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerOfferSuite.scala`

**Interfaces:**
- Consumes: `ContributingPower.use`, `WalkerPowers.useRestrictions`, `PowerUseRules`.
- Produces:
  - `BattlePlan.use`: at its plan window, the plan's user's use from its card. It costs a secret when its offer's costs hold a secret or a burnt secret, when its amount pays secrets (`PlanAmount.secretsEach > 0`), or when a secret surcharge taxes it (`PlanSurcharge.taxesSecret`). It is never consentable. A bandit plan has no use.
  - `PlanSurcharge.taxesSecret(ready: ReadyGame, context: PlanContext): Boolean`.
  - `Jinx.use`: the roller's use, persistent, costing a secret, consentable unless the throw is the Campaign's attack or defense pool.

- [ ] **Step 1: Write the failing offer tests**

Create `src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerOfferSuite.scala`. The boards come from `CampaignFixture`, as `FireTalkersSuite`, `GleamingArmorSuite` and `JinxSuite` build them. Spell Breaker stands at Deep Woods, which is in play on every fixture map and is neither the origin nor a target.

```scala
package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.OathRules
import oathdigital.gameplay.WalkerDiceFixture.scripted
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.actions.dice.RerollChoice
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, ParkedNode, Table}
import oathdigital.testkit.Unwrap.accepted

/** Spell Breaker over battle plans and rerolls (catalog batch 8, slice
  * 10): a refused plan is withheld with the plain reason, since nobody is
  * asked inside a Campaign; a refused reroll is askable outside a
  * Campaign's own dice. */
class SpellBreakerOfferSuite extends munit.FunSuite:
  import AttackDieFace.HollowSword
  import DefenseDieFace.{Blank, TwoShields}

  private val breaker = CatalogNames.denizen("Spell Breaker")
  private val woods = CatalogNames.site("Deep Woods")
  private val fireTalkers = CatalogNames.denizen("Fire Talkers")
  private val fireTalkersRef = DecisionOptionRef.Denizen(fireTalkers)
  private val armor = CatalogNames.denizen("Gleaming Armor")
  private val watchdog = CatalogNames.denizen("Watchdog")
  private val watchdogRef = DecisionOptionRef.Denizen(watchdog)
  private val titleRef: DecisionOptionRef = DecisionOptionRef.Button("title")
  private val jinx = CatalogNames.denizen("Jinx")
  private val jinxRef = DecisionOptionRef.Denizen(jinx)
  private val broken = Some(OptionReason.Noted(SpellBreaker.id,
    SpellBreaker.plain(PowerSourceRef.Card(breaker))))

  /** `b` with Spell Breaker at Deep Woods, ruled by `ruler`. */
  private def breakerRuledBy(b: Board, ruler: PlayerId): Board =
    on(b)(_.denizen(breaker, at = woods).warbandsAt(woods, ruler, 1))

  /** p1 attacks holding Fire Talkers faceup, a faceup secret and the
    * Darkest Secret, as `FireTalkersSuite` sets it up. */
  private def attackerWithFireTalkers: Board =
    val b = withSecrets(withAdviser(board(warbands = 5, supply = 7),
      fireTalkers, Orientation.FaceUp), 1)
    on(b)(_.darkestSecret(Some(b.actor), secrets = 1))

  test("an enemy's Fire Talkers is withheld with Spell Breaker's reason"):
    val base = attackerWithFireTalkers
    val b = breakerRuledBy(base, base.rival)
    val run = commit(rules(winningDice), b, 2)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    assertEquals(run.withheld(b.actor, fireTalkersRef), broken)

  test("Spell Breaker's ruler uses Fire Talkers freely"):
    val base = attackerWithFireTalkers
    val b = breakerRuledBy(base, base.actor)
    assert(commit(rules(winningDice), b, 2).offered(b.actor)
      .contains(fireTalkersRef))

  test("a free plan Gleaming Armor taxes with a secret is withheld too, " +
      "the title's plan included"):
    // p1 attacks holding Gleaming Armor and rules Spell Breaker; the
    // defender's Watchdog costs nothing of itself, but a secret with the
    // tax, so the defender, p1's enemy, may not use it. The title's plan,
    // which the tax makes flip a secret, is a power too.
    val base = rivalRulesOriginAndHoldsOathkeeper(
      board(warbands = 5, supply = 7))
    val held = withAdviserFor(withAdviser(base, armor, Orientation.FaceUp),
      base.rival, watchdog, Orientation.FaceUp)
    val b = breakerRuledBy(on(held)(_.secrets(base.rival, faceUp = 1)),
      base.actor)
    val run = commit(rules(losingDice), b, 4)
    assertEquals(run.withheld(b.actor, watchdogRef), broken)
    assertEquals(run.withheld(b.actor, titleRef), broken)

  test("Jinx after a Campaign roll shows the plain reason"):
    val base = withSecrets(withAdviser(board(warbands = 2, supply = 7), jinx,
      Orientation.FaceUp), faceUp = 1)
    val b = breakerRuledBy(base, base.rival)
    val game = rules(scripted(Vector(HollowSword, HollowSword),
      Vector(HollowSword, HollowSword), Vector(Blank, Blank)))
    val asked = commit(game, b, 2).finishPlanWindows
    assert(awaits(asked, b.actor,
      RerollChoice.decisionId(CampaignIds.attackPool, 1)))
    assertEquals(asked.withheld(b.actor, jinxRef), broken)

  test("Jinx after a Recover roll is askable of Spell Breaker's ruler"):
    // JinxSuite's Recover board, with Spell Breaker ruled by p2.
    val site = CatalogNames.site("Fair Isle")
    val start = Table.start.turn(Table.p1, Phase.Act)
      .pawn(Table.p1, at = site)
      .relicAt(CatalogNames.relic("Dowsing Sticks"), at = site)
      .adviser(Table.p1, jinx).secrets(Table.p1, faceUp = 2)
      .denizen(breaker, at = woods).warbandsAt(woods, Table.p2, 1).ready
    val game = new OathRules(catalog,
      walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
      walkerDice = scripted(Vector(Blank, Blank), Vector(TwoShields,
        TwoShields)))
    val started = accepted(game.startWalker(Ready(start), ActionRef.Recover,
      Table.p1))
    val first = RerollChoice.decisionId(RecoverProcedure.recoverPool, 1)
    val jinxOption = ParkedNode.of(started.state, catalog,
      WalkerPowerCatalog.default(catalog)).toOption.flatten.collect {
      case ParkedNode.Decision(`first`, decide, _) => decide.query
    }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .flatMap(_.find(_.ref == jinxRef))
    assert(jinxOption.exists(_.askable), jinxOption.toString)
```

A sixth test covers Arcane Armor, which pays X secrets. Build its board as `ArcaneArmorSuite` does for the side that offers it, add `breakerRuledBy(_, <the other side>)`, and assert `run.withheld(<the Campaign's actor>, <Arcane Armor's ref>) == broken`.

Where a fixture helper is named differently on `main` (`losingDice`, `withAdviserFor`, `relicAt`), use the name the card's own suite uses. If Deep Woods is the Campaign's target on some fixture board, move Spell Breaker to Broken Peaks.

- [ ] **Step 2: Run it to see it fail**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.SpellBreakerOfferSuite"`
Expected: the refusal tests fail; the plans and Jinx are offered.

- [ ] **Step 3: A plan's use**

In `PlanSurcharge.scala`, lift the holder and enemy checks into the companion so a plan can ask them:

```scala
object PlanSurcharge:
  /** The player who holds `card` faceup among their advisers. */
  def holderOf(ready: ReadyGame, card: DenizenId): Option[PlayerId] =
    ready.game.current.players.find(_.advisers.exists {
      case DenizenState(id, Orientation.FaceUp, _) => id == card
      case _ => false
    }).map(_.player)

  /** Whether the plan of `side` in `setup` is the enemy's of `holding`. */
  def enemy(side: CampaignPlanSide, setup: CampaignSetup,
      holding: PlayerId): Boolean = side match
    case CampaignPlanSide.Defender => setup.actor == holding
    case CampaignPlanSide.Attacker =>
      setup.defender == CampaignDefender.Player(holding)

  /** Whether a surcharge that adds a secret taxes `context`'s plans:
    * Gleaming Armor's holder is the enemy of the plan's side (catalog
    * batch 8, slice 10). */
  def taxesSecret(ready: ReadyGame, context: PlanContext): Boolean =
    holderOf(ready, GleamingArmorCard.id)
      .exists(enemy(context.side, context.setup, _))
```

Make the trait's private `holder` and `enemy` call these. `application.setup` and `application.side` are what `enemy` reads today. If `CampaignSetup` is named differently on `main`, use the type of `application.setup`. Use whatever `PlanContext` calls its setup if it is not `setup`. If `GleamingArmorCard` lives under another name, use the card object in `GleamingArmor.scala`.

In `BattlePlan.scala`, add to the trait:

```scala
  /** Its use at its plan window (catalog batch 8, slice 10): the plan's
    * user's, from its card, or from no card for the title's plan, which
    * is a power too. It costs a secret when its costs or its amount hold
    * one, or a secret surcharge taxes it. Battle plans are printed
    * non-persistent. Nobody is asked inside a Campaign, and a bandit plan
    * has no user. */
  override def use(ctx: PowerCtx): Option[PowerUse] =
    for
      context <- PlanContext.of(ctx)
      if sides(context.side)
      user <- context.user
      offer <- plan(context)
    yield PowerUse(user, id, CampaignPlans.cardOf(offer.source),
      persistent = false,
      costsSecret = offer.costs.exists {
        case _: CampaignPlanCost.Secret | _: CampaignPlanCost.SecretBurnt =>
          true
        case _ => false
      } || offer.amount.exists(_.secretsEach > 0) ||
        PlanSurcharge.taxesSecret(ctx.state, context),
      consent = false,
      places = offer.costs.exists {
        case _: CampaignPlanCost.Favor | _: CampaignPlanCost.Secret => true
        case _ => false
      })
```

`CampaignPlans.cardOf` is in `gameplay.actions.campaign`; `PlanSurcharge` already imports it. If it returns a `DenizenId` or another narrower type, widen it to `CardId` with `.map(c => c: CardId)`.

The title's plan (`TitleDefensePlan`) is a `BattlePlan`, so it gets this use with `card = None`. Check that its `plan` passes `PlanContext.of` the same way; if it overrides `use` or is not a `BattlePlan` on `main`, give it the same `use` with `card = None`.

- [ ] **Step 4: Jinx's use**

In `Jinx.scala`, add:

```scala
  /** Its use on the throw at hand (catalog batch 8, slice 10): the
    * roller's, persistent, costing a secret. Nobody is asked about the
    * Campaign's own dice. */
  override def use(ctx: PowerCtx): Option[PowerUse] =
    RerollChoice.throwOf(ctx).filter(rolled =>
      Jinx.rules(ctx.state, rolled.roller)).map(rolled =>
      PowerUse.of(rolled.roller, JinxCard.power, JinxCard.id,
        consent = !Set(CampaignSetup.attackPool, CampaignSetup.defensePool)
          .contains(rolled.pool)))
```

Import `CampaignSetup` from `gameplay.actions.campaign`, and `PowerUse`.

- [ ] **Step 5: Withhold refused offers in `WalkerPowerGather.applyWindow`**

In `applyWindow`, add a judge beside `ctxFor`:

```scala
        /** Why `power` may not be used here, when a power-use restriction
          * refuses it (catalog batch 8, slice 10). */
        def refused(power: ContributingPower): Option[OptionReason] =
          power.use(ctxFor(power)).flatMap { use =>
            val found = PowerUseRules.refusals(state, use,
              powers.useRestrictions)
            Option.when(found.nonEmpty)(PowerUseRules.shown(state,
              use.user, found, use.consent))
          }
```

In the `OfferHost` case, mark each offered plan from a refused power withheld, unless its own rule already withheld it:

```scala
              gathered.offers.flatMap { case (powerId, offer) =>
                offer.plan(ctxFor(byId(powerId))).map(plan =>
                  OfferedPlan(powerId, plan.withheld.fold(plan.copy(
                    withheld = refused(byId(powerId))))(_ => plan)))
              }
```

In the `RerollHost` case, do the same for each `OfferedReroll`:

```scala
              reroll.offer(ctxFor(byId(powerId))).map(offer =>
                OfferedReroll(powerId, offer.withheld.fold(offer.copy(
                  withheld = refused(byId(powerId))))(_ => offer)))
```

Add the import `oathdigital.gameplay.operations.PowerUseRules`, and `OptionReason` to the model import if it is not there. If `WalkerPowerGather.scala` passes 800 lines, move `refused` into a new `walker/PowerUseJudge.scala` as `private[walker] object PowerUseJudge`.

A withheld reroll shown with an `Askable` reason is asked about like any askable option of a parked choose-one. Once a grant is remembered, `refused` finds nothing, so the reroll stands and the grant's answer picks it.

- [ ] **Step 6: Run the suites**

Run: `./sbtw "testOnly oathdigital.gameplay.powers.poweruse.* oathdigital.gameplay.powers.campaign.* oathdigital.gameplay.powers.dice.*"`
Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/powers/campaign/BattlePlan.scala src/main/scala/oathdigital/gameplay/powers/campaign/PlanSurcharge.scala src/main/scala/oathdigital/gameplay/powers/dice/Jinx.scala src/main/scala/oathdigital/gameplay/walker/WalkerPowerGather.scala src/test/scala/oathdigital/gameplay/powers/poweruse/SpellBreakerOfferSuite.scala
git commit -m "feat(batch8): Spell Breaker withholds secret battle plans and rerolls"
```

---

### Task 7: Gates and status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md` (Status line)
- Modify: `docs/ROADMAP.md`

- [ ] **Step 1: Run the full gate**

Run: `./sbtw "test" "frontend/test"`
Expected: everything passes. The server count rises by the tests this plan adds: 4 + 5 + 7 + 3 + 6 = 25 (the phase suite's 7 include its projection test).

Run: `./sbtw "frontend/fullOptJS"`
Expected: it links.

Run: `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`.
Expected: both pass.

- [ ] **Step 2: Record the status**

- In the design's Status line, write "Slices 1 to 9 are done, and slice 10's first part (Spell Breaker)."
- In the ROADMAP's item 10, add "Part 1 (Spell Breaker) is done; parts 2 and 3 are next."

```bash
git add docs/superpowers/specs/2026-10-12-catalog-batch-8-design.md docs/ROADMAP.md
git commit -m "docs(batch8): slice 10 part 1 done"
```
