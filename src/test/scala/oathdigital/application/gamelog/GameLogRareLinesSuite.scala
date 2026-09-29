package oathdigital.application.gamelog

import oathdigital.application.WalkerDecisionProjector
import oathdigital.engine.{RecordedEvent, ReplayStep}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{DeltaMeaning, WalkerCompleted,
  WalkerStepPayload, WalkerStepRecorded}
import oathdigital.model._
import LogScripts._

/** Lines no cheap script reaches: each is driven by one recorded batch
  * appended to the board script, the way the victory headlines are, so the
  * rule is read against real state. */
class GameLogRareLinesSuite extends munit.FunSuite:
  private lazy val script = board
  private lazy val steps = script.history.steps
  private lazy val ready = steps.last.after match
    case OathState.Ready(ready) => ready
    case other => fail(s"expected a ready game, got $other")
  private lazy val actor = script.actor
  private lazy val other = script.players.find(_ != actor).get
  private lazy val (site, relic) = ready.game.current.map.sites.collectFirst {
    case (id, state) if state.relics.nonEmpty => id -> state.relics.head.id
  }.get

  /** The lines one batch of `ops` posts inside a run of `procedure`. */
  private def posted(procedure: ProcedureRef, ops: CoreOperation*)
      : Vector[String] =
    val last = steps.last.after
    val tail = Vector[OathEvent](
      WalkerStepRecorded("batch", WalkerStepPayload.DeltaRecorded(
        DeltaMeaning.OperationApplied("batch")), ops.toVector, Vector.empty),
      WalkerCompleted(procedure))
    texts(formatter.format(steps ++ tail.zipWithIndex.map {
      case (event, index) => ReplayStep(RecordedEvent(steps.size.toLong + index,
        event), last, last) }, None).filter(_.sequence >= steps.size))

  private def lineage(player: PlayerId): LineageId =
    ready.game.current.players.find(_.player == player).get.lineage

  test("gains, draws, buries, peeks and reveals each post one line"):
    val drawn = ready.game.current.commonCards.worldDeck.take(2)
    val lines = posted(ActionRef.Travel,
      Gain.Favor(other, Suit.Arcane, 2), Gain.Secrets(actor, 1),
      Draw(actor, drawn, Location.Deck(CardDeck.World), Location.Hand(actor)),
      Bury(BuryableCard.Relic(relic), PositionedLocation(Location.Site(site))),
      Peek(other, relic, Location.Site(site)),
      Reveal(relic, Location.Site(site)))
    assert(lines.contains(s"${name(other)} gained 2 favor from the Arcane bank"),
      lines)
    assert(lines.contains("Gained 1 secret"), lines)
    // Both cards, counted by their backs: an observer sees no names.
    val kinds = drawn.map(LogWords.backOf)
    val backs = kinds.distinct.map { kind =>
      val count = kinds.count(_ == kind)
      if count == 1 then s"a $kind" else s"$count ${LogWords.plural(kind)}"
    }.mkString(" and ")
    assert(lines.contains(s"Drew $backs"), lines)
    assert(lines.contains("Buried a Relic"), lines)
    assert(lines.contains(s"${name(other)} peeked at a Relic"), lines)
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
      s"Moved 1 of ${name(other)}'s warbands to $at",
      s"Moved 1 bandit to $at",
      s"Moved 3 Imperial warbands to $at"))

  test("a vision discarded names its pile"):
    val vision = ready.game.current.commonCards.worldDeck.collectFirst {
      case id: VisionId => id }.get
    val lines = posted(ActionRef.Travel, Discard.Vision(vision,
      PositionedLocation(Location.Deck(CardDeck.World)), Region.Hinterland))
    assert(lines.contains("Discarded a Vision to the Hinterland discard"),
      lines)

  test("a won Raid tells what was taken, then what the defender lost"):
    val result = CampaignResult(actor, CampaignKind.Raid,
      CampaignDefender.Player(other), Vector.empty,
      Vector(CampaignRaidTarget.Pawn(other)), 2, Vector.empty, 2, 0, 0,
      Vector.empty, 1, attackerWins = true)
    val lines = posted(ActionRef.Campaign, RecordCampaignResult(result),
      Take(Piece.Card(relic), actor, Location.PlayArea(other),
        Location.PlayArea(actor)),
      Take(Piece.Banner(Banner.DarkestSecret), actor, Location.PlayArea(other),
        Location.PlayArea(actor)),
      Kill(Piece.Warbands(ForceKind.Exile(lineage(other)), 2),
        PositionedLocation(Location.PlayArea(other))),
      Burn.favor(1, PositionedLocation(Location.PlayArea(other))),
      Move(Piece.Card(relic), PositionedLocation(Location.PlayArea(other)),
        PositionedLocation(Location.SetAsideRelics)))
    assert(lines.exists(line => line.startsWith("Took ") &&
      line.endsWith(s" and Darkest Secret from ${name(other)}")), lines)
    assert(lines.contains(s"${name(other)} lost 2 warbands, burned 1 favor " +
      "and set aside a Relic"), lines)

  test("a defeated attacker's losses have no subject"):
    val result = CampaignResult(actor, CampaignKind.Conquest,
      CampaignDefender.Bandits, Vector(site), Vector.empty, 3, Vector.empty,
      1, 0, 0, Vector.empty, 4, attackerWins = false)
    val lines = posted(ActionRef.Campaign, RecordCampaignResult(result),
      Kill(Piece.Warbands(ForceKind.Exile(lineage(actor)), 2),
        PositionedLocation(Location.PlayArea(actor))))
    assert(lines.contains("The bandits win!"), lines)
    assert(lines.contains("Lost 2 warbands"), lines)

  test("every face of both dice has its rulebook name"):
    assertEquals(LogWords.dice(Vector(AttackDieFace.HollowSword,
      AttackDieFace.OneSword, AttackDieFace.TwoSwordsSkull)).map(_.text),
      Some("hollow sword, sword, two swords and a skull"))
    assertEquals(LogWords.dice(Vector(DefenseDieFace.Blank,
      DefenseDieFace.OneShield, DefenseDieFace.TwoShields,
      DefenseDieFace.Doubler)).map(_.faces), Some(Vector("blank", "one-shield",
      "two-shields", "doubler")))
    assertEquals(LogWords.dice(Vector.empty), None)
    assertEquals(LogWords.dice(Vector(AttackDieFace.OneSword,
      DefenseDieFace.Blank)), None)

  test("a chosen option of every kind reads as its name, never its key"):
    val words = new LogWords(catalog, presentation)
    val choices = new ChoiceWords(words,
      new WalkerDecisionProjector(catalog, presentation))
    def said(ref: DecisionOptionRef): String =
      choices.option(ref, ready, ready, None).map(_.text).mkString
    val denizen = ready.game.current.commonCards.worldDeck.collectFirst {
      case id: DenizenId => id }.get
    assertEquals(said(DecisionOptionRef.Player(other)), name(other))
    assertEquals(said(DecisionOptionRef.Site(site)), presentation.siteLabel(site))
    assertEquals(said(DecisionOptionRef.Denizen(denizen)), "a Denizen")
    assertEquals(said(DecisionOptionRef.Relic(relic)), "a Relic")
    assertEquals(said(DecisionOptionRef.RelicSlot(other, 0)),
      s"${name(other)}'s facedown relic (slot 1)")
    assertEquals(said(DecisionOptionRef.AdviserSlot(other, 0)),
      s"${name(other)}'s facedown adviser (slot 1)")
    assertEquals(said(DecisionOptionRef.AdviserSlot(other, 9)),
      s"${name(other)}'s adviser (slot 10)")
    assertEquals(said(DecisionOptionRef.Banner(Banner.PeoplesFavor)),
      "People's Favor")
    assertEquals(said(DecisionOptionRef.Deck(CardDeck.World)), "World")
    assertEquals(said(DecisionOptionRef.FavorBank(Suit.Order)),
      "the Order bank")
    // Nothing is parked, so a button falls back to its key's label.
    assertEquals(said(DecisionOptionRef.Button("search:world")), "World")
    assert(said(DecisionOptionRef.Vision(ready.game.current.commonCards
      .worldDeck.collectFirst { case id: VisionId => id }.get)) == "a Vision")
