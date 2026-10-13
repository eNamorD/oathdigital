package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{AdviserLimit, NoteText}
import oathdigital.gameplay.powers.whenplayed.{ASmallFavor, FaithfulFriend}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Unwrap

/** Vow of Union's restriction half through card play: under a faceup Vow
  * of Union the arrival pass buries every adviser that is not a faceup
  * Beast, as Family Wagon does with Nomads, and the adviser limit stays. */
class VowOfUnionAdvisersSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, park, placementOptions}

  private val actor = Table.p1
  private val vow = CatalogNames.denizen("Vow of Union")
  private val power = VowOfUnionAdvisers.forCatalog(catalog)
  // Beasts with no WHEN PLAYED.
  private val faeMerchant = CatalogNames.denizen("Fae Merchant")
  private val secondChance = CatalogNames.denizen("Second Chance")
  /** Discord, locked, adviser-only. WHEN PLAYED: gain four warbands. */
  private val smallFavor = CatalogNames.denizen("A Small Favor")
  /** Nomad, locked, adviser-only. WHEN PLAYED: gain 4 Supply. */
  private val friend = CatalogNames.denizen("Faithful Friend")
  private val powers =
    WalkerPowers(Vector(power, ASmallFavor, FaithfulFriend))
  private val buryNote = OptionReason.Written(VowOfUnionAdvisers.buryWarning)

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

  private def vowLines(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(VowOfUnionAdvisers.id, power.noteKeys, events)

  /** The line on the holder's turn, naming `cards`. */
  private def buriedLine(cards: CardId*): Vector[NoteText.Said] =
    Vector(NoteText.Said("buried",
      s"Buried ${cards.map(_.value).mkString(", ")}.", covers = true))

  private def worldDeck(ready: ReadyGame): Vector[WorldCardId] =
    ready.game.current.commonCards.worldDeck

  test("a non-Beast played faceup is buried at once, and its WHEN PLAYED " +
      "does not run; under a facedown Vow it stays"):
    val ready = actWith(smallFavor)(_.adviser(actor, vow)
      .adviser(actor, faeMerchant))
    val done = playedToAdvisers(ready, smallFavor, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](vow, faeMerchant))
    assertEquals(worldDeck(done.treeless).lastOption, Some(smallFavor))
    // It was never gained, so A Small Favor's four warbands never came.
    assertEquals(Look(done.treeless).boardWarbands(actor), 3)
    assertEquals(vowLines(done.events), buriedLine(smallFavor))
    assertWireRoundTrips(done.events)
    val hidden = actWith(smallFavor)(_.adviser(actor, vow, facedown = true)
      .adviser(actor, faeMerchant))
    val kept = playedToAdvisers(hidden, smallFavor, 2, faceup = true)
    assertEquals(Look(kept.treeless).advisers(actor),
      Vector[CardId](vow, faeMerchant, smallFavor))
    assertEquals(vowLines(kept.events), Vector.empty)

  test("a Beast played faceup is kept, and one played facedown is " +
      "buried, since a facedown card has no suit"):
    val ready = actWith(secondChance)(_.adviser(actor, vow))
    val faceup = playedToAdvisers(ready, secondChance, 1, faceup = true)
    assertEquals(Look(faceup.treeless).advisers(actor),
      Vector[CardId](vow, secondChance))
    assertEquals(vowLines(faceup.events), Vector.empty)
    val facedown = playedToAdvisers(ready, secondChance, 1, faceup = false)
    assertEquals(Look(facedown.treeless).advisers(actor), Vector[CardId](vow))
    assertEquals(worldDeck(facedown.treeless).lastOption, Some(secondChance))
    assertEquals(vowLines(facedown.events), buriedLine(secondChance))

  test("Vow of Union's own arrival buries every adviser that is not a " +
      "faceup Beast, a locked one too"):
    val ready = actWith(vow)(_.adviser(actor, faeMerchant)
      .adviser(actor, friend))
    val done = playedToAdvisers(ready, vow, 2, faceup = true)
    assertEquals(Look(done.treeless).advisers(actor),
      Vector[CardId](faeMerchant, vow))
    assertEquals(worldDeck(done.treeless).lastOption, Some(friend))
    assertEquals(vowLines(done.events), buriedLine(friend))

  test("a play Vow of Union will bury carries a note, and the holder keeps " +
      "the adviser limit"):
    def adviserOptions(card: DenizenId): Vector[DecisionOption] =
      val ready = actWith(card)(_.adviser(actor, vow)
        .adviser(actor, faeMerchant))
      val tree = build(ready, actor, card)
      placementOptions(ready, tree, park(ready, tree, powers), powers)
        .filter(_.ref.isInstanceOf[DecisionOptionRef.AdviserPlay])
    def thirdSlot(faceup: Boolean): DecisionOption =
      DecisionOption.AdviserPlay(DecisionOptionRef.AdviserPlay(actor, 2,
        faceup))
    assertEquals(adviserOptions(smallFavor), Vector[DecisionOption](
      DecisionOption.Warned(thirdSlot(faceup = true), buryNote),
      DecisionOption.Warned(thirdSlot(faceup = false), buryNote)))
    assertEquals(adviserOptions(secondChance), Vector[DecisionOption](
      thirdSlot(faceup = true),
      DecisionOption.Warned(thirdSlot(faceup = false), buryNote)))
    val holding = actWith(smallFavor)(_.adviser(actor, vow))
    assertEquals(AdviserLimit.of(catalog, holding, actor),
      AdviserLimit.Default)
