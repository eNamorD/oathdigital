package oathdigital.gameplay.powers.search

import oathdigital.gameplay.FacedownAdviserPlay
import oathdigital.gameplay.actions.CardPlayRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.banner.PeoplesFavorMob
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PlacementPicks, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, present}

class CropRotationSuite extends munit.FunSuite:
  import SearchFixture.{discardMore, keep, place, placeReplacing, rules, start}

  private val walkerPowers = WalkerPowerCatalog.default(catalog)
  private val crop = CatalogNames.denizen("Crop Rotation")
  private val modifiers = Vector(CropRotation.id)
  /** The Beast card each Search keeps and plays. */
  private val played = CatalogNames.denizen("Errand Boy")
  // Order denizens whose powers these tests do not use, to fill the site.
  private val fillers = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))
  private val home = CatalogNames.site("Ancient City")
  private val capacity = 3 // Ancient City's printed capacity

  /** p1 in Act at Ancient City with 5 Supply, the world deck topped by
    * `played`, and Ancient City holding `site`. p1 holds Crop Rotation as a
    * faceup adviser, or it stands at the site when `atSite`. */
  private def staged(site: Vector[DenizenId], atSite: Boolean = false)
      : ReadyGame =
    val table = Table.start.pawn(p1, home).worldDeckTop(played).supply(p1, 5)
    val held = if atSite then table.denizen(crop, at = home)
      else table.adviser(p1, crop)
    site.foldLeft(held)((t, card) => t.denizen(card, at = home)).ready

  /** Searches with `selected` and keeps `played`, parked on its placement. */
  private def toPlacement(ready: ReadyGame,
      selected: Vector[PowerId] = modifiers): OathTransition = accepted(for
    started <- start(ready, selected)
    kept <- keep(started, played)
  yield kept.copy(events = started.events ++ kept.events))

  /** The parked placement's site slots, each as the card it replaces. */
  private def siteSlots(at: OathTransition): Vector[Option[CardId]] =
    PlacementPicks.parkedOptions(at.state, walkerPowers)
      .filter(_.ref.isInstanceOf[DecisionOptionRef.SiteSlot])
      .map(PlacementPicks.replaces)

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    Look(ready).cardsAt(home)

  private def cropSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(CropRotation.id, Vector(CardPlayRules.discardFirst), events)

  test("selected, it offers each site denizen as Replace beside the empty " +
      "slot, and the empty slot discards nothing"):
    val ready = staged(Vector(fillers(0)))
    val asked = toPlacement(ready)
    assertEquals(siteSlots(asked), Vector(None, Some(fillers(0))))
    val done = accepted(place(asked, played, PlayedAs.AtSite))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](fillers(0), played))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("a Replace slot discards the card before the play"):
    val done = accepted(placeReplacing(toPlacement(staged(Vector(fillers(0)))),
      played, PlayedAs.AtSite, fillers(0)))
    assertEquals(siteCards(SearchFixture.after(done)), Vector[CardId](played))

  test("without the selection nothing may be replaced"):
    assertEquals(siteSlots(toPlacement(staged(Vector(fillers(0))),
      Vector.empty)), Vector(None))

  test("a full site takes the play only through a Replace slot"):
    val full = staged(fillers.take(capacity))
    assert(place(toPlacement(full, Vector.empty), played, PlayedAs.AtSite)
      .isLeft, "a full site accepts no play without a permission")
    val asked = toPlacement(full)
    assertEquals(siteSlots(asked), fillers.take(capacity).map(Some(_)))
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers(0)))
    assert(siteCards(SearchFixture.after(done)).contains(played))

  test("Crop Rotation itself, selected at the site, is shown in use and " +
      "cannot be replaced"):
    val asked = toPlacement(staged(Vector(fillers(0)), atSite = true))
    val cropSlot = present(PlacementPicks.parkedOptions(asked.state,
      walkerPowers).find(option =>
        PlacementPicks.replaces(option).contains(crop)), "Crop Rotation's slot")
    assert(!cropSlot.available)
    assert(placeReplacing(asked, played, PlayedAs.AtSite, crop).isLeft,
      "a card that prints a selected power cannot be discarded")
    assert(placeReplacing(asked, played, PlayedAs.AtSite, fillers(0)).isRight)

  test("it covers a ruined edifice too: an edifice is a denizen for placement"):
    val hall = CatalogNames.edifice("Hall of Ministers")
    val ready = Table.from(staged(Vector(fillers(0))))
      .edifice(hall, EdificeSide.Ruined, at = home).unchecked
    assertEquals(siteSlots(toPlacement(ready)),
      Vector(None, Some(fillers(0)), Some(hall)))

  test("it applies to a facedown adviser played to a site"):
    val ready = Table.start.adviser(p1, crop)
      .adviser(p1, played, facedown = true)
      .denizen(fillers(0), at = home).ready
    val started = accepted(FacedownAdviserPlay.parkedOnPlacement(rules,
      Ready(ready), p1, played, modifiers))
    assert(siteSlots(started).contains(Some(fillers(0))))

  test("its line is written for the discard it allowed"):
    val asked = toPlacement(staged(Vector(fillers(0))))
    assertEquals(cropSaid(asked.events), Vector.empty)
    val done = accepted(placeReplacing(asked, played, PlayedAs.AtSite,
      fillers(0)))
    assertEquals(cropSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${p1.value} may discard a card at their site first.", covers = false)))

  test("with the Mob as well, two cards may go, each credited to the " +
      "permission it spent"):
    val ready = Table.from(staged(Vector(fillers(0), fillers(1))))
      .peoplesFavor(Some(p1), 1).unchecked
    val replaced = accepted(placeReplacing(toPlacement(ready), played,
      PlayedAs.AtSite, fillers(0)))
    // The slot spent one permission; the other is offered as a follow-up.
    val done = accepted(discardMore(replaced, played, Vector(fillers(1))))
    assertEquals(siteCards(SearchFixture.after(done)), Vector[CardId](played))
    val events = replaced.events ++ done.events
    assertEquals(cropSaid(events).size, 1)
    assertEquals(NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys,
      events).size, 1)
