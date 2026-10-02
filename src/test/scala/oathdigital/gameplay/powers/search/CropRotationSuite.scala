package oathdigital.gameplay.powers.search

import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.{NoteText, SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.banner.PeoplesFavorMob
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class CropRotationSuite extends munit.FunSuite:
  import SearchFixture.{keep, place, rules, start}

  private val parked = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog))

  private val crop = CatalogNames.denizen("Crop Rotation")
  private val modifiers = Vector(CropRotation.id)
  /** The Beast card each Search keeps and plays. */
  private val played = CatalogNames.denizen("Errand Boy")
  // Order denizens whose powers these tests do not use, to fill the site.
  private val fillers = Vector("Code of Honor", "Messenger", "Shield Wall")
    .map(CatalogNames.denizen(_))
  private val home = CatalogNames.site("Ancient City")
  private val capacity = 3 // Ancient City's printed capacity
  private val noDiscard = CardPlayProcedure.noReplacement.ref
  private val replaceId = PlacementFixture.decisionId(played, "replace")

  /** p1 in Act at Ancient City with 5 Supply, the world deck topped by
    * `played`, and Ancient City holding `site`. p1 holds Crop Rotation as a
    * faceup adviser, or it stands at the site when `atSite`. */
  private def staged(site: Vector[DenizenId], atSite: Boolean = false)
      : ReadyGame =
    val table = Table.start.pawn(p1, home).worldDeckTop(played).supply(p1, 5)
    val held = if atSite then table.denizen(crop, at = home)
      else table.adviser(p1, crop)
    site.foldLeft(held)((t, card) => t.denizen(card, at = home)).ready

  /** Searches with `selected`, keeps `played` and plays it to the site. */
  private def toSite(ready: ReadyGame, selected: Vector[PowerId] = modifiers)
      : Either[OathViolation, OathTransition] = for
    started <- start(ready, selected)
    kept <- keep(started, played)
    placed <- place(kept, played, "site")
  yield placed.copy(events = started.events ++ kept.events ++ placed.events)

  private def asksToDiscard(transition: OathTransition): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == replaceId && facts.awaiting == p1)

  private def discard(from: OathTransition, chosen: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(from.state,
    p1, replaceId, DecisionAnswer.ChooseOneAnswer(chosen))

  private def siteCards(ready: ReadyGame): Vector[CardId] =
    Look(ready).cardsAt(home)

  private def cropSaid(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(CropRotation.id, Vector(
      oathdigital.gameplay.actions.PlacementRules.discardFirst), events)

  test("selected, a play to a site with room asks first, and may decline"):
    val ready = staged(Vector(fillers(0)))
    val asked = accepted(toSite(ready))
    assert(asksToDiscard(asked))
    val done = accepted(discard(asked, noDiscard))
    val end = SearchFixture.after(done)
    assertEquals(siteCards(end), Vector[CardId](fillers(0), played))
    assertEquals(ActPowerHarness.replayed(rules, ready,
      asked.events ++ done.events), end)
    assertWireRoundTrips(asked.events ++ done.events)

  test("choosing a card discards it before the play"):
    val asked = accepted(toSite(staged(Vector(fillers(0)))))
    val done = accepted(discard(asked, DecisionOptionRef.Denizen(fillers(0))))
    assertEquals(siteCards(SearchFixture.after(done)), Vector[CardId](played))

  test("without the selection nothing is asked"):
    val done = accepted(toSite(staged(Vector(fillers(0))), Vector.empty))
    assert(!asksToDiscard(done))
    assertEquals(siteCards(SearchFixture.after(done)),
      Vector[CardId](fillers(0), played))

  test("a full site takes the play only with a discard"):
    val full = staged(fillers.take(capacity))
    assert(toSite(full, Vector.empty).isLeft,
      "a full site accepts no play without a permission")
    val asked = accepted(toSite(full))
    assert(asksToDiscard(asked))
    assert(discard(asked, noDiscard).isLeft, "a discard is required")
    val done = accepted(discard(asked, DecisionOptionRef.Denizen(fillers(0))))
    assert(siteCards(SearchFixture.after(done)).contains(played))

  test("Crop Rotation itself, selected at the site, cannot be the discard"):
    val asked = accepted(toSite(staged(Vector(fillers(0)), atSite = true)))
    assert(asksToDiscard(asked))
    assert(discard(asked, DecisionOptionRef.Denizen(crop)).isLeft,
      "a card that prints a selected power cannot be discarded")
    assert(discard(asked, DecisionOptionRef.Denizen(fillers(0))).isRight)

  test("it applies to a facedown adviser played to a site"):
    val ready = Table.start.adviser(p1, crop)
      .adviser(p1, played, facedown = true)
      .denizen(fillers(0), at = home).ready
    val started = accepted(rules.startWalker(Ready(ready),
      ActionRef.PlayFacedownAdviser, p1, modifiers,
      Vector(DecisionOptionRef.Denizen(played))))
    assert(asksToDiscard(accepted(place(started, played, "site"))))

  test("its line is written once the discard is answered"):
    val asked = accepted(toSite(staged(Vector(fillers(0)))))
    assertEquals(cropSaid(asked.events), Vector.empty)
    val done = accepted(discard(asked, noDiscard))
    assertEquals(cropSaid(done.events), Vector(NoteText.Said("discard-first",
      s"${p1.value} may discard a card at their site first.", covers = false)))

  test("with the Mob as well, only one line is written"):
    val ready = Table.from(staged(Vector(fillers(0)))).peoplesFavor(Some(p1), 1)
      .unchecked
    val asked = accepted(toSite(ready))
    val done = accepted(discard(asked, noDiscard))
    val mobSaid = NoteText.said(PeoplesFavorMob.id, PeoplesFavorMob.noteKeys,
      done.events)
    assertEquals(cropSaid(done.events).size + mobSaid.size, 1)
