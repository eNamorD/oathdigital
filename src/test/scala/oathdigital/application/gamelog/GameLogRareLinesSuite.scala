package oathdigital.application.gamelog

import oathdigital.application.WalkerDecisionProjector
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerCompleted, WalkerStepPayload,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes}
import oathdigital.testkit.Table.p2
import LogScripts._

/** Lines no cheap script reaches: each is driven by one recorded batch
  * appended to `recordedEventsBoard`, the way the victory headlines are, so the
  * rule is read against real state. */
class GameLogRareLinesSuite extends munit.FunSuite:
  private lazy val script = recordedEventsBoard
  private lazy val steps = script.history.steps
  private lazy val ready = Outcomes.ready(steps.last.after)
  private lazy val actor = script.actor
  // The board's p2, who stands at Broken Peaks and holds Birdsong facedown.
  private val other = p2
  // The board's relic, Sticky Fire, lies at Broken Peaks.
  private val site = CatalogNames.site("Broken Peaks")
  private val relic = CatalogNames.relic("Sticky Fire")

  /** The lines one batch of `ops` posts inside a run of `procedure`. */
  private def posted(procedure: ProcedureRef, ops: CoreOperation*)
      : Vector[String] =
    val tail = Vector[OathEvent](
      WalkerStepRecorded("batch", WalkerStepPayload.DeltaRecorded,
        ops.toVector, Vector.empty),
      WalkerCompleted(procedure))
    texts(formatAppended(script, tail).filter(_.sequence >= steps.size))

  private def lineage(player: PlayerId): LineageId =
    Look(ready).player(player).lineage

  test("gains, draws, buries, peeks and reveals each post one line"):
    // The board's world deck opens with these two Denizens.
    val drawn = Vector("Threatening Roar", "Fae Merchant")
      .map(CatalogNames.denizen(_))
    assertEquals(ready.game.current.commonCards.worldDeck.take(2), drawn)
    val lines = posted(ActionRef.Travel,
      Gain.Favor(other, Suit.Arcane, 2), Gain.Secrets(actor, 1),
      Draw(actor, drawn, Location.Deck(CardDeck.World), Location.Hand(actor)),
      Bury(BuryableCard.Relic(relic), PositionedLocation(Location.Site(site))),
      Peek(other, relic, Location.Site(site)),
      Reveal(relic, Location.Site(site)))
    val otherName = playerLabel(other)
    assert(lines.contains(s"$otherName gained 2 favor from the Arcane bank"),
      lines)
    assert(lines.contains("Gained 1 secret"), lines)
    // Both cards, counted by their backs: an observer sees no names.
    assert(lines.contains("Drew 2 Denizens"), lines)
    assert(lines.contains("Buried a Relic"), lines)
    assert(lines.contains(s"${playerLabel(other)} peeked at a Relic"), lines)
    assert(lines.exists(_.startsWith("Revealed ")), lines)

  test("warbands name their owner unless they are the actor's"):
    val lines = posted(ActionRef.Travel,
      Move(Piece.Warbands(ForceKind.Exile(lineage(actor)), 2),
        PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(site))),
      Move(Piece.Warbands(ForceKind.Exile(lineage(other)), 1),
        PositionedLocation(Location.PlayArea(other)),
        PositionedLocation(Location.Site(site))),
      Move(Piece.Warbands(ForceKind.Bandit, 1),
        PositionedLocation(Location.WarbandBank(ForceKind.Bandit)),
        PositionedLocation(Location.Site(site))),
      Move(Piece.Warbands(ForceKind.Imperial, 3),
        PositionedLocation(Location.WarbandBank(ForceKind.Imperial)),
        PositionedLocation(Location.Site(site))))
    val at = presentation.siteLabel(site)
    assertEquals(lines.filter(_.startsWith("Moved ")), Vector(
      s"Moved 2 warbands to $at",
      s"Moved 1 of ${playerLabel(other)}'s warbands to $at",
      s"Moved 1 bandit to $at",
      s"Moved 3 Imperial warbands to $at"))

  test("a vision discarded names its pile"):
    // Vision of Sanctuary lies in the board's world deck.
    val vision = VisionRules.Sanctuary
    assert(ready.game.current.commonCards.worldDeck.contains(vision))
    val lines = posted(ActionRef.Travel, Discard.Vision(vision,
      PositionedLocation(Location.Deck(CardDeck.World)), Region.Hinterland))
    assert(lines.contains("Discarded a Vision to the Hinterland discard"),
      lines)

  test("dice read as their faces' rulebook names, and no dice or mixed dice " +
      "read as nothing"):
    assertEquals(LogWords.dice(Vector(AttackDieFace.HollowSword,
      AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull)).map(_.text),
      Some("hollow sword, sword, two swords and a skull"))
    assertEquals(LogWords.dice(Vector(DefenseDieFace.Blank,
      DefenseDieFace.OneShield, DefenseDieFace.TwoShields,
      DefenseDieFace.Doubler)).map(_.faces.map(_.key)),
      Some(Vector("blank", "one-shield", "two-shields", "doubler")))
    assertEquals(LogWords.dice(Vector.empty), None)
    assertEquals(LogWords.dice(Vector(AttackDieFace.OneSword,
      DefenseDieFace.Blank)), None)

  test("a chosen option of every kind reads as its name, never its key"):
    val words = new LogWords(catalog, presentation)
    val choices = new ChoiceWords(words,
      new WalkerDecisionProjector(catalog, presentation))
    def said(ref: DecisionOptionRef): String =
      choices.option(ref, ready, ready, None).map(_.text).mkString
    // Threatening Roar, the board's top world-deck card (pinned by the
    // first test here), unseen by all.
    val denizen = CatalogNames.denizen("Threatening Roar")
    assertEquals(said(DecisionOptionRef.Player(other)), playerLabel(other))
    assertEquals(said(DecisionOptionRef.Site(site)), presentation.siteLabel(site))
    assertEquals(said(DecisionOptionRef.Denizen(denizen)), "a Denizen")
    assertEquals(said(DecisionOptionRef.Relic(relic)), "a Relic")
    // A slot reads as its index plus one. A relic slot always reads
    // facedown, whatever p2 holds; p2's Birdsong is facedown; adviser slot
    // 10 is empty and names no face.
    assertEquals(said(DecisionOptionRef.RelicSlot(other, 0)),
      s"${playerLabel(other)}'s facedown relic (slot 1)")
    assertEquals(said(DecisionOptionRef.AdviserSlot(other, 0)),
      s"${playerLabel(other)}'s facedown adviser (slot 1)")
    assertEquals(said(DecisionOptionRef.AdviserSlot(other, 9)),
      s"${playerLabel(other)}'s adviser (slot 10)")
    assertEquals(said(DecisionOptionRef.Banner(Banner.PeoplesFavor)),
      "People's Favor")
    assertEquals(said(DecisionOptionRef.Deck(CardDeck.World)), "World")
    assertEquals(said(DecisionOptionRef.FavorBank(Suit.Order)),
      "the Order bank")
    // Nothing is parked, so a button falls back to its key's label.
    assertEquals(said(DecisionOptionRef.Button("search:world")), "World")
    assert(said(DecisionOptionRef.Vision(VisionRules.Sanctuary)) == "a Vision")
