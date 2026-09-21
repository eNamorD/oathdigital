package oathdigital.gameplay

import oathdigital.model._

class CoreOperationsSuite extends munit.FunSuite {
  private val red = PlayerId("red")
  private val blue = PlayerId("blue")
  private val redArea = Location.PlayArea(red)
  private val blueArea = Location.PlayArea(blue)
  private val site = Location.Site(SiteId("site:mine"))

  test("operation requiredness distinguishes costs, draws, and optional effects") {
    assert(!GainSupply(red, 1).required)
    assert(SpendSupply(red, 1).required)
    assert(!SpendSupply(red, 1, required = false).required)
    assert(PayCost(red, redArea, Cost.free).required)
    assert(Draw(red, Vector(DenizenId("denizen:one")),
      Location.Deck(CardDeck.World), redArea).required)
    assert(Exchange(Give(Piece.Favor(1), red, redArea, blueArea),
      Give(Piece.Secrets(1), blue, blueArea, redArea)).required)
    assert(!Replace(Piece.Warbands(ForceKind.Imperial, 1),
      Piece.Warbands(ForceKind.Bandit, 1), PositionedLocation(site)).required)
    assert(Discard.Denizen(DenizenId("denizen:one"), PositionedLocation(site),
      Region.Cradle, Suit.Order, 0, 0, red, required = true).required)
    assert(Play(DenizenId("denizen:one"), PositionedLocation(redArea),
      site, Orientation.FaceUp, required = true).required)
    assert(Replace(Piece.Warbands(ForceKind.Imperial, 1),
      Piece.Warbands(ForceKind.Bandit, 1), PositionedLocation(site),
      required = true).required)
  }

  test("Swap is two simultaneous reciprocal card moves") {
    val first = DenizenId("denizen:first")
    val second = RelicId("relic:second")
    val firstLocation = PositionedLocation(redArea)
    val secondLocation = PositionedLocation(site)

    val swap = Swap(first, firstLocation, second, secondLocation)

    assertEquals(Operation.flatten(swap), Vector(
      Move(Piece.Card(first), firstLocation, secondLocation),
      Move(Piece.Card(second), secondLocation, firstLocation)))
    assert(swap.simultaneous)
  }

  test("Bury is a distinct primitive with the matching deck bottom") {
    val denizen = Bury(BuryableCard.Denizen(DenizenId("denizen:one")),
      PositionedLocation(site))
    val relic = Bury(BuryableCard.Relic(RelicId("relic:one")),
      PositionedLocation(redArea))
    val edifice = Bury(BuryableCard.Edifice(EdificeId("edifice:one")),
      PositionedLocation(site))

    val buries = Vector(denizen, relic, edifice)
    assertEquals(buries.map(_.to), Vector(
      PositionedLocation(Location.Deck(CardDeck.World),
        StackPosition.Bottom),
      PositionedLocation(Location.Deck(CardDeck.Relic),
        StackPosition.Bottom),
      PositionedLocation(Location.Deck(CardDeck.Edifice),
        StackPosition.Bottom)))
    assertEquals(buries.flatMap(Operation.flatten), buries)
  }

  test("Discard routes cards and their resources by glossary rules") {
    val card = DenizenId("denizen:one")
    val discard = Discard.Denizen(card, PositionedLocation(site),
      to = Region.Provinces,
      suit = Suit.Order, favor = 2, secrets = 1, actingPlayer = red)

    assertEquals(Operation.flatten(discard), Vector(
      Move(Piece.Card(card), PositionedLocation(site),
        PositionedLocation(Location.RegionalDiscard(Region.Provinces),
          StackPosition.Top), resultingOrientation = Some(Orientation.FaceDown)),
      Move(Piece.Favor(2),
        PositionedLocation(Location.OnCard(card)),
        PositionedLocation(Location.FavorBank(Suit.Order))),
      Move(Piece.Secrets(1),
        PositionedLocation(Location.OnCard(card)),
        PositionedLocation(redArea)),
      FlipSecrets(red, 1, SecretSide.FaceUp, SecretSide.FaceDown)))
  }

  test("Gain, burn, kill, sacrifice, and replace use the correct banks") {
    val exile = ForceKind.Exile(LineageId("red-lineage"))
    val gained = Operation.flatten(Gain.Secrets(red, 2)).head
    assertEquals(gained, Move(Piece.Secrets(2),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(redArea)))

    val burned = Operation.flatten(Burn.favor(1,
      PositionedLocation(redArea))).head
    assertEquals(burned, Move(Piece.Favor(1),
      PositionedLocation(redArea),
      PositionedLocation(Location.SharedBank)))

    val warbands = Piece.Warbands(exile, 2)
    val kill = Kill(warbands, PositionedLocation(site))
    val sacrifice = Sacrifice(red, warbands, PositionedLocation(site))
    assertEquals(Operation.flatten(sacrifice), Operation.flatten(kill))

    val imperial = Piece.Warbands(ForceKind.Imperial, 2)
    val replace = Replace(warbands, imperial, PositionedLocation(site))
    assertEquals(Operation.flatten(replace), Vector(
      Move(warbands, PositionedLocation(site),
        PositionedLocation(Location.WarbandBank(exile))),
      Move(imperial,
        PositionedLocation(Location.WarbandBank(ForceKind.Imperial)),
        PositionedLocation(site))))
    assert(replace.simultaneous)
  }

