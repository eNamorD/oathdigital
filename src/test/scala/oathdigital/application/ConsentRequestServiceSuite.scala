package oathdigital.application

import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.powers.travel.{NarrowPassSite, TravelFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathEvent.{CommandAccepted, ConsentAnswered,
  ConsentPreferenceSet, ConsentRequestWithdrawn, ConsentRequested}
import oathdigital.testkit.{Look, Outcomes, PlacementPicks}
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.{accepted, present}

/** Asking for consent and answering through the service (consent design,
  * user story, rulings 10 to 14, "Commands"). */
class ConsentRequestServiceSuite extends munit.FunSuite:
  import ConsentFixture._
  private val pass = NarrowPassSite.pass
  private val request = ConsentRequest(p1,
    TravelProcedure.destinationDecisionId, dunes,
    Vector(ConsentAsk(p2, Vector(pass))))
  private val powers = WalkerPowers.selected(
    WalkerPowerCatalog.default(catalog), Vector.empty)
  private def rejected(violation: OathViolation) =
    Left(GameApplicationError.CommandRejected(violation))

  /** p1 starts a Travel and asks about Dunes. */
  private def asking(service: GameApplicationService)(using munit.Location)
      : GameAccepted =
    val started = startTravel(service)
    accepted(service.handle(gameId, started.nextSequence, requestDunes))

  private def answering(service: GameApplicationService, at: GameAccepted,
      choice: ConsentChoice)(using munit.Location): GameAccepted =
    accepted(service.handle(gameId, at.nextSequence,
      GameCommand.AnswerConsent(p2, choice)))

  private def pawnOfP1(at: GameAccepted)(using munit.Location)
      : Option[SiteId] =
    Look(Outcomes.ready(at.state)).player(p1).pawnSite

  private def parked(at: GameAccepted)(using munit.Location): Boolean =
    Outcomes.ready(at.state).game.current.walkerPending.nonEmpty

  private def dunesOffered(at: GameAccepted)(using munit.Location)
      : DecisionOption =
    present(PlacementPicks.parkedOptions(at.state, powers)
      .find(_.ref == dunes), "Dunes among the destinations")

  private def askable(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, _: OptionReason.Askable) => true
    case _ => false

  private def refusedPlainly(option: DecisionOption): Boolean = option match
    case DecisionOption.Unavailable(_, OptionReason.Noted(_, note)) =>
      note.key == "no-travel"
    case _ => false

  test("a request waits on the Pass's ruler, and Yes travels there for the " +
      "requester, as the requester's own undoable step"):
    val (service, repository) = ConsentFixture.service()
    val asked = asking(service)
    assertEquals(asked.events, Vector(ConsentRequested(request)))
    assertEquals(consent(asked).pending, Some(request))
    val yes = answering(service, asked, ConsentChoice.Yes)
    assertEquals(yes.events.head, ConsentAnswered(p2, granted = true))
    assert(!yes.events.exists(_.isInstanceOf[CommandAccepted]))
    assertEquals(pawnOfP1(yes), Some(TravelFixture.provincePlains))
    // The move is p1's batch: it opens right after the answer.
    assertEquals(yes.undo.map(step => (step.issuer, step.from)),
      Some((p1, asked.nextSequence + 1L)))
    val reloaded = ParkedServiceFixture.replayingSecond(service, repository)
    assertEquals(reloaded.load(gameId).map(_.map(game =>
      (game.state, game.undo))), Right(Some((yes.state, yes.undo))))

  test("No leaves Dunes greyed out with its ordinary reason, and it cannot " +
      "be asked about again"):
    val (service, _) = ConsentFixture.service()
    val no = answering(service, asking(service), ConsentChoice.No)
    assertEquals(no.events, Vector(ConsentAnswered(p2, granted = false)))
    assert(parked(no))
    assert(refusedPlainly(dunesOffered(no)))
    assertEquals(service.handle(gameId, no.nextSequence, requestDunes),
      rejected(OathViolation.ConsentUnavailable(
        s"${dunes.wireId} is not an option to ask consent for")))

  test("Always Allow grants and sets the ruler's preference"):
    val (service, _) = ConsentFixture.service()
    val always = answering(service, asking(service), ConsentChoice.Always)
    assertEquals(always.events.take(2), Vector(
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Always),
      ConsentAnswered(p2, granted = true)))
    assertEquals(consent(always).preference(p2, pass, p1),
      Some(ConsentPreference.Always))
    assertEquals(pawnOfP1(always), Some(TravelFixture.provincePlains))

  test("Never Allow refuses and sets the ruler's preference"):
    val (service, _) = ConsentFixture.service()
    val never = answering(service, asking(service), ConsentChoice.Never)
    assertEquals(never.events, Vector(
      ConsentPreferenceSet(p2, pass, p1, ConsentSetting.Never),
      ConsentAnswered(p2, granted = false)))
    assert(parked(never))

  test("a ruler's Always answers a request at once"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Always)))
    val started = startTravel(service, set.nextSequence)
    val asked = accepted(service.handle(gameId, started.nextSequence,
      requestDunes))
    assertEquals(asked.events.take(2), Vector(ConsentRequested(request),
      ConsentAnswered(p2, granted = true)))
    assertEquals(pawnOfP1(asked), Some(TravelFixture.provincePlains))

  test("a ruler's Never refuses a request at once"):
    val (service, _) = ConsentFixture.service()
    val set = accepted(service.handle(gameId, 0L,
      setPreference(ConsentSetting.Never)))
    val started = startTravel(service, set.nextSequence)
    val asked = accepted(service.handle(gameId, started.nextSequence,
      requestDunes))
    assertEquals(asked.events, Vector(ConsentRequested(request),
      ConsentAnswered(p2, granted = false)))
    assert(parked(asked))

  test("after a grant the requester may undo the move, and pick Dunes " +
      "again without asking"):
    val (service, _) = ConsentFixture.service()
    val yes = answering(service, asking(service), ConsentChoice.Yes)
    val undone = accepted(service.handle(gameId, yes.nextSequence,
      GameCommand.Undo(p1)))
    assert(parked(undone))
    assert(dunesOffered(undone).available)
    val picked = accepted(service.handle(gameId, undone.nextSequence,
      ParkedServiceFixture.pickTravelDestination(p1,
        TravelFixture.provincePlains)))
    assertEquals(pawnOfP1(picked), Some(TravelFixture.provincePlains))
    assert(!picked.events.exists(_.isInstanceOf[ConsentEvent]))

  test("after a refusal the requester may undo the Travel, and Dunes stays " +
      "refused this turn"):
    val (service, _) = ConsentFixture.service()
    val no = answering(service, asking(service), ConsentChoice.No)
    val undone = accepted(service.handle(gameId, no.nextSequence,
      GameCommand.Undo(p1)))
    assert(!parked(undone))
    val again = startTravel(service, undone.nextSequence)
    assert(refusedPlainly(dunesOffered(again)))

  test("Undo while the request waits withdraws it, and nothing is " +
      "remembered"):
    val (service, _) = ConsentFixture.service()
    val asked = asking(service)
    val withdrawn = accepted(service.handle(gameId, asked.nextSequence,
      GameCommand.Undo(p1)))
    assertEquals(withdrawn.events, Vector(ConsentRequestWithdrawn(p1)))
    assertEquals(consent(withdrawn), ConsentState())
    assert(parked(withdrawn))
    assert(askable(dunesOffered(withdrawn)))

  test("while a request waits, only its answers, its withdrawal and " +
      "preferences run"):
    val (service, _) = ConsentFixture.service()
    val asked = asking(service)
    val at = asked.nextSequence
    assertEquals(service.handle(gameId, at, ParkedServiceFixture
      .pickTravelDestination(p1, TravelFixture.coast)),
      rejected(OathViolation.ConsentPending))
    assertEquals(service.handle(gameId, at, requestDunes),
      rejected(OathViolation.ConsentPending))
    assertEquals(service.handle(gameId, at, GameCommand.AnswerConsent(p3,
      ConsentChoice.Yes)), rejected(OathViolation.ConsentUnavailable(
      "p3 has not been asked for consent")))
    assertEquals(service.handle(gameId, at, GameCommand.Undo(p3)),
      rejected(OathViolation.ConsentPending))
    assertEquals(service.handle(gameId, at, GameCommand.Redo(p1)),
      rejected(OathViolation.ConsentPending))
    // A preference changed while prompted leaves the prompt open (ruling 19).
    val set = accepted(service.handle(gameId, at,
      setPreference(ConsentSetting.Always)))
    assertEquals(consent(set).pending, Some(request))

  test("with no request waiting, an answer is refused"):
    val (service, _) = ConsentFixture.service()
    val started = startTravel(service)
    assertEquals(service.handle(gameId, started.nextSequence,
      GameCommand.AnswerConsent(p2, ConsentChoice.Yes)),
      rejected(OathViolation.ConsentUnavailable(
        "no consent request is waiting")))
