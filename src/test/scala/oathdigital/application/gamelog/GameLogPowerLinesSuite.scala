package oathdigital.application.gamelog

import oathdigital.application.ParkedServiceFixture
import oathdigital.engine.ReplayStep
import oathdigital.gameplay.actions.{PlacementRules, RuleNotes}
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{PowerNoted, WalkerCompleted, WalkerParked,
  WalkerStepRecorded}
import oathdigital.model._
import LogScripts._

/** Lines powers write about themselves (power log lines design, section 2).
  * The notes are journaled into real scripts, as Silver Tongue's, whose
  * wording this suite supplies. */
class GameLogPowerLinesSuite extends munit.FunSuite:
  private type Steps = Vector[ReplayStep[OathState, OathEvent]]
  private val power = SilverTongue.id
  private val card = PowerSourceRef.Card(ParkedServiceFixture.silverTongueCard)
  private val said = NoteKey("said", Vector(NotePart.Arg(0),
    NotePart.Text(" said "), NotePart.Arg(1), NotePart.Text(".")))
  private val took = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1), NotePart.Text(".")))
  private val counted = NoteKey("counted", Vector(NotePart.Arg(0),
    NotePart.Text(" lost "), NotePart.Arg(1), NotePart.Text(" "),
    NotePart.Plural(1, "warband", "warbands"), NotePart.Text(", then "),
    NotePart.Arg(2), NotePart.Text(".")))
  private val none = NoteKey("used.none", Vector(NotePart.Text("Nothing to take.")))
  private val noting = new GameLogFormatter(catalog, presentation,
    NoteWordings.default(catalog) ++
      NoteWordings.of(power, Vector(said, took, counted, none)))

  private def entries(steps: Steps, viewer: Option[PlayerId]) =
    noting.format(steps, viewer).filter(_.depth == 1)
  private def lines(steps: Steps, viewer: Option[PlayerId] = None) =
    texts(entries(steps, viewer))
  private def ours(steps: Steps, viewer: Option[PlayerId] = None) =
    entries(steps, viewer).filter(entry => text(entry).startsWith("Silver Tongue: "))

  /** Silver Tongue's take: the step that moves favor out of a bank. */
  private def take(steps: Steps): Int = steps.indexWhere(_.event.event match
    case step: WalkerStepRecorded => step.ops.exists {
      case Move(Piece.Favor(_), PositionedLocation(Location.FavorBank(_), _),
          _, _) => true
      case _ => false
    }
    case _ => false)

  private def saying(actor: PlayerId, arg: NoteArg): PowerNoted =
    PowerNoted(power, said(card, NoteArg.Player(actor), arg), covers = false)

  private def closes(event: OathEvent): Boolean = event match
    case _: WalkerParked | _: WalkerCompleted => true
    case _ => false

  private def assertPrefixStable(steps: Steps): Unit =
    val whole = noting.format(steps, None)
    steps.indices.filter(index => closes(steps(index).event.event))
      .map(_ + 1).foreach { end =>
        assertEquals(noting.format(steps.take(end), None),
          whole.takeWhile(_.sequence < end), s"at $end")
      }

  test("a note reads as its source, a colon and its sentence"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor)))
    val entry = ours(noted).head
    assertEquals(text(entry), s"Silver Tongue: ${name(script.actor)} said 2 favor.")
    assertEquals(entry.kind, LogKind.Trigger)

  test("a banner source reads as its banner"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val noted = inserted(steps, take(steps), PowerNoted(power,
      said(PowerSourceRef.Banner(Banner.DarkestSecret),
        NoteArg.Player(script.actor), NoteArg.Amount(1, NoteUnit.Secret)),
      covers = false))
    assert(lines(noted).contains(
      s"Darkest Secret: ${name(script.actor)} said 1 secret."), lines(noted))

  test("a phase power's used note replaces Used {card} as the action line"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    assert(lines(steps).contains("Used Silver Tongue"), lines(steps))
    val noted = inserted(steps, take(steps), PowerNoted(power, took(card,
      NoteArg.Player(script.actor), NoteArg.Amount(1, NoteUnit.Favor)),
      covers = false))
    assert(!lines(noted).exists(_.startsWith("Used ")), lines(noted))
    val entry = ours(noted).head
    assertEquals(text(entry), s"Silver Tongue: ${name(script.actor)} took 1 favor.")
    assertEquals(entry.kind, LogKind.Action)

  test("a covering note drops the generic lines of the step before it, but no decision line"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val before = lines(steps)
    assert(before.exists(_.startsWith("Gained 1 favor from the ")), before)
    val chose = before.filter(_.startsWith("Chose "))
    assert(chose.nonEmpty, before)
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Amount(1, NoteUnit.Favor)).copy(covers = true))
    val after = lines(noted)
    assert(!after.exists(_.startsWith("Gained 1 favor from the ")), after)
    assertEquals(after.filter(_.startsWith("Chose ")), chose)
    assertPrefixStable(noted)

  test("a note identical to an earlier one in the action posts nothing"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val twice = saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor))
    val noted = inserted(steps, take(steps), twice, twice,
      saying(script.actor, NoteArg.Amount(3, NoteUnit.Favor)))
    assertEquals(ours(noted).map(text), Vector(
      s"Silver Tongue: ${name(script.actor)} said 2 favor.",
      s"Silver Tongue: ${name(script.actor)} said 3 favor."))

  test("plurals follow their amount"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    def counting(count: Int) = PowerNoted(power, counted(card,
      NoteArg.Player(script.actor), NoteArg.Number(count),
      NoteArg.Amount(count, NoteUnit.Warband)), covers = false)
    val noted = inserted(steps, take(steps), counting(1), counting(2))
    assertEquals(ours(noted).map(text), Vector(
      s"Silver Tongue: ${name(script.actor)} lost 1 warband, then 1 warband.",
      s"Silver Tongue: ${name(script.actor)} lost 2 warbands, then 2 warbands."))

  test("a note no power words posts nothing"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val unknown = PowerNoted(PowerId("test.unknown"),
      PowerNote(card, "said", Vector.empty), covers = false)
    assertEquals(lines(inserted(steps, take(steps), unknown)), lines(steps))

  test("a card its viewer may not identify is not named to them"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val (owner, hidden) = last.game.current.players
      .filter(_.player != script.actor).flatMap(held => held.advisers.collect {
        case DenizenState(id, Orientation.FaceDown, _) => held.player -> (id: CardId)
        case VisionState(id, Orientation.FaceDown) => held.player -> (id: CardId)
      }).head
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Card(hidden)))
    def named(viewer: PlayerId) = ours(noted, Some(viewer)).head.spans.collect {
      case shown: LogSpan.Card => shown.id }
    assert(!named(script.actor).contains(hidden.value), named(script.actor))
    assert(named(owner).contains(hidden.value), named(owner))

  test("a card list reads as one phrase, and a banner by its name"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val hidden: CardId = last.game.current.players
      .filter(_.player != script.actor).flatMap(_.advisers.collect {
        case DenizenState(id, Orientation.FaceDown, _) => id: CardId
        case VisionState(id, Orientation.FaceDown) => id: CardId
      }).head
    val back = hidden match
      case _: VisionId => "a Vision"
      case _ => "a Denizen"
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(Vector(ParkedServiceFixture.silverTongueCard, hidden))))
    assertEquals(text(ours(listed, Some(script.actor)).head),
      s"Silver Tongue: ${name(script.actor)} said Silver Tongue and $back.")
    val banner = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Banner(Banner.DarkestSecret)))
    assertEquals(text(ours(banner).head),
      s"Silver Tongue: ${name(script.actor)} said Darkest Secret.")

  test("more than five cards read as a card list, each its face or its back"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val deck = last.game.current.commonCards.worldDeck.take(5)
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(ParkedServiceFixture.silverTongueCard +: deck)))
    val entry = ours(listed, Some(script.actor)).head
    val lists = entry.spans.collect { case cards: LogSpan.Cards => cards }
    assertEquals(lists.map(_.text), Vector("6 cards"))
    assertEquals(lists.head.cards.map(card => card.hidden -> card.cardId),
      (false -> ParkedServiceFixture.silverTongueCard.value) +:
        deck.map(_ => true -> "hidden"))
    assertEquals(text(entry),
      s"Silver Tongue: ${name(script.actor)} said 6 cards.")

  test("more than five cards its viewer may identify none of read as a count"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(last.game.current.commonCards.worldDeck.take(6))))
    val entry = ours(listed, Some(script.actor)).head
    assert(!entry.spans.exists(_.isInstanceOf[LogSpan.Cards]),
      entry.spans.toString)
    assertEquals(text(entry),
      s"Silver Tongue: ${name(script.actor)} said 6 cards.")

  test("a pile reads as its name, the template supplying the article"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val cradle = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Pile(SearchSource.RegionalDiscard(Region.Cradle))))
    assertEquals(text(ours(cradle).head),
      s"Silver Tongue: ${name(script.actor)} said Cradle discard pile.")
    val deck = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Pile(SearchSource.WorldDeck)))
    assertEquals(text(ours(deck).head),
      s"Silver Tongue: ${name(script.actor)} said world deck.")

  test("a note's source its viewer may not identify reads as its back"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val last = steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val (owner, hidden) = last.game.current.players
      .filter(_.player != script.actor).flatMap(held => held.advisers.collect {
        case DenizenState(id, Orientation.FaceDown, _) => held.player -> (id: CardId)
      }).head
    val noted = inserted(steps, take(steps), PowerNoted(power,
      said(PowerSourceRef.Card(hidden), NoteArg.Player(script.actor),
        NoteArg.Amount(2, NoteUnit.Favor)), covers = false))
    def line(viewer: PlayerId) = entries(noted, Some(viewer))
      .find(entry => text(entry).endsWith(" said 2 favor.")).get
    assertEquals(text(line(script.actor)),
      s"a Denizen: ${name(script.actor)} said 2 favor.")
    assert(line(owner).spans.head == LogSpan.Card(hidden.value,
      presentation.cardLabel(last, hidden)), line(owner).spans)

  test("the same note in another action posts again"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val note = saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor))
    val first = steps.indexWhere(_.event.event.isInstanceOf[WalkerStepRecorded])
    assert(first < take(steps))
    val noted = inserted(inserted(steps, take(steps), note), first, note)
    // The card is not public during setup, so the first line shows its back.
    assertEquals(lines(noted).count(_.endsWith(s"${name(script.actor)} said 2 favor.")),
      2, lines(noted))

  test("a note waits for its action's start line"):
    val script = raid
    val steps = withoutNotes(script.history.steps)
    val opened = steps.indexWhere(_.event.event match
      case parked: WalkerParked => parked.procedure == ActionRef.Campaign
      case _ => false)
    val noted = inserted(steps, opened,
      saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor)))
    val all = lines(noted)
    val start = all.indexWhere(_.startsWith("Started Campaign"))
    assert(start >= 0, all)
    // Silver Tongue is not at hand in this game, so the log shows its back.
    assertEquals(all.indexWhere(_.endsWith(" said 2 favor.")), start + 1, all)
    assertPrefixStable(noted)

  test("Gambling Hall writes its roll and its gain in place of the generic lines"):
    val script = gamblingHall
    val all = texts(format(script, None).filter(_.depth == 1))
    val actor = name(script.actor)
    val rolled = all.filter(_.startsWith(s"Gambling Hall: $actor rolled "))
    assertEquals(rolled.size, 1, all)
    assert(rolled.head.endsWith(", Total: 8"), rolled.head)
    val gained = all.filter(_.startsWith(s"Gambling Hall: $actor gained "))
    assertEquals(gained.size, 1, all)
    assert(gained.head.endsWith(" bank."), gained.head)
    // The favor arranged before the power posts its own line; only the
    // power's lines are held to this.
    val used = all.dropWhile(!_.startsWith("Gambling Hall: "))
    assert(!used.exists(line => line.startsWith("Used ") ||
      line.startsWith("Rolled ") || line.startsWith("Gained ")), all)
    val entry = format(script, None).find(entry =>
      text(entry).startsWith(s"Gambling Hall: $actor rolled ")).get
    assertEquals(entry.kind, LogKind.Action)

  test("a variant of a phase power's used note also replaces Used {card}"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val noted = inserted(steps, take(steps),
      PowerNoted(power, none(card), covers = false))
    assert(!lines(noted).exists(_.startsWith("Used ")), lines(noted))
    val entry = ours(noted).head
    assertEquals(text(entry), "Silver Tongue: Nothing to take.")
    assertEquals(entry.kind, LogKind.Action)

  test("a card is named to a viewer who identified it at any state its line reads"):
    val script = usePower
    val last = script.history.steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val (owner, hidden) = last.game.current.players
      .filter(_.player != script.actor).flatMap(held => held.advisers.collect {
        case DenizenState(id, Orientation.FaceDown, _) => held.player -> id
      }).head
    val shown = last.updateCurrent(current => current.copy(players =
      current.players.map(held => if held.player != owner then held
        else held.copy(advisers = held.advisers.map {
          case DenizenState(`hidden`, _, tokens) =>
            DenizenState(hidden, Orientation.FaceUp, tokens)
          case other => other
        }))))
    val words = new LogWords(catalog, presentation)
    val viewer = Some(script.actor)
    assertEquals(words.seen(hidden, Vector(last), viewer), CardWord.Back("Denizen"))
    assertEquals(words.seen(hidden, Vector(shown, last), viewer),
      CardWord.Named(LogSpan.Card(hidden.value,
        presentation.cardLabel(last, hidden))))

  test("Wolves writes its kill as the action line, after the choice it answers"):
    val script = wolves
    val entries = format(script, None).filter(_.depth == 1)
    val all = texts(entries)
    val kill = all.indexWhere(_.startsWith("Wolves: Killed 1 "))
    assert(kill > 0, all)
    assert(all(kill - 1).startsWith("Chose "), all)
    assert(all(kill).endsWith(" warband."), all(kill))
    assert(!all.exists(_.startsWith("Used ")), all)
    assertEquals(entries(kill).kind, LogKind.Action)

  test("a game rule's line is worded from RuleNotes"):
    assertEquals(NoteWordings.default(catalog).template(RuleNotes.homelandDiscard,
      PlacementRules.discardFirst.name), Some(PlacementRules.discardFirst.template))

  test("a setup note posts under the Setup headline, among the setup lines"):
    val script = usePower
    val steps = withoutNotes(script.history.steps)
    val placed = steps.indexWhere(_.event.event match
      case step: WalkerStepRecorded => step.ops.exists {
        case Move(Piece.Pawn(_), _, _, _) => true
        case _ => false
      }
      case _ => false)
    val noted = inserted(steps, placed,
      saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor)))
    val all = texts(noting.format(noted, None))
    val line = all.indexWhere(_.endsWith(s"${name(script.actor)} said 2 favor."))
    assert(all.indexOf("Setup") < line && line < all.indexOf("Round 1"), all)
    assert(all.take(line).exists(_.contains(" placed pawn at ")), all)
    assertPrefixStable(noted)
