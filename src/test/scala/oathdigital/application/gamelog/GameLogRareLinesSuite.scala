package oathdigital.application.gamelog

import oathdigital.application.WalkerDecisionProjector
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{DeltaPayload, WalkerCompleted,
  WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2, p3}
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
  // Two more sites, for warbands that may not share one.
  private val ancientCity = CatalogNames.site("Ancient City")
  private val buriedGiant = CatalogNames.site("Buried Giant")

  /** The lines one batch of `ops` posts inside a run of `procedure`. */
  private def posted(procedure: ProcedureRef, ops: CoreOperation*)
      : Vector[String] = postedOn(script, procedure, ops*)

  /** `posted`, against a board of this suite's own rather than the shared
    * one. */
  private def postedOn(on: Script, procedure: ProcedureRef,
      ops: CoreOperation*): Vector[String] =
    val tail = Vector[OathEvent](
      WalkerStepRecorded("batch", DeltaPayload(ops.toVector), Vector.empty),
      WalkerCompleted(procedure))
    texts(formatAppended(on, tail)
      .filter(_.sequence >= on.history.steps.size))

  private def lineage(in: ReadyGame, player: PlayerId): LineageId =
    Look(in).player(player).lineage

  /** A board for warband moves: p1 acting, p1 and p2 each holding warbands
    * to move, and Ancient City and Buried Giant swept of the bandits that
    * `banditsRefilled` leaves at every empty site. A site holds one force
    * kind at a time, so each mover needs a site of its own. */
  private lazy val warbandBoard: Script =
    val (service, waking) = atTable("rare-warbands",
      Table.start.pawn(p1, "Ancient City").pawn(p2, "Broken Peaks")
        .pawn(p3, "Buried Giant").turn(p1, Phase.Wake)
        .boardWarbands(p1, 3).boardWarbands(p2, 2)
        .banditsRefilled.bandits("Ancient City", 0).bandits("Buried Giant", 0))
    waking.endingWake(p1)
    Script("rare-warbands", service, p1)

  private lazy val warbandReady: ReadyGame =
    Outcomes.ready(warbandBoard.history.steps.last.after)

  test("gains, draws, buries, peeks and reveals each post one line"):
    // The board's world deck opens with these two Denizens.
    val drawn = Vector("Threatening Roar", "Fae Merchant")
      .map(CatalogNames.denizen(_))
    assertEquals(ready.game.current.commonCards.worldDeck.take(2), drawn)
    val lines = posted(ActionRef.Travel,
      Gain.Favor(other, Suit.Arcane, 2), Gain.Secrets(actor, 1),
      Draw(actor, drawn, Location.Deck(CardDeck.World), Location.Hand(actor)),
      // The peek and the reveal read the relic where it lies; burying it
      // puts it on the relic deck, so the bury comes last.
      Peek(other, relic, Location.Site(site)),
      Reveal(relic, Location.Site(site)),
      Bury(BuryableCard.Relic(relic), PositionedLocation(Location.Site(site))))
    val otherName = playerLabel(other)
    assert(lines.contains(s"$otherName gained 2 favor from the Arcane bank"),
      lines)
    assert(lines.contains("Gained 1 secret"), lines)
    // Both cards, counted by their backs: an observer sees no names.
    assert(lines.contains("Drew 2 Denizens"), lines)
    // The peek names no card, while the bury after the reveal does: the
    // reveal made the relic public one operation earlier.
    assert(lines.contains(s"${playerLabel(other)} peeked at a Relic"), lines)
    assert(lines.contains("Revealed Sticky Fire"), lines)
    assert(lines.contains("Buried Sticky Fire"), lines)

  // Imperial warbands have no case here: no setup seats a Chancellor and no
  // rule makes a Citizen, so `banks.warbandSupply` never holds Imperial and
  // the move is rejected on every board a Table can build. `DetailLines`
  // words them for the day citizenship arrives.
  test("warbands name their owner unless they are the actor's"):
    val mine = ForceKind.Exile(lineage(warbandReady, p1))
    val theirs = ForceKind.Exile(lineage(warbandReady, p2))
    val lines = postedOn(warbandBoard, ActionRef.Travel,
      Move(Piece.Warbands(mine, 2),
        PositionedLocation(Location.PlayArea(p1)),
        PositionedLocation(Location.Site(ancientCity))),
      Move(Piece.Warbands(theirs, 1),
        PositionedLocation(Location.PlayArea(p2)),
        PositionedLocation(Location.Site(buriedGiant))),
      Move(Piece.Warbands(ForceKind.Bandit, 1),
        PositionedLocation(Location.WarbandBank(ForceKind.Bandit)),
        PositionedLocation(Location.Site(site))))
    assertEquals(lines.filter(_.startsWith("Moved ")), Vector(
      s"Moved 2 warbands to ${presentation.siteLabel(ancientCity)}",
      s"Moved 1 of ${playerLabel(p2)}'s warbands to " +
        presentation.siteLabel(buriedGiant),
      s"Moved 1 bandit to ${presentation.siteLabel(site)}"))

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
