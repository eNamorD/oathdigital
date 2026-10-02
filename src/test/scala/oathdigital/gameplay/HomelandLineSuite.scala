package oathdigital.gameplay

import oathdigital.gameplay.actions.{PlacementRules, RuleNotes}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{as, present}

/** The Homeland rule's line (power log lines design, "Game rules"): a play to
  * the Homeland of the card's suit asks for a discard, and the Homeland says
  * why once the discard is answered.
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

  /** Answers the placement with "site", returning the replacement park. */
  private def playToSite(ready: ReadyGame, tree: Operation, card: DenizenId)
      : WalkerOutcome.Parked =
    as[WalkerOutcome.Parked](answer(ready, tree, park(ready, tree, noPowers),
      noPowers, cardPlayDecisionId(card, "place"),
      DecisionOptionRef.Button("site"), actor))

  private def replaceWith(ready: ReadyGame, tree: Operation,
      pending: PendingTree, card: DenizenId, ref: DecisionOptionRef)
      : WalkerOutcome.Finished =
    as[WalkerOutcome.Finished](answer(ready, tree, pending, noPowers,
      cardPlayDecisionId(card, "replace"), ref, actor))

  /** The Homeland's line: the actor may discard a site card first. */
  private val discardFirstLine = Vector(NoteText.Said("discard-first",
    s"${actor.value} may discard a card at their site first.", covers = false))

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(RuleNotes.homelandDiscard, Vector(PlacementRules.discardFirst),
      events)

  test("a play to a full matching Homeland writes the Homeland's line after " +
      "the discard"):
    val ready = atHomeland(matching)
    val tree = build(ready, actor, matching)
    val asked = playToSite(ready, tree, matching)
    assertEquals(said(asked.events), Vector.empty)
    val discarded = present(
      options(ready, tree, asked.tree, noPowers).headOption, "a discard option")
    val done = replaceWith(ready, tree, asked.tree, matching, discarded)
    assertEquals(said(done.events), discardFirstLine)

  test("a full Homeland of another suit offers no site, and writes nothing"):
    val ready = atHomeland(otherSuit)
    val tree = build(ready, actor, otherSuit)
    val parked = Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, noPowers))
    assert(!options(ready, tree, parked.tree, noPowers)
      .contains(DecisionOptionRef.Button("site")))
    assertEquals(said(parked.events), Vector.empty)

  test("a play with room at a matching Homeland writes the line after the " +
      "discard it chose"):
    val ready = atHomeland(matching, full = false)
    val tree = build(ready, actor, matching)
    val asked = playToSite(ready, tree, matching)
    val discarded = present(options(ready, tree, asked.tree, noPowers)
      .collectFirst { case ref: DecisionOptionRef.Denizen => ref },
      "a denizen to discard")
    val done = replaceWith(ready, tree, asked.tree, matching, discarded)
    assertEquals(said(done.events), discardFirstLine)

  test("a play with room at a matching Homeland writes the line when it " +
      "declines the discard"):
    val ready = atHomeland(matching, full = false)
    val tree = build(ready, actor, matching)
    val asked = playToSite(ready, tree, matching)
    val done = replaceWith(ready, tree, asked.tree, matching,
      CardPlayProcedure.noReplacement.ref)
    assert(Look(done.treeless).cardsAt(homeSite).contains(matching))
    assertEquals(said(done.events), discardFirstLine)
