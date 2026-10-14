package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.actions.economy.{MusterProcedure, TradeProcedure}
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{NoteText, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, MenuPlay,
  Outcomes, PhasePowerHarness, PowerRules, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class SpitefulMirrorSuite extends munit.FunSuite:
  private val mirror = CatalogNames.relic("Spiteful Mirror")
  private val power = SpitefulMirror.forCatalog(catalog)
  private val home = CatalogNames.site("Ancient City")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Discord denizen. */
  private val plainDiscord = SearchFixture.anyPlainDenizensOf(Suit.Discord)
    .head
  private val defame = CatalogNames.denizen("Defame")
  private val rules = PowerRules.withWalkerPowers
  private def modifiers(action: PowerWindow): String =
    ModifierPicks.decisionId(action)

  /** p1 at Ancient City holding Spiteful Mirror, a plain Discord adviser
    * and `secrets` faceup secrets, with Wrestlers at the site and 3 favor
    * in the Discord bank. */
  private def mirrorAtWrestlers(secrets: Int = 2): Table =
    Table.start.pawn(p1, at = home).relic(p1, mirror)
      .adviser(p1, plainDiscord).denizen(wrestlers, at = home)
      .secrets(p1, secrets).bankFavor(Suit.Discord, 3)

  /** `ready` with Spiteful Mirror already paid for this turn. */
  private def alreadyPaid(ready: ReadyGame): ReadyGame =
    ready.updateCurrent(current => current.copy(turn = current.turn.copy(
      usedPowers = current.turn.usedPowers + SpitefulMirror.useRef)))

  private def begun(ready: ReadyGame, action: ActionRef,
      selection: Vector[DecisionOptionRef]): OathTransition =
    accepted(MenuPlay.started(rules, Ready(ready), p1, action, selection))

  private val forFavor = Vector(DecisionOptionRef.Button("favor"))
  private val forSecrets = Vector(DecisionOptionRef.Button("secret"))

  /** `from`, then p1's answer `ref` at `decisionId`; the events run from
    * the action's start. */
  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(rules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  private def mirrorPicked(from: OathTransition, window: PowerWindow)
      : OathTransition =
    answeredWith(from, modifiers(window), DecisionOptionRef.Relic(mirror))

  private def optionStates(at: OathTransition)
      : Option[Vector[(String, String, Option[String])]] =
    PhasePowerHarness.queryOf(at, p1).map(_.offeredOptions.map(option =>
      (option.id, option.state, option.reason)))

  test("the first pick pays a secret and records the use; the Trade's " +
      "card is Discord, and the line names it"):
    val ready = mirrorAtWrestlers().ready
    // No other modifier to pick, so the Trade starts by itself.
    val picked = mirrorPicked(begun(ready, ActionRef.Trade, forFavor),
      PowerWindow.TradeModifierSelection)
    val done = answeredWith(picked, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.state)
    assertEquals(end.tokensOn(mirror), Tokens(0, 1))
    assertEquals(end.faceUpSecrets(p1), 0)
    // 1, and 1 for the plain Discord adviser, from the Discord bank.
    assertEquals(end.favor(p1), Look(ready).favor(p1) + 2)
    assertEquals(end.bankFavor(Suit.Discord), 1)
    assertEquals(end.bankFavor(Suit.Order), Look(ready).bankFavor(Suit.Order))
    assert(Outcomes.ready(done.state).game.current.turn.usedPowers
      .contains(SpitefulMirror.useRef))
    assertEquals(NoteText.said(power.id, power.noteKeys, done.events),
      Vector(NoteText.Said("acting",
        s"Acting as if ${wrestlers.value} is a Discord card.",
        covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events),
      Outcomes.ready(done.state))
    assertWireRoundTrips(done.events)

  test("paid for this turn, it is offered free, and picking it pays " +
      "nothing"):
    val ready = alreadyPaid(mirrorAtWrestlers(secrets = 1).ready)
    val asked = begun(ready, ActionRef.Trade, forFavor)
    assert(optionStates(asked).exists(_.contains(
      (mirror.value, "available", None))), optionStates(asked).toString)
    val done = answeredWith(mirrorPicked(asked,
      PowerWindow.TradeModifierSelection), TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(wrestlers))
    val end = Look(done.state)
    assertEquals(end.tokensOn(mirror), Tokens(0, 0))
    // Only the Trade's own secret.
    assertEquals(end.faceUpSecrets(p1), 0)
    assertEquals(end.bankFavor(Suit.Discord), 1)

  test("unpaid, with no secret, it is unavailable with the shortfall"):
    // Downtrodden, free, makes the Muster ask for its modifiers.
    val asked = begun(mirrorAtWrestlers(secrets = 0)
      .adviser(p1, CatalogNames.denizen("Downtrodden")).ready,
      ActionRef.Muster, Vector.empty)
    assert(optionStates(asked).exists(_.contains((mirror.value,
      "unavailable", Some("Needs 1 secret; you have 0")))),
      optionStates(asked).toString)

  test("a Muster counts the Discord advisers"):
    val ready = mirrorAtWrestlers().favor(p1, 1).ready
    val done = answeredWith(mirrorPicked(begun(ready, ActionRef.Muster,
      Vector.empty), PowerWindow.MusterModifierSelection),
      MusterProcedure.decisionId, DecisionOptionRef.Denizen(wrestlers))
    // 1, and 1 for the plain Discord adviser; no Order adviser.
    assertEquals(Look(done.state).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 2)

  test("a Trade for secrets counts the Discord advisers"):
    val ready = mirrorAtWrestlers(secrets = 1).favor(p1, 2).ready
    val done = answeredWith(mirrorPicked(begun(ready, ActionRef.Trade,
      forSecrets), PowerWindow.TradeModifierSelection),
      TradeProcedure.decisionId, DecisionOptionRef.Denizen(wrestlers))
    // The Mirror's secret paid, then 1 gained for the Discord adviser.
    assertEquals(Look(done.state).faceUpSecrets(p1), 1)

  test("Defame reads the Trade's card as Discord"):
    val ready = mirrorAtWrestlers().adviser(p1, defame).favor(p2, 2).ready
    val bothPicked = answeredWith(mirrorPicked(begun(ready, ActionRef.Trade,
      forFavor), PowerWindow.TradeModifierSelection),
      modifiers(PowerWindow.TradeModifierSelection),
      DecisionOptionRef.Denizen(defame))
    val asked = answeredWith(bothPicked, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(wrestlers))
    assert(PhasePowerHarness.offered(asked, p1).exists(
      _.contains("player" -> p2.value)),
      PhasePowerHarness.offered(asked, p1).toString)

  test("The Old Oak, traded as Discord, gives its secret for a Discord " +
      "adviser"):
    val oak = CatalogNames.denizen("The Old Oak")
    val ready = mirrorAtWrestlers(secrets = 1).denizen(oak, at = home)
      .favor(p1, 2).ready
    val bothPicked = answeredWith(mirrorPicked(begun(ready, ActionRef.Trade,
      forSecrets), PowerWindow.TradeModifierSelection),
      modifiers(PowerWindow.TradeModifierSelection),
      DecisionOptionRef.Denizen(oak))
    val done = answeredWith(bothPicked, TradeProcedure.decisionId,
      DecisionOptionRef.Denizen(oak))
    // The Mirror's secret paid, then 1 for the Discord adviser and 1 more
    // from The Old Oak.
    assertEquals(Look(done.state).faceUpSecrets(p1), 2)
