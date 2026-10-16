package oathdigital.gameplay.walker

import oathdigital.model._
import oathdigital.model.DecisionAnswer.{ChooseAmountAnswer, ChooseManyAnswer,
  ChooseOneAnswer}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.CatalogNames

/** The search's answers and narrowing, apart from the walk (global operation
  * restrictions design, "Lazy pruning"). A verdict here is a plain function,
  * so each test says exactly which answers survive.
  */
class WalkerSearchSuite extends munit.FunSuite:
  private val refused = OathViolation.InvalidEventOrder("refused")
  private val card = CatalogNames.denizen("Horse Archers")
  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef = DecisionOptionRef.Button(key)
  private def decide(query: DecisionQuery) = Decide("test.ask", p1, query)
  private def only(allowed: DecisionAnswer => Boolean)
      : DecisionAnswer => Either[OathViolation, Unit] =
    answer => Either.cond(allowed(answer), (), refused)
  private val abc = Vector(button("a"), button("b"), button("c"))
  private def passable(query: DecisionQuery) =
    decide(query).copy(passWhenEmpty = true)

  test("a choose-one keeps the options whose answer survives"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"),
      button("b"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ == ChooseOneAnswer(ref("b")))),
      Right(Some(decide(DecisionQuery.ChooseOne(Vector(button("b")))))))

  test("a required choose-one with no survivor is refused"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ => false)), Left(refused))

  test("a choose-many keeps an option that survives only beside another"):
    val asked = decide(DecisionQuery.ChooseMany(1, 2, abc, None))
    val pair = ChooseManyAnswer(Vector(ref("a"), ref("b")))
    assertEquals(WalkerSearch.narrow(asked, only(_ == pair)),
      Right(Some(decide(DecisionQuery.ChooseMany(1, 2,
        Vector(button("a"), button("b")), None)))))

  test("an optional choose-many with no survivor is not asked"):
    val asked = decide(DecisionQuery.ChooseMany(0, 1, abc, None))
    assertEquals(WalkerSearch.narrow(asked, only(_ => false)), Right(None))

  test("a choose-one passed when empty, with no survivor, is not asked, and " +
      "a search passes it"):
    val asked = passable(DecisionQuery.ChooseOne(Vector(button("a"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ => false)), Right(None))
    assertEquals(WalkerSearch.reach(asked, only(_ => false)),
      Right(WalkerSearch.Reach.Skipped))

  test("a choose-one passed when empty keeps its one survivor to ask"):
    val asked = passable(DecisionQuery.ChooseOne(Vector(button("a"),
      button("b"))))
    assertEquals(WalkerSearch.narrow(asked, only(_ == ChooseOneAnswer(ref("b")))),
      Right(Some(passable(DecisionQuery.ChooseOne(Vector(button("b")))))))

  test("a choose-amount narrows to the surviving range, and a gap leaves it " +
      "whole"):
    val asked = decide(DecisionQuery.ChooseAmount(0, 4, Some("How many?"),
      "Confirm"))
    assertEquals(WalkerSearch.narrow(asked, only {
      case ChooseAmountAnswer(value) => value == 1 || value == 2
      case _ => false
    }), Right(Some(decide(DecisionQuery.ChooseAmount(1, 2, Some("How many?"),
      "Confirm")))))
    assertEquals(WalkerSearch.narrow(asked, only {
      case ChooseAmountAnswer(value) => value != 2
      case _ => false
    }), Right(Some(asked)))

  test("a search tries the smallest selections first and stops at a survivor"):
    var tried = Vector.empty[DecisionAnswer]
    val asked = decide(DecisionQuery.ChooseMany(0, 3, abc, None))
    val survivor = ChooseManyAnswer(Vector(ref("b")))
    assertEquals(WalkerSearch.reach(asked, answer => {
      tried = tried :+ answer
      Either.cond(answer == survivor, (), refused)
    }), Right(WalkerSearch.Reach.Answerable))
    assertEquals(tried, Vector(ChooseManyAnswer(Vector.empty),
      ChooseManyAnswer(Vector(ref("a"))), survivor))

  test("a decision no answer survives cannot be reached"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"),
      button("b"))))
    assertEquals(WalkerSearch.reach(asked, only(_ => false)), Left(refused))

  test("an optional choose-many with no options is passed"):
    val asked = decide(DecisionQuery.ChooseMany(0, 1, Vector.empty, None))
    assertEquals(WalkerSearch.reach(asked, only(_ => false)),
      Right(WalkerSearch.Reach.Skipped))

  test("peeks, reveals, draws and cards turned faceup hide the outcome; " +
      "other operations do not"):
    assert(WalkerSearch.hides(Peek(p1, card, Location.PlayArea(p1))))
    assert(WalkerSearch.hides(Reveal(card, Location.PlayArea(p1))))
    assert(WalkerSearch.hides(Flip(card, Location.PlayArea(p1),
      Orientation.FaceUp)))
    assert(WalkerSearch.hides(Roll(PoolKey("test.roll"),
      DiceSpec(DiceKind.Attack), RollMode.Automatic)))
    assert(!WalkerSearch.hides(Flip(card, Location.PlayArea(p1),
      Orientation.FaceDown)))
    assert(!WalkerSearch.hides(GainSupply(p1, 1)))

  private val because = OptionReason.Written("Not now")

  test("an option the search drops is kept unavailable when its reason is known"):
    val asked = decide(DecisionQuery.ChooseMany(1, 2, abc, None))
    assertEquals(WalkerSearch.narrowed(asked, _ != ref("b"),
      found => Option.when(found == ref("b"))(because)).map(_.query),
      Some(DecisionQuery.ChooseMany(1, 2, Vector(button("a"),
        DecisionOption.Unavailable(button("b"), because), button("c")), None)))

  test("an option the search drops without a reason is removed"):
    val asked = decide(DecisionQuery.ChooseMany(1, 2, abc, None))
    assertEquals(WalkerSearch.narrowed(asked, _ != ref("b")).map(_.query),
      Some(DecisionQuery.ChooseMany(1, 2, Vector(button("a"), button("c")),
        None)))

  test("an optional choose-many left with only unavailable options is not asked"):
    val asked = decide(DecisionQuery.ChooseMany(0, 1, Vector(button("b")), None))
    assertEquals(WalkerSearch.narrowed(asked, _ => false, _ => Some(because)),
      None)

  test("an unavailable option is never tried as an answer"):
    val asked = decide(DecisionQuery.ChooseOne(Vector(button("a"),
      DecisionOption.Unavailable(button("b"), because))))
    assertEquals(WalkerSearch.narrow(asked, only(_ => true)),
      Right(Some(asked)))
    assertEquals(WalkerSearch.choosable(asked), Vector(ref("a")))

  /** A partition of `cards` into one favor and the rest secrets, as a
    * Forge splits its printed cost. */
  private def splitting(cards: Vector[String]): Decide =
    decide(DecisionQuery.Partition(Vector(
      DecisionSection("favor", "Favor", 1, Some(1)),
      DecisionSection("secret", "Secret", cards.size - 1,
        Some(cards.size - 1))),
      cards.map(name => DecisionOption.Denizen(DecisionOptionRef.Denizen(
        CatalogNames.denizen(name))))))

  private val threeCards = splitting(Vector("Horse Archers", "Wrestlers",
    "Errand Boy"))

  test("a small partition with no surviving split is refused, by the " +
      "walk and by a search"):
    assertEquals(WalkerSearch.narrow(threeCards, only(_ => false)),
      Left(refused))
    assertEquals(WalkerSearch.reach(threeCards, only(_ => false)),
      Left(refused))

  test("a small partition with one surviving split is kept whole"):
    val favorOnArchers: DecisionAnswer => Boolean =
      case DecisionAnswer.PartitionAnswer(placements) => placements.contains(
        DecisionPlacement(DecisionOptionRef.Denizen(card), "favor"))
      case _ => false
    assertEquals(WalkerSearch.narrow(threeCards, only(favorOnArchers)),
      Right(Some(threeCards)))

  test("a partition with more than 64 ways to place is not tried"):
    // Seven cards in two sections: 128 ways.
    val sevenCards = splitting(Vector("Horse Archers", "Wrestlers",
      "Errand Boy", "Alchemist", "Messenger", "Wolves", "Elders"))
    assertEquals(WalkerSearch.narrow(sevenCards, only(_ => false)),
      Right(Some(sevenCards)))
    assertEquals(WalkerSearch.reach(sevenCards, only(_ => false)),
      Right(WalkerSearch.Reach.Answerable))
