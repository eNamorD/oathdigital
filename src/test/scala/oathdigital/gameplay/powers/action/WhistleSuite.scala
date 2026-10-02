package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.ActPowerHarness.usableIds
import oathdigital.testkit.Outcomes.ready
import oathdigital.testkit.Table.{p2, p3}

class WhistleSuite extends munit.FunSuite:
  import PowerFixture._
  import MovementFixture._
  import PhasePowerHarness.{awaits, backToActing, ops, replayed}

  private val whistle = RelicId("R08")
  private def staged(secrets: Int = 2) = inPhase(
    Table.from(withRelic(homelands(quietTable), whistle)).secrets(actor,
      secrets).unchecked, Phase.Act)
  private val target = DecisionOptionRef.Player(p2)

  test("it pulls the chosen pawn to the actor's site and hands over the secret"):
    val start = staged()
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(awaits(parked, Whistle.decisionId))
    assertEquals(relicOf(ready(parked.state), whistle).get.tokens, Tokens(0, 1))

    val done = choose(parked.state, Whistle.decisionId, target).toOption.get
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(Look(after).pawn(p2), CatalogNames.site("Ancient City"))
    assertEquals(Look(after).pawn(p3), CatalogNames.site("Buried Giant"))
    assertEquals(Look(after).pawn(actor), CatalogNames.site("Ancient City"))
    assertEquals(relicOf(after, whistle).get.tokens, Tokens.empty)
    assertEquals(Look(after).player(p2).board.faceUpSecrets,
      Look(start).player(p2).board.faceUpSecrets + 1)
    assertEquals(Look(after).player(actor).board.faceUpSecrets, 1)
    assertEquals(replayed(start, parked.events ++ done.events), Right(done.state))

  test("only players at other sites are offered, even when there is one"):
    val start = Table.from(staged()).pawn(p3, "Ancient City").unchecked
    val parked = use(start, Whistle.id, whistle).toOption.get
    assert(awaits(parked, Whistle.decisionId))
    assert(choose(parked.state, Whistle.decisionId,
      DecisionOptionRef.Player(p3)).isLeft)
    assert(choose(parked.state, Whistle.decisionId, target).isRight)

  test("with nobody to pull, the cost is paid and the secret stays"):
    val start = Table.from(staged()).pawn(p3, "Ancient City").pawn(p2,
      "Ancient City").unchecked
    val done = use(start, Whistle.id, whistle).toOption.get
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, whistle).get.tokens, Tokens(0, 1))
    assertEquals(Look(after).player(actor).board.faceUpSecrets, 1)
    assertEquals(Look(after).pawn(p3), CatalogNames.site("Ancient City"))
    assert(!ops(done.events).exists {
      case Move(Piece.Pawn(_), _, _, _) => true
      case _ => false
    })
    assert(!usableIds(after).contains(Whistle.id),
      "the secret still rests on the Whistle")

  test("it is unusable without a secret"):
    assert(usableIds(staged()).contains(Whistle.id))
    assert(!usableIds(staged(secrets = 0)).contains(Whistle.id))
    assert(use(staged(secrets = 0), Whistle.id, whistle).isLeft)

  test("it writes the pawn it pulled and the secret it gave"):
    val parked = use(staged(), Whistle.id, whistle).toOption.get
    val done = choose(parked.state, Whistle.decisionId, target).toOption.get
    assertEquals(NoteText.said(Whistle, done.events), Vector(NoteText.Said(
      NoteKey.Used, s"Placed ${p2.value} at ${CatalogNames.site("Ancient City").value} and gave " +
        s"${p2.value} the Whistle's secret.", covers = false)))

  test("with nobody to pull it writes that"):
    val start = Table.from(staged()).pawn(p3, "Ancient City").pawn(p2,
      "Ancient City").unchecked
    val done = use(start, Whistle.id, whistle).toOption.get
    assertEquals(NoteText.said(Whistle, done.events), Vector(NoteText.Said(
      "used.none", "No pawn could be pulled.", covers = false)))
