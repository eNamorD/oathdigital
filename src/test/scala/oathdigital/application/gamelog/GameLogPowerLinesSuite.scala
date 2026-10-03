package oathdigital.application.gamelog

import oathdigital.engine.ReplayStep
import oathdigital.gameplay.actions.{PlacementRules, RuleNotes, VisionRules}
import oathdigital.gameplay.powers.rest.SilverTongue
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ChoicePayload, PowerNoted, WalkerCompleted,
  WalkerParked, WalkerStepRecorded}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p2
import LogScripts._

/** Lines powers write about themselves (power log lines design, section 2).
  * The notes are journaled into real scripts, as Silver Tongue's, whose
  * wording this suite supplies. */
class GameLogPowerLinesSuite extends munit.FunSuite:
  private val silverTongueCard = CatalogNames.denizen("Silver Tongue")
  private type Steps = Vector[ReplayStep[OathState, OathEvent]]
  private val power = SilverTongue.id
  private val card = PowerSourceRef.Card(silverTongueCard)
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

  /** The first of `posted`, the Silver Tongue lines a note left; fails when
    * there are none. */
  private def firstSilverTongueLine(posted: Vector[LogEntry])(
      using munit.Location): LogEntry =
    posted.headOption.getOrElse(fail("no Silver Tongue line was posted"))

  /** The Silver Tongue script, shared by the tests that journal notes into
    * its run. */
  private lazy val silverTongueScript = silverTongue

  /** That script's journal without its own notes. */
  private lazy val silverTongueSteps =
    withoutNotes(silverTongueScript.history.steps)

  // The Silver Tongue park gives p2 Birdsong facedown, and no other seat
  // holds a facedown adviser.
  private val hiddenOwner = p2
  private val hiddenAdviser = CatalogNames.denizen("Birdsong")

  /** Fails unless `hiddenOwner` holds `hiddenAdviser` facedown in `ready`. */
  private def assertOwnerHoldsHiddenFacedown(ready: ReadyGame)(
      using munit.Location): Unit =
    val advisers = Look(ready).player(hiddenOwner).advisers
    assert(advisers.exists {
      case DenizenState(`hiddenAdviser`, Orientation.FaceDown, _) => true
      case _ => false
    }, s"$hiddenOwner should hold ${hiddenAdviser.value} facedown; " +
      s"advisers: $advisers")

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
    val script = silverTongueScript
    val steps = silverTongueSteps
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor)))
    val entry = firstSilverTongueLine(ours(noted))
    assertEquals(text(entry),
      s"Silver Tongue: ${playerLabel(script.actor)} said 2 favor.")
    assertEquals(entry.kind, LogKind.Trigger)

  test("a banner source reads as its banner"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val noted = inserted(steps, take(steps), PowerNoted(power,
      said(PowerSourceRef.Banner(Banner.DarkestSecret),
        NoteArg.Player(script.actor), NoteArg.Amount(1, NoteUnit.Secret)),
      covers = false))
    val actor = playerLabel(script.actor)
    assert(lines(noted).contains(s"Darkest Secret: $actor said 1 secret."),
      lines(noted))

  test("a phase power's used note replaces Used {card} as the action line"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    assert(lines(steps).contains("Used Silver Tongue"), lines(steps))
    val noted = inserted(steps, take(steps), PowerNoted(power, took(card,
      NoteArg.Player(script.actor), NoteArg.Amount(1, NoteUnit.Favor)),
      covers = false))
    assert(!lines(noted).exists(_.startsWith("Used ")), lines(noted))
    val entry = firstSilverTongueLine(ours(noted))
    assertEquals(text(entry),
      s"Silver Tongue: ${playerLabel(script.actor)} took 1 favor.")
    assertEquals(entry.kind, LogKind.Action)

  test("a covering note drops the generic lines of the step before it, but no decision line"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val before = lines(steps)
    // Silver Tongue takes one favor from the bank it names.
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
    val script = silverTongueScript
    val steps = silverTongueSteps
    val twice = saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor))
    val noted = inserted(steps, take(steps), twice, twice,
      saying(script.actor, NoteArg.Amount(3, NoteUnit.Favor)))
    assertEquals(ours(noted).map(text), Vector(
      s"Silver Tongue: ${playerLabel(script.actor)} said 2 favor.",
      s"Silver Tongue: ${playerLabel(script.actor)} said 3 favor."))

  test("plurals follow their amount"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    def counting(count: Int) = PowerNoted(power, counted(card,
      NoteArg.Player(script.actor), NoteArg.Number(count),
      NoteArg.Amount(count, NoteUnit.Warband)), covers = false)
    val noted = inserted(steps, take(steps), counting(1), counting(2))
    val actor = playerLabel(script.actor)
    assertEquals(ours(noted).map(text), Vector(
      s"Silver Tongue: $actor lost 1 warband, then 1 warband.",
      s"Silver Tongue: $actor lost 2 warbands, then 2 warbands."))

  test("a note no power words posts nothing"):
    val steps = silverTongueSteps
    val unknown = PowerNoted(PowerId("test.unknown"),
      PowerNote(card, "said", Vector.empty), covers = false)
    assertEquals(lines(inserted(steps, take(steps), unknown)), lines(steps))

  test("a card its viewer may not identify is not named to them"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    assertOwnerHoldsHiddenFacedown(Outcomes.ready(steps.last.after))
    val noted = inserted(steps, take(steps),
      saying(script.actor, NoteArg.Card(hiddenAdviser)))
    def named(viewer: PlayerId) =
      firstSilverTongueLine(ours(noted, Some(viewer))).spans.collect {
      case shown: LogSpan.Card => shown.id }
    assert(!named(script.actor).contains(hiddenAdviser.value),
      named(script.actor))
    assert(named(hiddenOwner).contains(hiddenAdviser.value), named(hiddenOwner))

  test("a card list reads as one phrase, and a banner by its name"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    assertOwnerHoldsHiddenFacedown(Outcomes.ready(steps.last.after))
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(Vector(silverTongueCard, hiddenAdviser))))
    val actor = playerLabel(script.actor)
    assertEquals(text(firstSilverTongueLine(ours(listed, Some(script.actor)))),
      s"Silver Tongue: $actor said Silver Tongue and a Denizen.")
    val banner = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Banner(Banner.DarkestSecret)))
    assertEquals(text(firstSilverTongueLine(ours(banner))),
      s"Silver Tongue: ${playerLabel(script.actor)} said Darkest Secret.")

  test("more than five cards read as a card list, each its face or its back"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val last = Outcomes.ready(steps.last.after)
    val deck = last.game.current.commonCards.worldDeck.take(5)
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(silverTongueCard +: deck)))
    val entry = firstSilverTongueLine(ours(listed, Some(script.actor)))
    val lists = entry.spans.collect { case cards: LogSpan.Cards => cards }
    assertEquals(lists.map(_.text), Vector("6 cards"))
    assertEquals(lists.head.cards.map(card => card.hidden -> card.cardId),
      (false -> silverTongueCard.value) +:
        deck.map(_ => true -> "hidden"))
    assertEquals(text(entry),
      s"Silver Tongue: ${playerLabel(script.actor)} said 6 cards.")

  test("more than five cards its viewer may identify none of read as a count"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val last = Outcomes.ready(steps.last.after)
    val listed = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Cards(last.game.current.commonCards.worldDeck.take(6))))
    val entry = firstSilverTongueLine(ours(listed, Some(script.actor)))
    assert(!entry.spans.exists(_.isInstanceOf[LogSpan.Cards]),
      entry.spans.toString)
    assertEquals(text(entry),
      s"Silver Tongue: ${playerLabel(script.actor)} said 6 cards.")

  test("pressing Done on an Inspect writes no Chose line"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    def pressed(ref: DecisionOptionRef) = inserted(steps, take(steps),
      WalkerStepRecorded("inspect", ChoicePayload("power.scryer.inspect",
        DecisionAnswer.ChooseOneAnswer(ref), script.actor), Vector.empty,
        Vector.empty))
    def chose(lines: Vector[String]) = lines.count(_.startsWith("Chose "))
    val before = chose(lines(steps))
    assertEquals(chose(lines(pressed(DecisionOptionRef.Button("other")))),
      before + 1)
    assertEquals(chose(lines(pressed(DecisionQuery.Inspect.Done))), before)

  test("a pile reads as its name, the template supplying the article"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val cradle = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Pile(SearchSource.RegionalDiscard(Region.Cradle))))
    assertEquals(text(firstSilverTongueLine(ours(cradle))),
      s"Silver Tongue: ${playerLabel(script.actor)} said Cradle discard pile.")
    val deck = inserted(steps, take(steps), saying(script.actor,
      NoteArg.Pile(SearchSource.WorldDeck)))
    assertEquals(text(firstSilverTongueLine(ours(deck))),
      s"Silver Tongue: ${playerLabel(script.actor)} said world deck.")

  test("a note's source its viewer may not identify reads as its back"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val last = Outcomes.ready(steps.last.after)
    assertOwnerHoldsHiddenFacedown(last)
    val noted = inserted(steps, take(steps), PowerNoted(power,
      said(PowerSourceRef.Card(hiddenAdviser), NoteArg.Player(script.actor),
        NoteArg.Amount(2, NoteUnit.Favor)), covers = false))
    def line(viewer: PlayerId) =
      val posted = entries(noted, Some(viewer))
      posted.find(entry => text(entry).endsWith(" said 2 favor.")).getOrElse(
        fail(s"no 'said 2 favor.' line for $viewer in ${texts(posted)}"))
    assertEquals(text(line(script.actor)),
      s"a Denizen: ${playerLabel(script.actor)} said 2 favor.")
    assert(line(hiddenOwner).spans.head == LogSpan.Card(hiddenAdviser.value,
      presentation.cardLabel(last, hiddenAdviser)), line(hiddenOwner).spans)

  test("the same note in another action posts again"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val note = saying(script.actor, NoteArg.Amount(2, NoteUnit.Favor))
    val first = steps.indexWhere(_.event.event.isInstanceOf[WalkerStepRecorded])
    assert(first < take(steps))
    val noted = inserted(inserted(steps, take(steps), note), first, note)
    // The first note lands in Begin Rest's run, the second in Silver Tongue's.
    assertEquals(lines(noted).count(
      _.endsWith(s"${playerLabel(script.actor)} said 2 favor.")),
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
    val actor = playerLabel(script.actor)
    val rolled = all.filter(_.startsWith(s"Gambling Hall: $actor rolled "))
    assertEquals(rolled.size, 1, all)
    // Gambling Hall rolls 4 defense dice and steadyDice makes each two
    // shields: 4 x 2 = 8.
    assert(rolled.head.endsWith(", Total: 8"), rolled.head)
    val gained = all.filter(_.startsWith(s"Gambling Hall: $actor gained "))
    assertEquals(gained.size, 1, all)
    assert(gained.head.endsWith(" bank."), gained.head)
    // Only the power's own lines are held to this.
    val used = all.dropWhile(!_.startsWith("Gambling Hall: "))
    assert(!used.exists(line => line.startsWith("Used ") ||
      line.startsWith("Rolled ") || line.startsWith("Gained ")), all)
    val entry =
      entryStarting(format(script, None), s"Gambling Hall: $actor rolled ")
    assertEquals(entry.kind, LogKind.Action)

  test("a variant of a phase power's used note also replaces Used {card}"):
    val steps = silverTongueSteps
    val noted = inserted(steps, take(steps),
      PowerNoted(power, none(card), covers = false))
    assert(!lines(noted).exists(_.startsWith("Used ")), lines(noted))
    val entry = firstSilverTongueLine(ours(noted))
    assertEquals(text(entry), "Silver Tongue: Nothing to take.")
    assertEquals(entry.kind, LogKind.Action)

  test("a card is named to a viewer who identified it at any state its line reads"):
    val script = silverTongueScript
    val last = Outcomes.ready(script.history.steps.last.after)
    assertOwnerHoldsHiddenFacedown(last)
    // p2 holds Birdsong alone, so turning it faceup keeps its slot.
    val shown = Table.from(last)
      .adviser(hiddenOwner, hiddenAdviser, facedown = false).unchecked
    val words = new LogWords(catalog, presentation)
    val viewer = Some(script.actor)
    assertEquals(words.seen(hiddenAdviser, Vector(last), viewer),
      CardWord.Back("Denizen"))
    assertEquals(words.seen(hiddenAdviser, Vector(shown, last), viewer),
      CardWord.Named(LogSpan.Card(hiddenAdviser.value,
        presentation.cardLabel(last, hiddenAdviser))))

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
    val script = woken
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
    val line = all.indexWhere(
      _.endsWith(s"${playerLabel(script.actor)} said 2 favor."))
    assert(all.indexOf("Setup") < line && line < all.indexOf("Round 1"), all)
    assert(all.take(line).exists(_.contains(" placed pawn at ")), all)
    assertPrefixStable(noted)

  test("a power that names its notes' source reads under that name, not its site"):
    val script = silverTongueScript
    val steps = silverTongueSteps
    val site = CatalogNames.site("Ancient City")
    val noted = inserted(steps, take(steps), PowerNoted(power, took(
      PowerSourceRef.Site(site), NoteArg.Player(script.actor),
      NoteArg.Amount(1, NoteUnit.Favor)), covers = false))
    val named = new GameLogFormatter(catalog, presentation,
      NoteWordings.default(catalog) ++ NoteWordings.of(power, Vector(took),
        Some("River")))
    assert(texts(named.format(noted, None).filter(_.depth == 1)).contains(
      s"River: ${playerLabel(script.actor)} took 1 favor."))
    // Without a name the site is the source, as Narrow Pass's line has it.
    val siteName = presentation.siteLabel(site)
    assert(lines(noted).contains(
      s"$siteName: ${playerLabel(script.actor)} took 1 favor."),
      lines(noted))

  test("the River's notes are written under River; Narrow Pass keeps its site"):
    val wordings = NoteWordings.default(catalog)
    assertEquals(wordings.source(PowerId("site.riverbank.river")), Some("River"))
    assertEquals(wordings.source(PowerId("site.narrow-pass.pass")), None)

  test("Oracle writes its draw, then where the Vision went, named to its " +
      "drawer alone"):
    val script = oracle
    val actor = playerLabel(script.actor)
    val other = script.otherSeat
    // The oracle script stacks Vision of Sanctuary on the world deck.
    val vision = VisionRules.Sanctuary
    Vector(script.actor, other).foreach { viewer =>
      val shown = format(script, Some(viewer)).filter(_.depth == 1)
      val all = texts(shown)
      val drew = all.indexWhere(_.startsWith(s"Oracle: $actor drew "))
      assert(drew >= 0, all)
      assert(all(drew).endsWith(" from the world deck."), all(drew))
      assertEquals(shown(drew).kind, LogKind.Action)
      assert(all(drew + 1).startsWith("Played "), all)
      assert(all(drew + 1).endsWith(" as an adviser"), all)
      assert(!all.drop(drew).exists(_.startsWith("Used ")), all)
      val named = shown(drew).spans.exists {
        case LogSpan.Card(id, _) => id == vision.value
        case _ => false
      }
      assertEquals(named, viewer == script.actor, shown(drew).spans.toString)
    }

  test("Barbed Net writes its peek and its take as its action's lines, " +
      "naming the relic to its taker alone"):
    val script = barbedNet
    val actor = playerLabel(script.actor)
    val other = script.otherSeat
    // The barbed-net script's answer takes Dowsing Sticks.
    val taken = CatalogNames.relic("Dowsing Sticks")
    Vector(script.actor, other).foreach { viewer =>
      val shown = format(script, Some(viewer)).filter(_.depth == 1)
      val all = texts(shown)
      val peeked = all.indexWhere(
        _.startsWith(s"Barbed Net: $actor peeked at the relics at "))
      val took = all.indexWhere(_.startsWith(s"Barbed Net: $actor took "))
      assert(peeked >= 0 && took > peeked, all)
      assert(all(took).contains(" facedown from "), all(took))
      assertEquals(shown(peeked).kind, LogKind.Action)
      assertEquals(shown(took).kind, LogKind.Action)
      assert(!all.exists(_.startsWith("Used Barbed Net")), all)
      assertEquals(all.count(_.contains("peeked at")), 1, all)
      val named = shown(took).spans.exists {
        case LogSpan.Card(id, _) => id == taken.value
        case _ => false
      }
      assertEquals(named, viewer == script.actor, shown(took).spans.toString)
    }

  test("Hunger's forced step writes one trigger line that covers the burial, " +
      "naming the card to whoever may identify it"):
    val script = hunger
    val holder = playerLabel(script.actor)
    val other = script.otherSeat
    Vector(script.actor, other).foreach { viewer =>
      val shown = format(script, Some(viewer)).filter(_.depth == 1)
      val all = texts(shown)
      val at = all.indexWhere(line => line.startsWith(s"Hunger: $holder buried ") &&
        line.endsWith(s" from $holder's advisers."))
      assert(at >= 0, all)
      assertEquals(shown(at).kind, LogKind.Trigger)
      assert(!all.exists(_.startsWith("Buried ")), all)
      assert(!all.exists(_.startsWith("Used Hunger")), all)
      assertEquals(all(at).contains(" buried a Denizen from "),
        viewer != script.actor, all(at))
    }

  test("Family Heirloom tells its draw and choice in one line, naming the " +
      "relic to its drawer alone, with no Chose or Buried line"):
    Vector(heirloomKept -> "kept it.",
        heirloomReturned -> "put it on the bottom of the relic deck.")
      .foreach { case (script, end) =>
        val actor = playerLabel(script.actor)
        val other = script.otherSeat
        Vector(script.actor, other).foreach { viewer =>
          val shown = format(script, Some(viewer)).filter(_.depth == 1)
          val all = texts(shown)
          val at = all.indexWhere(line =>
            line.startsWith(s"Family Heirloom: $actor drew ") && line.endsWith(end))
          assert(at >= 0, all)
          assertEquals(shown(at).kind, LogKind.Trigger)
          assert(!all.exists(_.startsWith("Chose ")), all)
          assert(!all.exists(_.startsWith("Buried ")), all)
          // Dowsing Sticks tops the heirloom scripts' relic deck.
          val named = shown(at).spans.exists {
            case LogSpan.Card(id, _) =>
              id == CatalogNames.relic("Dowsing Sticks").value
            case _ => false
          }
          assertEquals(named, viewer == script.actor, shown(at).spans.toString)
        }
      }
