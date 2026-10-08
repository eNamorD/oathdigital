package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, PowerRules,
  Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class FaeMerchantSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val walkerParked = PowerRules.parkedUnderWalkerAndPhasePowers

  private val fae = CatalogNames.denizen("Fae Merchant")
  private val source = DecisionOptionRef.Denizen(fae)
  private val scepter = RelicId("grand-scepter")
  private val whistle = CatalogNames.relic("Whistle")
  /** The relic on top of the relic deck: the one Fae Merchant draws. */
  private val top = CatalogNames.relic("Dowsing Sticks")
  private def relicRef(id: RelicId) = DecisionOptionRef.Relic(id)

  /** p1's Act at Ancient City with Fae Merchant at the site, holding `held`
    * faceup with `secrets` faceup secrets; Dowsing Sticks tops the relic
    * deck. */
  private def stagedTable(held: Vector[RelicId], secrets: Int = 2): Table =
    val acting = Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .denizen(fae, at = "Ancient City").secrets(actor, secrets)
      .relicDeckTop(top)
    held.foldLeft(acting)(_.relic(actor, _))
  private def staged(held: Vector[RelicId] = Vector(whistle),
      secrets: Int = 2): ReadyGame = stagedTable(held, secrets).ready
  private def relicIds(state: ReadyGame) = Look(state).player(actor)
    .relics.map(_.id)

  test("it draws a relic, then asks which relic to put on the bottom"):
    val ready0 = staged()
    val rules0 = rulesWithPrintedRestrictions()
    val parked = accepted(use(rules0, ready0, FaeMerchant.id, source))
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val mid = ready(parked.state)
    assertEquals(relicIds(mid), Vector(whistle, top))
    assertEquals(Look(mid).player(actor).relics.last.orientation,
      Orientation.FaceDown)
    assertEquals(Look(mid).tokensOn(fae), Tokens(0, 1))

    val done = accepted(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(whistle)))
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(top))
    assertEquals(end.game.current.commonCards.relicDeck.last, whistle)
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)
    assertWireRoundTrips(parked.events ++ done.events)

  test("the relic just taken may be the one put back"):
    val ready0 = staged()
    val rules0 = rulesWithPrintedRestrictions()
    val parked = accepted(use(rules0, ready0, FaeMerchant.id, source))
    val end = ready(accepted(answer(rules0, parked.state,
      FaeMerchant.decisionId, relicRef(top))).state)
    assertEquals(relicIds(end), Vector(whistle))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)

  test("the Grand Scepter is never offered, and cannot be chosen"):
    val ready0 = staged(Vector(scepter, whistle))
    val rules0 = rulesWithPrintedRestrictions()
    val parked = accepted(use(rules0, ready0, FaeMerchant.id, source))
    assert(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(scepter)).isLeft)
    val end = ready(accepted(answer(rules0, parked.state,
      FaeMerchant.decisionId, relicRef(whistle))).state)
    assert(relicIds(end).contains(scepter))

  test("with only the Grand Scepter held, the drawn relic is offered alone " +
      "for the player to confirm"):
    val ready0 = staged(Vector(scepter))
    val rules0 = rulesWithPrintedRestrictions()
    val parked = accepted(use(rules0, ready0, FaeMerchant.id, source))
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    assert(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(scepter)).isLeft)
    val done = accepted(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)))
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck.last, top)
    assertEquals(replayed(rules0, ready0, parked.events ++ done.events), end)

  test("with no other relic the drawn one is offered alone, and confirming " +
      "puts it back"):
    val ready0 = staged(Vector.empty)
    val rules0 = rulesWithPrintedRestrictions()
    val parked = accepted(use(rules0, ready0, FaeMerchant.id, source))
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val done = accepted(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(top)))
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck.last, top)

  test("a secret on the relic put back returns to its holder facedown"):
    val ready0 = stagedTable(Vector(whistle)).tokens(whistle, secrets = 1).ready
    val rules0 = rulesWithPrintedRestrictions()
    val parked = accepted(use(rules0, ready0, FaeMerchant.id, source))
    val end = ready(accepted(answer(rules0, parked.state,
      FaeMerchant.decisionId, relicRef(whistle))).state)
    assertEquals(Look(end).player(actor).board.faceDownSecrets,
      Look(ready0).player(actor).board.faceDownSecrets + 1)

  test("an empty relic deck still offers the one held relic, and confirming " +
      "puts it on the bottom"):
    val ready0 = stagedTable(Vector(whistle)).relicDeckEmptied.ready
    val rules0 = rulesWithPrintedRestrictions()
    val parked = accepted(use(rules0, ready0, FaeMerchant.id, source))
    walkerParked.assertParked(parked.state, ActionRef.UsePower(FaeMerchant.id),
      FaeMerchant.decisionId, actor)
    val done = accepted(answer(rules0, parked.state, FaeMerchant.decisionId,
      relicRef(whistle)))
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector.empty[RelicId])
    assertEquals(end.game.current.commonCards.relicDeck, Vector(whistle))

  test("with only the Grand Scepter held and an empty relic deck, it asks " +
      "nothing and puts nothing back"):
    val ready0 = stagedTable(Vector(scepter)).relicDeckEmptied.ready
    assert(usableIds(ready0).contains(FaeMerchant.id))
    val rules0 = rulesWithPrintedRestrictions()
    val done = accepted(use(rules0, ready0, FaeMerchant.id, source))
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    val end = ready(done.state)
    assertEquals(relicIds(end), Vector(scepter))
    assertEquals(end.game.current.commonCards.relicDeck, Vector.empty[RelicId])
    assertEquals(Look(end).tokensOn(fae), Tokens(0, 1))
    assertEquals(replayed(rules0, ready0, done.events), end)

  test("it is unusable without a faceup secret"):
    assert(!usableIds(staged(secrets = 0)).contains(FaeMerchant.id))

  test("it writes the relic it drew, then the relic it put back in place of the generic line"):
    val ready0 = staged()
    val merchant = FaeMerchant
    val parked = accepted(use(rulesWithPrintedRestrictions(), ready0,
      FaeMerchant.id, source))
    assertEquals(NoteText.said(merchant, parked.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} drew ${top.value} facedown.",
        covers = false)))
    val done = accepted(answer(rulesWithPrintedRestrictions(), parked.state,
      FaeMerchant.decisionId, relicRef(whistle)))
    assertEquals(NoteText.said(merchant, done.events), Vector(
      NoteText.Said("returned",
        s"${actor.value} put ${whistle.value} on the bottom of the relic deck.",
        covers = true)))