  test("Exchange composes reciprocal Give operations") {
    val favor = Piece.Favor(1)
    val secret = Piece.Secrets(1)
    val give = Give(favor, red, redArea, blueArea)
    val receive = Give(secret, blue, blueArea, redArea)

    val exchange = Exchange(give, receive)

    assertEquals(Operation.flatten(exchange),
      Operation.flatten(give) ++ Operation.flatten(receive))
    assertEquals(Give(favor, red, redArea, Location.SharedBank).to,
      Location.SharedBank)
    intercept[IllegalArgumentException](Exchange(give,
      Give(secret, red, redArea, blueArea)))
  }

  test("invalid operation descriptions fail at construction") {
    intercept[IllegalArgumentException](Piece.Favor(0))
    intercept[IllegalArgumentException](Draw(red, Vector.empty,
      Location.Deck(CardDeck.World), redArea))
    intercept[IllegalArgumentException](Play(DenizenId("denizen:one"),
      PositionedLocation(site), Location.SharedBank,
      Orientation.FaceUp))
    intercept[IllegalArgumentException](Swap(DenizenId("denizen:one"),
      PositionedLocation(site), RelicId("relic:one"), PositionedLocation(site)))
    intercept[IllegalArgumentException](Move(Piece.Favor(1),
      PositionedLocation(redArea), PositionedLocation(blueArea),
      resultingOrientation = Some(Orientation.FaceDown)))
    intercept[IllegalArgumentException](Take(Piece.Favor(1), red,
      Location.SharedBank, blueArea))
    intercept[IllegalArgumentException](FlipSecrets(red, 0,
      SecretSide.FaceUp, SecretSide.FaceDown))
    intercept[IllegalArgumentException](FlipSecrets(red, 1,
      SecretSide.FaceUp, SecretSide.FaceUp))
  }

  test("Draw is a top-first sequence of Take operations") {
    val cards = Vector[CardId](DenizenId("denizen:first"),
      VisionId("vision:second"))
    val source = Location.Deck(CardDeck.World)
    val destination = Location.Hand(red)

    val draw = Draw(red, cards, source, destination)

    assertEquals(draw.takes, cards.map(card => Take(Piece.Card(card),
      red, source, destination, StackPosition.Top)))
    assertEquals(Operation.flatten(draw), draw.takes.flatMap(Operation.flatten))
  }

  test("every glossary composite retains its semantic root and primitive order") {
    val card = DenizenId("denizen:root")
    val relic = RelicId("relic:root")
    val exile = ForceKind.Exile(LineageId("red-lineage"))
    val from = PositionedLocation(site)

    val operations = Vector[CoreOperation](
      Burn.favor(1, from),
      Discard.Vision(VisionId("vision:root"), from, Region.Cradle),
      Discard.RuinedEdifice(EdificeId("edifice:root"), from, Suit.Order,
        favor = 1, secrets = 1, red),
      Discard.Relic(relic, from, secrets = 1, red),
      Draw(red, Vector(card), Location.Deck(CardDeck.World), redArea),
      Exchange(Give(Piece.Favor(1), red, redArea, blueArea),
        Give(Piece.Secrets(1), blue, blueArea, redArea)),
      Gain.Favor(red, Suit.Order, 1),
      Gain.Secrets(red, 1),
      Gain.Warbands(red, exile, 1),
      Give(Piece.Favor(1), red, redArea, blueArea),
      Kill(Piece.Warbands(exile, 1), from),
      Play(card, from, redArea, Orientation.FaceDown),
      Replace(Piece.Warbands(exile, 1),
        Piece.Warbands(ForceKind.Imperial, 1), from),
      Reveal(card, site),
      Sacrifice(red, Piece.Warbands(exile, 1), from),
      Swap(card, from, relic, PositionedLocation(redArea)),
      Take(Piece.Card(card), red, site, redArea)
    )

    operations.foreach { operation =>
      val leaves = Operation.flatten(operation)
      assert(leaves.nonEmpty, operation.toString)
      assert(leaves.forall(leaf => Operation.flatten(leaf).size == 1),
        operation.toString)
    }
    assertEquals(operations.filter(_.simultaneous).map(_.getClass.getSimpleName),
      Vector("Replace", "Swap"))
    assertEquals(operations.collectFirst {
      case value: Discard.RuinedEdifice => Operation.flatten(value)
    }.get, Vector(
      Move(Piece.Card(EdificeId("edifice:root")), from,
        PositionedLocation(Location.Deck(CardDeck.Edifice), StackPosition.Bottom)),
      Move(Piece.Favor(1), PositionedLocation(Location.OnCard(
        EdificeId("edifice:root"))),
        PositionedLocation(Location.FavorBank(Suit.Order))),
      Move(Piece.Secrets(1), PositionedLocation(Location.OnCard(
        EdificeId("edifice:root"))), PositionedLocation(redArea)),
      FlipSecrets(red, 1, SecretSide.FaceUp, SecretSide.FaceDown)))
  }

  test("invalid descriptions can be converted to typed replay failures") {
    val failure = OperationError.describe(Piece.Favor(0)).left.toOption.get
    assertEquals(failure.code, "invalid-description")
    assertEquals(failure.toViolation,
      OathViolation.CoreOperationRejected("invalid-description",
        "favor amount must be positive"))
  }
}
