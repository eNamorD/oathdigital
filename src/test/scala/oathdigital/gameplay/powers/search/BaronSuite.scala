package oathdigital.gameplay.powers.search

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Baron (card 250), a selected Search modifier: "You may play to any site
  * you rule, and you may discard a card from that site first." */
class BaronSuite extends munit.FunSuite:
  import SearchFixture.{keep, placeAt, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val baron = CatalogNames.denizen("Baron")
  private val modifiers = Vector(Baron.id)
  /** The plain Beast card each Search keeps and plays. */
  private val played = CatalogNames.denizen("Errand Boy")
  private val fillers = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))
  private val home = CatalogNames.site("Ancient City")
  /** Room for one, no Homeland. */
  private val giant = CatalogNames.site("Buried Giant")
  private val peaks = CatalogNames.site("Broken Peaks")

  /** p1 in Act at Ancient City holding Baron faceup, with 5 Supply and
    * `played` on the world deck. p1 rules Buried Giant, which holds
    * `atGiant`; p2 rules Broken Peaks. */
  private def ruling(atGiant: Vector[DenizenId]): Table =
    atGiant.foldLeft(Table.start.pawn(p1, home).worldDeckTop(played)
      .supply(p1, 5).adviser(p1, baron).warbandsAt(giant, p1, 1)
      .warbandsAt(peaks, p2, 1))((t, card) => t.denizen(card, at = giant))

  private def toPlacement(ready: ReadyGame,
      selected: Vector[PowerId] = modifiers): OathTransition = accepted(for
    started <- start(ready, selected)
    kept <- keep(started, played)
  yield kept.copy(events = started.events ++ kept.events))

  private def slotsOf(at: OathTransition): Vector[(DecisionOptionRef,
      Option[CardId])] =
    PlacementPicks.siteSlotOptions(PlacementPicks.parkedOptions(at.state,
      walkerPowers)).map(option => option.ref -> PlacementPicks.replaces(
      option))

  test("selected, it offers each site the player rules after the pawn " +
      "site, and no site of another ruler"):
    val ready = ruling(Vector.empty).ready
    assertEquals(PowerAccess.ruledSites(ready, p1), Set(giant))
    assertEquals(slotsOf(toPlacement(ready)), Vector(
      DecisionOptionRef.SiteSlot(home, 0) -> None,
      DecisionOptionRef.SiteSlot(giant, 0) -> None))

  test("a full ruled site offers its card as Replace"):
    assertEquals(slotsOf(toPlacement(ruling(fillers.take(1)).ready)), Vector(
      DecisionOptionRef.SiteSlot(home, 0) -> None,
      DecisionOptionRef.SiteSlot(giant, 0) -> Some(fillers(0))))

  test("without the selection only the pawn site is offered"):
    assertEquals(slotsOf(toPlacement(ruling(Vector(fillers(0))).ready,
      Vector.empty)), Vector(DecisionOptionRef.SiteSlot(home, 0) -> None))

  test("its discard holds at a pawn site the player rules too"):
    val ready = ruling(Vector.empty).warbandsAt(home, p1, 1)
      .denizen(fillers(1), at = home).ready
    assert(slotsOf(toPlacement(ready)).contains(
      DecisionOptionRef.SiteSlot(home, 0) -> Some(fillers(1))))

  test("a full ruled site takes the play through Replace: the discard goes " +
      "to the pile after that site's region, with Baron's line"):
    val ready = ruling(fillers.take(1)).ready
    val asked = toPlacement(ready)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers(0)))
    val end = SearchFixture.after(done)
    assertEquals(Look(end).cardsAt(giant), Vector[CardId](played))
    assert(Look(end).discardAfter(giant).contains(fillers(0)))
    assertEquals(NoteText.said(Baron.id, Vector(CardPlayRules.discardFirst),
      done.events), Vector(NoteText.Said("discard-first",
      s"May discard a card at ${giant.value} first.", covers = false)))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("an empty slot at a ruled site plays there and discards nothing"):
    val asked = toPlacement(ruling(Vector.empty).ready)
    val done = accepted(placeAt(asked, played,
      DecisionOptionRef.SiteSlot(giant, 0)))
    assertEquals(Look(SearchFixture.after(done)).cardsAt(giant),
      Vector[CardId](played))
    assertEquals(NoteText.said(Baron.id, Vector(CardPlayRules.discardFirst),
      done.events), Vector.empty)
