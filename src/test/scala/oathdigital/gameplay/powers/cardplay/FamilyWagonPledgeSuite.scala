package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.ProcedureNotes
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.PartitionAnswer
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Unwrap.accepted

/** Family Wagon beside Pledge to Discord (catalog batch 8 rulings, slice
  * 8): what goes whatever the order is buried at once, then the active
  * player sorts the Nomads between burial before Pledge and keeping. */
class FamilyWagonPledgeSuite extends munit.FunSuite:
  import PowerFixture.actor

  private val power = FamilyWagon.forCatalog(catalog)
  private val wagon = CatalogNames.denizen("Family Wagon")
  private val pledge = CatalogNames.denizen("Pledge to Discord")
  private val elders = CatalogNames.denizen("Elders")
  private val rainBoots = CatalogNames.denizen("Rain Boots")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val fillers = Vector("Homesteaders", "Land Warden")
    .map(CatalogNames.denizen(_))

  /** The actor with Pledge to Discord and `advisers`, about to Search
    * Family Wagon off the deck. */
  private def pledgedWith(advisers: DenizenId*)(
      using munit.Location): ReadyGame =
    advisers.foldLeft(Table.from(SearchFixture.staged(
      Vector(wagon) ++ fillers, supply = 5)).adviser(actor, pledge))(
      (table, card) => table.adviser(actor, card)).ready

  private def playedFaceup(ready: ReadyGame): OathTransition =
    SearchFixture.play(ready, Vector.empty, wagon, PlayedAs.Faceup)

  private def wagonLines(events: Vector[OathEvent]): Vector[String] =
    NoteText.said(FamilyWagon.id, power.noteKeys, events).map(_.key)

  /** The lines the card play itself wrote, by key. */
  private def playLines(events: Vector[OathEvent]): Vector[String] =
    NoteText.said(ProcedureNotes.id(ActionRef.PlayFacedownAdviser),
      CardPlayProcedure.noteKeys, events).map(_.key)

  test("the Order adviser goes at once; the Nomads are sorted, and those " +
      "buried before Pledge return their favor to the Discord bank"):
    val ready = Table.from(pledgedWith(elders, rainBoots, wrestlers))
      .tokens(elders, favor = 1).ready
    val played = playedFaceup(ready)
    assertEquals(PhasePowerHarness.offered(played, actor), Some(Vector(
      "denizen" -> elders.value, "denizen" -> rainBoots.value)))
    val done = accepted(SearchFixture.rules.resolveWalker(played.state,
      actor, FamilyWagon.pledgeDecisionId, PartitionAnswer(Vector(
        DecisionPlacement(DecisionOptionRef.Denizen(elders),
          SuitOnlyAdvisers.buryKey),
        DecisionPlacement(DecisionOptionRef.Denizen(rainBoots),
          SuitOnlyAdvisers.keepKey)))))
    val end = SearchFixture.after(done)
    assertEquals(Look(end).advisers(actor),
      Vector[CardId](rainBoots, wagon))
    assertEquals(end.banks.favor.getOrElse(Suit.Discord, 0),
      ready.banks.favor.getOrElse(Suit.Discord, 0) + 1)
    assertEquals(wagonLines(played.events ++ done.events),
      Vector("buried", "buried"))
    // The play's own line survives the park. This power's follow-up walks
    // under the play leaf's own path, so the park ends the command before
    // the line's leaf is reached and only the resumed shape can write it
    // (procedures journal their own notes, slice 3). Exactly once, in the
    // resuming command.
    assertEquals(playLines(played.events), Vector.empty)
    assertEquals(playLines(done.events), Vector("played-faceup-adviser"))

  test("with no Nomad beside Pledge, nothing is asked and every other " +
      "adviser is buried at once"):
    val played = playedFaceup(pledgedWith(wrestlers))
    assertEquals(Look(SearchFixture.after(played)).advisers(actor),
      Vector[CardId](wagon))
    assertEquals(wagonLines(played.events), Vector("buried"))
    assertEquals(playLines(played.events), Vector("played-faceup-adviser"))

  test("the heading speaks to the holder, or names them as its holder"):
    assertEquals(SuitOnlyAdvisers.pledgeHeading("Family Wagon", Suit.Nomad,
      holderAnswers = true), "Family Wagon: Depending on when you bury " +
      "Pledge to Discord, you can keep all or none of your Nomad advisers. " +
      "Choose which Nomad advisers you keep.")
    assertEquals(SuitOnlyAdvisers.pledgeHeading("Vow of Union", Suit.Beast,
      holderAnswers = false), "Vow of Union: Depending on when Pledge to " +
      "Discord is buried, its holder keeps all or none of their Beast " +
      "advisers. Choose which they keep.")
