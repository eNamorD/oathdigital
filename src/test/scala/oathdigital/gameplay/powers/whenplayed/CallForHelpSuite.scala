package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.CardPlay
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}

class CallForHelpSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[CallForHelp]
  private val help = power.cardId
  private val played = hook(help)
  /** p1's pawn stands in the Cradle. */
  private val cradle = Region.Cradle
  private val next = CardPlay.nextRegion(cradle)

  /** Call for Help as p1's adviser, with `cards` in the Cradle discard pile,
    * the last named on top. */
  private def cradlePile(cards: String*): Table =
    Table.start.adviser(p1, help).discarded(cradle, cards*)

  private def amount(count: Int): DecisionAnswer =
    DecisionAnswer.ChooseAmountAnswer(count)

  private def named(cards: String*): Vector[WorldCardId] =
    cards.toVector.map(CatalogNames.worldCard(_))

  private def pileOf(ready: ReadyGame, region: Region): Vector[WorldCardId] =
    ready.game.current.commonCards.discard(region)

  private def knows(ready: ReadyGame, viewer: PlayerId, card: WorldCardId)
      : Boolean =
    ready.knowledge.advisers.getOrElse(viewer, Vector.empty).contains(card)

  private val nothingLine = NoteText.Said("none", "Nothing was revealed.",
    covers = false)

  test("draws off the top one at a time to the next region's pile, first " +
      "drawn deepest, shows each to every player, and gains 2 warbands per " +
      "Nomad card"):
    // Wild Mounts and Mountain Giant are Nomad; Mountain Giant is on top.
    val ready = cradlePile("Old Songs", "Spinning Bee", "Wild Mounts",
      "Mountain Giant").ready
    val walked = walkAnswering(ready, power, played, amount(3),
      chooseOne(CallForHelp.stop))
    val drawn = named("Mountain Giant", "Wild Mounts", "Spinning Bee")
    assertEquals(amountMaximaAsked(walked), Vector(4))
    // A pile stores its top last, so the first drawn is deepest.
    assertEquals(pileOf(walked.end, next), drawn)
    assertEquals(pileOf(walked.end, cradle), named("Old Songs"))
    // The quiet table's 3 warbands, and 2 for each of the two Nomad cards.
    assertEquals(Look(walked.end).boardWarbands(p1), 3 + 4)
    for card <- drawn; viewer <- Vector(p1, p2, p3) do
      assert(knows(walked.end, viewer, card), s"$viewer does not know $card")
    assertEquals(said(power, walked.events), Vector(NoteText.Said("revealed",
      s"Revealed and discarded ${drawn.map(_.value).mkString(", ")}, and " +
        "gained 4 warbands.", covers = true)))
    assertEquals(replayed(ready, walked.events), walked.end)
    assertWireRoundTrips(walked.events)

  test("keeping on drawing asks again, for no more than what is left of six"):
    val ready = cradlePile("Old Songs", "Spinning Bee", "Deed Writer",
      "Land Warden", "Defame", "Second Wind", "Zealots", "Unstable Summon")
      .ready
    val walked = walkAnswering(ready, power, played, amount(5),
      chooseOne(CallForHelp.keep), amount(1))
    // Six drawn, so no third question.
    assertEquals(amountMaximaAsked(walked), Vector(6, 1))
    assertEquals(pileOf(walked.end, cradle), named("Old Songs",
      "Spinning Bee"))
    assertEquals(said(power, walked.events).map(_.key),
      Vector("discarded", "discarded"))
    assertEquals(Look(walked.end).boardWarbands(p1), 3)

  test("the warbands gained stop at what p1's bank holds"):
    val ready = cradlePile("Wild Mounts").warbandsInBank(p1, 1).ready
    val walked = walkAnswering(ready, power, played, amount(1))
    assertEquals(Look(walked.end).boardWarbands(p1),
      Look(ready).boardWarbands(p1) + 1)
    assertEquals(said(power, walked.events), Vector(NoteText.Said("revealed",
      s"Revealed and discarded ${named("Wild Mounts").head.value}, and " +
        "gained 1 warband.", covers = true)))

  test("an amount of 0 reveals nothing and asks nothing more"):
    val ready = cradlePile("Old Songs", "Spinning Bee").ready
    val walked = walkAnswering(ready, power, played, amount(0))
    assertEquals(PhasePowerHarness.ops(walked.events), Vector.empty)
    assertEquals(pileOf(walked.end, cradle), named("Old Songs",
      "Spinning Bee"))
    assertEquals(said(power, walked.events), Vector(nothingLine))

  test("with the region's pile empty nothing is asked, and the line says so"):
    val done = Outcomes.finished(play(Table.start.adviser(p1, help).ready,
      power, help))
    assertEquals(said(power, done.events), Vector(nothingLine))
