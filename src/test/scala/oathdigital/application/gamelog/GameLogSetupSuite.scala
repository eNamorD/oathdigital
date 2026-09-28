package oathdigital.application.gamelog

import oathdigital.application.{GameApplicationService,
  InMemoryEventStreamRepository}
import oathdigital.gameplay.setup.{FirstGameSetupFixture, SetupProcedure}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Answers, Situation}
import LogScripts._

class GameLogSetupSuite extends munit.FunSuite:
  private def setupLines(script: Script, viewer: Option[PlayerId])
      : Vector[LogEntry] = format(script, viewer)
    .takeWhile(entry => text(entry) != "Round 1").filter(_.depth == 1)

  test("every player places a pawn and keeps an adviser, in turn order"):
    val script = woken
    val lines = texts(setupLines(script, None))
    assertEquals(lines.count(_.contains(" placed pawn at ")),
      script.players.size, lines)
    assertEquals(lines.count(_.contains(" kept ")), script.players.size, lines)
    // Turn order runs from the first player through the seats.
    val ready = script.history.steps.last.after match
      case OathState.Ready(value) => value
      case other => fail(s"expected a ready game, got $other")
    val seats = ready.game.current.players.map(_.player)
    val first = seats.indexOf(ready.setup.firstPlayer)
    val order = (seats.drop(first) ++ seats.take(first)).map(_.value.capitalize)
    assertEquals(lines.filter(_.contains(" placed pawn at "))
      .map(_.takeWhile(_ != ' ')), order)
    assertEquals(lines.filter(_.contains(" kept ")).map(_.takeWhile(_ != ' ')),
      order)

  test("a kept adviser is named to its owner and read by its back by others"):
    val script = woken
    val mine = setupLines(script, Some(script.actor))
      .filter(entry => text(entry).contains(" kept "))
    val own = mine.find(_.spans.head == LogSpan.Player(script.actor.value,
      name(script.actor))).get
    assert(own.spans.exists(_.isInstanceOf[LogSpan.Card]), own.spans)
    mine.filterNot(_ == own).foreach { entry =>
      assert("^.+ kept a (Denizen|Vision)$".r.matches(text(entry)), text(entry))
    }

  test("a card revealed in Reveal Cards is named to every viewer"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository,
      campaignDicePort = steadyDice)
    val revealAll: Answers =
      case park if park.decisionId ==
          SetupProcedure.revealDecisionId(park.awaiting) =>
        val DecisionQuery.ChooseMany(_, _, options, _) = park.decide.query: @unchecked
        DecisionAnswer.ChooseManyAnswer(options.map(_.ref))
    val woken = Situation.wake(Situation.journaled(service, catalog,
      repository, "revealed").withAnswers(
        Situation.pawnsAt(FirstGameSetupFixture.sites) orElse revealAll))
    val script = Script("revealed", service, active(woken))
    val revealed = woken.ready.game.current.players.map(player =>
      player.advisers.count(_ match
        case d: DenizenState => d.orientation == Orientation.FaceUp
        case _ => false)).sum
    assert(revealed > 0)
    val public = texts(setupLines(script, None)).filter(_.contains(" revealed "))
    assertEquals(public.size, revealed, public)
    public.foreach(line =>
      assert(!"^.+ revealed a (Denizen|Vision)$".r.matches(line), line))
    script.players.foreach { viewer =>
      assertEquals(texts(setupLines(script, Some(viewer)))
        .filter(_.contains(" revealed ")), public, viewer)
    }
