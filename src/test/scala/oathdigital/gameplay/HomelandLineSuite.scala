package oathdigital.gameplay

import oathdigital.gameplay.actions.{PlacementRules, RuleNotes}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** The Homeland rule's line (power log lines design, "Game rules"): a play to
  * the Homeland of the card's suit asks for a discard, and the Homeland says
  * why once the discard is answered.
  */
class HomelandLineSuite extends munit.FunSuite:
  import PlacementFixture._

  private val none = WalkerPowers.empty
  private val (homeSite, homeSuit) = homeland

  /** The Homeland, full or with room, ruled by the actor, and a card in hand
    * whose suit is the Homeland's or not. */
  private def atHomeland(matching: Boolean, full: Boolean = true)
      : (ReadyGame, PlayerId, DenizenId) =
    val cards = plain(initialReady)
    val card = cards.find(id =>
      catalog.suitOf(id).contains(homeSuit) == matching).get
    val capacity = catalog.site(homeSite).get.capacity
    val fillers = cards.filter(_ != card).take(if full then capacity else 1)
    val (ready, actor, site) = staged(card, fillers.map(denizen(_)),
      Some(homeSite))
    (ruledByActor(ready, site), actor, card)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(RuleNotes.homelandDiscard, Vector(PlacementRules.discardFirst),
      events)

  test("a play to a full matching Homeland writes the Homeland's line after " +
      "the discard"):
    val (ready, actor, card) = atHomeland(matching = true)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, asked) = answer(ready, tree,
      park(ready, tree, none), none, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor): @unchecked
    assertEquals(said(asked), Vector.empty)
    val discarded = options(ready, tree, pending, none).head
    val WalkerOutcome.Finished(_, events) = answer(ready, tree, pending, none,
      decisionId(card, "replace"), discarded, actor): @unchecked
    assertEquals(said(events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))

  test("a full Homeland of another suit offers no site, and writes nothing"):
    val (ready, actor, card) = atHomeland(matching = false)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, events) = ProcedureWalker.advance(ready,
      tree, None, none).toOption.get: @unchecked
    assert(!options(ready, tree, pending, none)
      .contains(DecisionOptionRef.Button("site")))
    assertEquals(said(events), Vector.empty)

  test("a play with room at a matching Homeland writes the line after the " +
      "discard it chose"):
    val (ready, actor, card) = atHomeland(matching = true, full = false)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, _) = answer(ready, tree,
      park(ready, tree, none), none, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor): @unchecked
    val discarded = options(ready, tree, pending, none).collectFirst {
      case ref: DecisionOptionRef.Denizen => ref }.get
    val WalkerOutcome.Finished(_, events) = answer(ready, tree, pending, none,
      decisionId(card, "replace"), discarded, actor): @unchecked
    assertEquals(said(events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))

  test("a play with room at a matching Homeland writes the line when it " +
      "declines the discard"):
    val (ready, actor, card) = atHomeland(matching = true, full = false)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, _) = answer(ready, tree,
      park(ready, tree, none), none, decisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor): @unchecked
    val done @ WalkerOutcome.Finished(_, events) = answer(ready, tree, pending,
      none, decisionId(card, "replace"),
      CardPlayProcedure.noReplacement.ref, actor): @unchecked
    assert(done.treeless.game.current.map.sites(homeSite).denizens
      .exists(_.id == card))
    assertEquals(said(events), Vector(NoteText.Said("discard-first",
      s"${actor.value} may discard a card at their site first.", covers = false)))
