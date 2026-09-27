package oathdigital.gameplay

import oathdigital.gameplay.actions.{PlacementRules, RuleNotes}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** The Homeland rule's line (power log lines design, "Game rules"): a play to
  * a full Homeland of the card's suit asks for a discard, and the Homeland
  * says why once the discard is answered.
  */
class HomelandLineSuite extends munit.FunSuite:
  import PlacementFixture._

  private val hall = EdificeId("E16")
  private val none = WalkerPowers.empty

  /** A full site whose Homeland is the Hall, ruled by the actor, and a card
    * in hand whose suit matches the Hall's or not. */
  private def fullHomeland(matching: Boolean): (ReadyGame, PlayerId, DenizenId) =
    val hallSuit = catalog.suitOf(hall).get
    val cards = plain(initialReady)
    val card = cards.find(id =>
      catalog.suitOf(id).contains(hallSuit) == matching).get
    val (_, _, probe) = staged(card, Vector.empty)
    val capacity = catalog.sites.find(_.id == probe).get.capacity
    val fillers = cards.filter(_ != card).take(capacity - 1)
    val (ready, actor, site) = staged(card, fillers.map(denizen(_)) :+
      EdificeState(hall, EdificeSide.Intact, Tokens.empty))
    (ruledByActor(ready, site), actor, card)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(RuleNotes.homelandDiscard, Vector(PlacementRules.discardFirst),
      events)

  test("a play to a full matching Homeland writes the Homeland's line after " +
      "the discard"):
    val (ready, actor, card) = fullHomeland(matching = true)
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
    val (ready, actor, card) = fullHomeland(matching = false)
    val tree = build(ready, actor, card)
    val WalkerOutcome.Parked(pending, events) = ProcedureWalker.advance(ready,
      tree, None, none).toOption.get: @unchecked
    assert(!options(ready, tree, pending, none)
      .contains(DecisionOptionRef.Button("site")))
    assertEquals(said(events), Vector.empty)
