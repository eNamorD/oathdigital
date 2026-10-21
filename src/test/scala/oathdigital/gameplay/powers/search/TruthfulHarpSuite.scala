package oathdigital.gameplay.powers.search

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, PowerRules,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p2, p3}
import oathdigital.testkit.Unwrap.accepted

class TruthfulHarpSuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val harp = CatalogNames.relic("Truthful Harp")
  // The players besides p1, who see what p1 reveals.
  private val viewers = Vector(p2, p3)
  private val onlyHarp = Vector(TruthfulHarp.id)
  private val both = Vector(TruthfulHarp.id, Augury.id)

  /** p1's Act at Ancient City holding Truthful Harp faceup, with 5 Supply
    * and the world deck topped by `top`. */
  private def withHarp(top: Vector[WorldCardId]): ReadyGame =
    withRelic(SearchFixture.staged(top, supply = 5), harp)

  private def hand(transition: OathTransition): Vector[WorldCardId] =
    SearchFixture.after(transition).game.current.temporaryHands(actor)

  private def known(transition: OathTransition, viewer: PlayerId)
      : Vector[WorldCardId] = SearchFixture.after(transition).knowledge
    .advisers.getOrElse(viewer, Vector.empty)

  private val power = TruthfulHarp.forCatalog(catalog)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("a Search draws two more cards, and every card drawn is revealed"):
    val top = discordCards.take(7)
    val ready = withHarp(top)
    val started = accepted(start(ready, onlyHarp))
    assertEquals(hand(started), top.take(5))
    viewers.foreach(viewer =>
      assertEquals(known(started, viewer).toSet, top.take(5).toSet[WorldCardId]))
    assertEquals(ActPowerHarness.replayed(rules, ready, started.events),
      SearchFixture.after(started))
    assertWireRoundTrips(started.events)

  test("the reveal does not change the play: the kept card can still be played " +
      "facedown as an adviser, as usual"):
    val top = discordCards.take(7)
    val ready = withHarp(top)
    val done = play(ready, onlyHarp, top(4), PlayedAs.Facedown)
    val advisers = Look(SearchFixture.after(done)).player(actor).advisers
    assert(advisers.exists {
      case card: DenizenState => card.id == top(4) &&
        card.orientation == Orientation.FaceDown
      case _ => false
    })
    // The other players saw the card while it was revealed, and remember it.
    // That is what a reveal at a table leaves behind, and it needs no rule.
    assert(known(done, p2).contains(top(4)))

  test("the Harp and Augury stack, and the reveal covers all six cards"):
    val top = discordCards.take(8)
    val augury = CatalogNames.denizen("Augury")
    val ready = Table.from(withHarp(top)).denizen(augury, at = "Ancient City")
      .ready
    val started = accepted(start(ready, both))
    assertEquals(hand(started), top.take(6))
    viewers.foreach(viewer =>
      assertEquals(known(started, viewer).toSet, top.take(6).toSet[WorldCardId]))

  test("nothing is revealed without the Harp"):
    val top = discordCards.take(7)
    val started = accepted(start(withHarp(top)))
    assertEquals(hand(started), top.take(3))
    viewers.foreach(viewer =>
      assertEquals(known(started, viewer), Vector.empty[WorldCardId]))

  test("a facedown Harp is not offered, so the Search starts on its source"):
    val facedown = withRelic(
      SearchFixture.staged(discordCards.take(7), supply = 5), harp,
      Orientation.FaceDown)
    val started = accepted(SearchFixture.rules.startWalker(Ready(facedown),
      ActionRef.Search, actor))
    PowerRules.parkedUnderWalkerPowers.assertParked(started.state,
      ActionRef.Search, SearchProcedure.sourceDecisionId, actor)

  // ---- Lines ----

  test("the revealed hand is the Harp's line, in place of the peeks"):
    val top = discordCards.take(7)
    val started = accepted(start(withHarp(top), onlyHarp))
    assertEquals(said(started.events), Vector(NoteText.Said("revealed",
      s"Revealed ${top.take(5).map(_.value).mkString(", ")}.", covers = true)))

  test("a Search without the Harp writes no Harp line"):
    assertEquals(said(accepted(start(withHarp(discordCards.take(7)))).events),
      Vector.empty)
