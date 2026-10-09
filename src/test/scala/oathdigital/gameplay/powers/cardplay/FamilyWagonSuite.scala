package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.whenplayed.{ASmallFavor, FaithfulFriend}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap

/** Family Wagon (card 168) through card play: under a faceup Family Wagon
  * the arrival pass buries every adviser that is not a faceup Nomad, and
  * card play gives the holder no limit and notes the plays it will bury.
  * The other arrival routes are in `FamilyWagonArrivalsSuite`. */
class FamilyWagonSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, park, placementOptions}

  private val actor = Table.p1
  private val wagon = CatalogNames.denizen("Family Wagon")
  private val power = FamilyWagon.forCatalog(catalog)
  // Nomads.
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val lancers = CatalogNames.denizen("Lancers")
  /** Nomad, locked, adviser-only. WHEN PLAYED: gain 4 Supply. */
  private val friend = CatalogNames.denizen("Faithful Friend")
  /** Discord, locked, adviser-only. WHEN PLAYED: gain four warbands. */
  private val smallFavor = CatalogNames.denizen("A Small Favor")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val faith = VisionRules.Faith
  private val powers =
    WalkerPowers(Vector(power, ASmallFavor, FaithfulFriend))
  private val buryNote = OptionReason.Written(FamilyWagon.buryWarning)

  /** p1's Act at Ancient City with 2 Supply and 3 warbands on the board,
    * `card` in hand, and the advisers `held` adds. */
  private def actWith(card: DenizenId)(held: Table => Table): ReadyGame =
    held(Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .supply(actor, 2).boardWarbands(actor, 3).hand(actor, card)).ready

  /** `card` played from hand to p1's adviser slot `index`, under
    * `powers`. */
  private def playedToAdvisers(ready: ReadyGame, card: DenizenId,
      index: Int, faceup: Boolean): WalkerOutcome.Finished =
    val tree = build(ready, actor, card)
    Unwrap.as[WalkerOutcome.Finished](answer(ready, tree,
      park(ready, tree, powers), powers,
      CardPlayProcedure.placementDecisionId(card),
      DecisionOptionRef.AdviserPlay(actor, index, faceup), actor))

  private def wagonLines(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(FamilyWagon.id, power.noteKeys, events)

  /** Family Wagon's line on its holder's turn, naming `cards`. */
  private def buriedLine(cards: CardId*): Vector[NoteText.Said] =
    Vector(NoteText.Said("buried",
      s"Buried ${cards.map(_.value).mkString(", ")}.", covers = true))

  private def worldDeck(ready: ReadyGame): Vector[WorldCardId] =
    ready.game.current.commonCards.worldDeck

  test("a non-Nomad played faceup is buried at once, and its WHEN PLAYED " +
      "does not run"):
    val ready = actWith(smallFavor)(_.adviser(actor, wagon)
      .adviser(actor, elders))
    val done = playedToAdvisers(ready, smallFavor, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](wagon, elders))
    assertEquals(worldDeck(done.treeless).lastOption, Some(smallFavor))
    // It was never gained, so A Small Favor's four warbands never came.
    assertEquals(Look(done.treeless).boardWarbands(actor), 3)
    assertEquals(wagonLines(done.events), buriedLine(smallFavor))
    assertWireRoundTrips(done.events)
    // Without Family Wagon, the same play gains them.
    val unheld = actWith(smallFavor)(_.adviser(actor, elders))
    assertEquals(Look(playedToAdvisers(unheld, smallFavor, 1,
      faceup = true).treeless).boardWarbands(actor), 3 + 4)

  test("a Nomad played faceup is kept, and its WHEN PLAYED runs"):
    val ready = actWith(friend)(_.adviser(actor, wagon))
    val done = playedToAdvisers(ready, friend, 1, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](wagon, friend))
    assertEquals(Look(done.treeless).supply(actor), 2 + 4)
    assertEquals(wagonLines(done.events), Vector.empty)

  test("a Nomad played facedown is buried, since a facedown card is not a " +
      "Nomad adviser"):
    val ready = actWith(rainBoots)(_.adviser(actor, wagon))
    val done = playedToAdvisers(ready, rainBoots, 1, faceup = false)
    assertEquals(Look(done.treeless).advisers(actor), Vector[CardId](wagon))
    assertEquals(worldDeck(done.treeless).lastOption, Some(rainBoots))
    assertEquals(wagonLines(done.events), buriedLine(rainBoots))

  test("Family Wagon's own arrival buries every adviser that is not a " +
      "faceup Nomad, in held order, a locked one and a Vision too"):
    val ready = actWith(wagon)(_.adviser(actor, wrestlers)
      .adviser(actor, elders).adviser(actor, smallFavor)
      .adviser(actor, faith, facedown = true)
      .adviser(actor, rainBoots, facedown = true))
    // Family Wagon faceup after the play lifts the limit: a new sixth slot.
    val done = playedToAdvisers(ready, wagon, 5, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](elders, wagon))
    assertEquals(worldDeck(done.treeless).takeRight(4),
      Vector[WorldCardId](wrestlers, smallFavor, faith, rainBoots))
    assertEquals(wagonLines(done.events),
      buriedLine(wrestlers, smallFavor, faith, rainBoots))

  test("a facedown Family Wagon played faceup buries the advisers beside " +
      "it"):
    val ready = Table.start.turn(actor, Phase.Act).pawn(actor, "Ancient City")
      .adviser(actor, wagon, facedown = true).adviser(actor, wrestlers)
      .adviser(actor, elders).ready
    val tree = Unwrap.accepted(CardPlayProcedure.buildFacedown(catalog, ready,
      actor, Vector.empty))
    val picked = Unwrap.as[WalkerOutcome.Parked](answer(ready, tree,
      park(ready, tree, powers), powers,
      CardPlayProcedure.facedownAdviserDecisionId,
      DecisionOptionRef.Denizen(wagon), actor))
    val done = Unwrap.as[WalkerOutcome.Finished](answer(ready, tree,
      picked.tree, powers, CardPlayProcedure.placementDecisionId(wagon),
      DecisionOptionRef.AdviserPlay(actor, 0, faceup = true), actor))
    assertEquals(Look(done.treeless).advisers(actor).toSet,
      Set[CardId](wagon, elders))
    assertEquals(worldDeck(done.treeless).lastOption, Some(wrestlers))
    assertEquals(wagonLines(done.events), buriedLine(wrestlers))

  test("the holder has no limit, and a play Family Wagon will bury carries " +
      "a note: a non-Nomad either way, a Nomad facedown"):
    def adviserOptions(card: DenizenId): Vector[DecisionOption] =
      val ready = actWith(card)(_.adviser(actor, wagon)
        .adviser(actor, elders).adviser(actor, rainBoots)
        .adviser(actor, lancers))
      val tree = build(ready, actor, card)
      placementOptions(ready, tree, park(ready, tree, powers), powers)
        .filter(_.ref.isInstanceOf[DecisionOptionRef.AdviserPlay])
    def fifthSlot(faceup: Boolean): DecisionOption =
      DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(actor, 4,
        faceup))
    assertEquals(adviserOptions(smallFavor), Vector[DecisionOption](
      DecisionOption.Warned(fifthSlot(faceup = true), buryNote),
      DecisionOption.Warned(fifthSlot(faceup = false), buryNote)))
    assertEquals(adviserOptions(friend), Vector[DecisionOption](
      fifthSlot(faceup = true),
      DecisionOption.Warned(fifthSlot(faceup = false), buryNote)))

  test("a facedown Family Wagon buries nothing"):
    val ready = actWith(smallFavor)(_.adviser(actor, wagon, facedown = true)
      .adviser(actor, elders))
    val done = playedToAdvisers(ready, smallFavor, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](wagon, elders, smallFavor))
    assertEquals(wagonLines(done.events), Vector.empty)
