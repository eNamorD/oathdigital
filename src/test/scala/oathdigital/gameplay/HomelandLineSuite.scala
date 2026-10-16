package oathdigital.gameplay

import oathdigital.gameplay.actions.{CardPlayRules, RuleNotes}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.as

/** The Homeland rule's line (power log lines design, "Game rules"): a play to
  * the Homeland of the card's suit may replace a card there, and the Homeland
  * says why when it does. A play that replaces nothing writes nothing.
  */
class HomelandLineSuite extends munit.FunSuite:
  import PlacementFixture.{answer, build, cardPlayDecisionId, options, park}

  private val noPowers = WalkerPowers.empty
  private val actor = p1
  /** The Beast Homeland, with room for three cards. */
  private val homeSite = CatalogNames.site("Deep Woods")
  // A Beast card, matching the Homeland, and a Nomad card, which does not.
  private val matching = CatalogNames.denizen("Errand Boy")
  private val otherSuit = CatalogNames.denizen("Rain Boots")

  /** p1 stands at Deep Woods, which p1 rules, with `card` in hand. Deep Woods
    * holds three other-suit cards if `full`, else one. */
  private def atHomeland(card: DenizenId, full: Boolean = true): ReadyGame =
    val fillers = Vector("Ancient Binding", "Wrestlers", "Battle Honors")
      .take(if full then 3 else 1)
    fillers.foldLeft(Table.start
      .pawn(actor, at = homeSite).warbandsAt(homeSite, actor, 1)
      .hand(actor, card))((table, held) => table.denizen(held, at = homeSite))
      .ready

  /** Plays `card` to Deep Woods' slot `index`. */
  private def playedTo(ready: ReadyGame, card: DenizenId, index: Int)
      : WalkerOutcome.Finished =
    val tree = build(ready, actor, card)
    as[WalkerOutcome.Finished](answer(ready, tree, park(ready, tree, noPowers),
      noPowers, cardPlayDecisionId(card, "place"),
      DecisionOptionRef.SiteSlot(homeSite, index), actor))

  /** The Homeland's line: the actor may discard a site card first. */
  private val discardFirstLine = Vector(NoteText.Said("discard-first",
    s"May discard a card at ${homeSite.value} first.", covers = false))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(RuleNotes.homelandDiscard, Vector(CardPlayRules.discardFirst),
      events)

  test("a play replacing a card at a full matching Homeland writes the " +
      "Homeland's line"):
    assertEquals(said(playedTo(atHomeland(matching), matching, 0).events),
      discardFirstLine)

  test("a full Homeland of another suit offers no site slot, and writes " +
      "nothing"):
    val ready = atHomeland(otherSuit)
    val tree = build(ready, actor, otherSuit)
    val parked = Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, noPowers))
    assert(!options(ready, tree, parked.tree, noPowers)
      .exists(_.isInstanceOf[DecisionOptionRef.SiteSlot]))
    assertEquals(said(parked.events), Vector.empty)

  test("a play replacing a card at a matching Homeland with room writes " +
      "the line"):
    assertEquals(said(playedTo(atHomeland(matching, full = false), matching, 0)
      .events), discardFirstLine)

  test("a play to the empty slot of a matching Homeland discards nothing and " +
      "writes nothing"):
    val done = playedTo(atHomeland(matching, full = false), matching, 1)
    assert(Look(done.treeless).cardsAt(homeSite).contains(matching))
    assertEquals(said(done.events), Vector.empty)
