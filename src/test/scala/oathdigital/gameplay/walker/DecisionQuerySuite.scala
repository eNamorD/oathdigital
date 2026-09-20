package oathdigital.gameplay.walker

import oathdigital.model.{DecisionAnswer, DecisionOption, DecisionOptionRef, DecisionPlacement, DecisionQuery, DecisionSection, DenizenId, DistributeAmount, DistributeSlot, NegotiationBounds, NegotiationDisclosure, NegotiationDisclosureRef, NegotiationTerms, NegotiationTransfer, OathViolation, PlayerId, RelicId, SiteId, Suit}

/** Task 2: the generic decision contract, exercised with hand-built queries
  * and no game state at all.
  *
  * That absence is the point rather than a convenience. `DecisionQueries`
  * takes no `ReadyGame`, so it cannot express state-dependent legality even
  * by accident (plan ruling R3); a suite that needed a fixture game to
  * compile would be the first sign that boundary had been crossed.
  *
  * Every assertion compares whole `OathViolation` values, so each rejection
  * below is pinned to its own message: a refactor that collapsed two
  * distinct failures into one generic "invalid answer" would fail here
  * rather than silently degrade what a player is told.
  */
class DecisionQuerySuite extends munit.FunSuite {

  private val decisionId = "recover.choice"

  private val continueRef = DecisionOptionRef.Button("continue")
  private val stopRef = DecisionOptionRef.Button("stop")
  private val relicRef = DecisionOptionRef.Relic(RelicId("relic-1"))

  private val continueOption = DecisionOption.Button(continueRef, "Continue")
  private val stopOption = DecisionOption.Button(stopRef, "Stop")

  private val chooseOne =
    DecisionQuery.ChooseOne(Vector(continueOption, stopOption))

  private def denizenRef(id: String) =
    DecisionOptionRef.Denizen(DenizenId(id))
  private def denizenOption(id: String) =
    DecisionOption.Denizen(denizenRef(id))

  private val favorSection = DecisionSection("favor", "Favor", 1)
  private val secretSection = DecisionSection("secret", "Secret", 1)

  private val partition = DecisionQuery.Partition(
    sections = Vector(favorSection, secretSection),
    options = Vector(denizenOption("d1"), denizenOption("d2"),
      denizenOption("d3")))

  /** One complete, legal answer to [[partition]], reused wherever a test
    * needs a placement that is beyond reproach so that the thing under test
    * is the only variable.
    */
  private val legalPlacement = DecisionAnswer.PartitionAnswer(Vector(
    DecisionPlacement(denizenRef("d1"), "favor"),
    DecisionPlacement(denizenRef("d2"), "favor"),
    DecisionPlacement(denizenRef("d3"), "secret")))

  private def invalid(detail: String) =
    Left(OathViolation.InvalidEventOrder(detail))

  private val anyone = PlayerId("player-red")
  private def accepts(query: DecisionQuery, answer: DecisionAnswer) =
    DecisionQueries.accepts(decisionId, query, answer, anyone)

  private def wellFormed(query: DecisionQuery) =
    DecisionQueries.wellFormed(decisionId, query)

  // (a) ChooseOne accepts each declared reference, and only those.

  test("a choose-one query accepts every option it declares") {
    assertEquals(
      accepts(chooseOne, DecisionAnswer.ChooseOneAnswer(continueRef)),
      Right(()))
    assertEquals(
      accepts(chooseOne, DecisionAnswer.ChooseOneAnswer(stopRef)),
      Right(()))
  }

  test("a choose-one query rejects a reference it does not declare") {
    assertEquals(
      accepts(chooseOne, DecisionAnswer.ChooseOneAnswer(relicRef)),
      invalid(s"decision $decisionId does not offer the selected option"))
  }

  // (b) A malformed choose-one query fails structurally.

  test("a choose-one query with duplicate option references is malformed") {
    assertEquals(
      wellFormed(DecisionQuery.ChooseOne(
        Vector(continueOption, continueOption))),
      invalid(s"decision $decisionId declares duplicate options"))
  }

  test("a choose-one query with no options is malformed") {
    assertEquals(
      wellFormed(DecisionQuery.ChooseOne(Vector.empty)),
      invalid(s"decision $decisionId declares no options"))
  }

  test("a well-formed choose-one query passes the structural check") {
    assertEquals(wellFormed(chooseOne), Right(()))
  }

  // (c) Legality is keyed on the reference, never on the label.

  test("two options differing only in label are duplicates") {
    assertEquals(
      wellFormed(DecisionQuery.ChooseOne(Vector(continueOption,
        DecisionOption.Button(continueRef, "Keep going")))),
      invalid(s"decision $decisionId declares duplicate options"))
  }

  test("a relabelled option stays answerable by its unchanged reference") {
    val relabelled = DecisionQuery.ChooseOne(Vector(
      DecisionOption.Button(continueRef, "Press on"), stopOption))
    assertEquals(
      accepts(relabelled, DecisionAnswer.ChooseOneAnswer(continueRef)),
      Right(()))
  }

  // (d) Partition accepts a complete, legal placement.

  test("a partition query accepts a placement of every option into a " +
      "declared section") {
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "favor"),
      DecisionPlacement(denizenRef("d2"), "favor"),
      DecisionPlacement(denizenRef("d3"), "secret")))
    assertEquals(accepts(partition, answer), Right(()))
  }

  test("a partition section may hold more than its minimum") {
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "secret"),
      DecisionPlacement(denizenRef("d2"), "secret"),
      DecisionPlacement(denizenRef("d3"), "favor")))
    assertEquals(accepts(partition, answer), Right(()))
  }

  // (e) Each way of getting a partition answer wrong is reported distinctly.

  test("a partition answer that leaves an option unplaced is rejected") {
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "favor"),
      DecisionPlacement(denizenRef("d2"), "secret")))
    assertEquals(accepts(partition, answer),
      invalid(s"decision $decisionId leaves an option unplaced"))
  }

  test("a partition answer that places one option twice is rejected") {
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "favor"),
      DecisionPlacement(denizenRef("d1"), "secret"),
      DecisionPlacement(denizenRef("d2"), "favor"),
      DecisionPlacement(denizenRef("d3"), "secret")))
    assertEquals(accepts(partition, answer),
      invalid(s"decision $decisionId places an option more than once"))
  }

  test("a partition answer that places an undeclared option is rejected") {
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "favor"),
      DecisionPlacement(denizenRef("d2"), "favor"),
      DecisionPlacement(denizenRef("d3"), "secret"),
      DecisionPlacement(relicRef, "secret")))
    assertEquals(accepts(partition, answer),
      invalid(s"decision $decisionId does not offer a placed option"))
  }

  test("a partition answer naming an undeclared section is rejected") {
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "favor"),
      DecisionPlacement(denizenRef("d2"), "favor"),
      DecisionPlacement(denizenRef("d3"), "warband")))
    assertEquals(accepts(partition, answer),
      invalid(s"decision $decisionId has no section 'warband'"))
  }

  test("a partition answer leaving a section under its minimum is rejected") {
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "favor"),
      DecisionPlacement(denizenRef("d2"), "favor"),
      DecisionPlacement(denizenRef("d3"), "favor")))
    assertEquals(accepts(partition, answer),
      invalid(s"decision $decisionId leaves section 'secret' below its " +
        "minimum of 1"))
  }

  test("a section with a zero minimum may be left empty") {
    val optional = DecisionQuery.Partition(
      sections = Vector(favorSection, DecisionSection("secret", "Secret", 0)),
      options = Vector(denizenOption("d1"), denizenOption("d2")))
    val answer = DecisionAnswer.PartitionAnswer(Vector(
      DecisionPlacement(denizenRef("d1"), "favor"),
      DecisionPlacement(denizenRef("d2"), "favor")))
    assertEquals(wellFormed(optional), Right(()))
    assertEquals(accepts(optional, answer), Right(()))
  }

  // (f) A malformed partition query fails structurally.

  test("a partition query with duplicate section keys is malformed") {
    assertEquals(
      wellFormed(partition.copy(
        sections = Vector(favorSection, favorSection.copy(label = "Again")))),
      invalid(s"decision $decisionId declares duplicate sections"))
  }

  test("a partition query with a negative minimum is malformed") {
    assertEquals(
      wellFormed(partition.copy(
        sections = Vector(favorSection, secretSection.copy(minRequired = -1)))),
      invalid(s"decision $decisionId declares section 'secret' with a " +
        "negative minimum"))
  }

  test("a partition query with duplicate options is malformed") {
    assertEquals(
      wellFormed(partition.copy(
        options = Vector(denizenOption("d1"), denizenOption("d1")))),
      invalid(s"decision $decisionId declares duplicate options"))
  }

  test("a partition query with no options is malformed") {
    assertEquals(
      wellFormed(partition.copy(options = Vector.empty)),
      invalid(s"decision $decisionId declares no options"))
  }

  test("a partition query with fewer than two sections is malformed") {
    val message = s"decision $decisionId declares fewer than two sections"
    assertEquals(wellFormed(partition.copy(sections = Vector.empty)),
      invalid(message))
    assertEquals(wellFormed(partition.copy(sections = Vector(favorSection))),
      invalid(message))
  }

  test("a partition query whose minimums exceed its options is malformed") {
    assertEquals(
      wellFormed(partition.copy(sections = Vector(
        favorSection.copy(minRequired = 2),
        secretSection.copy(minRequired = 2)))),
      invalid(s"decision $decisionId declares section minimums no answer " +
        "can meet"))
  }

  test("minimums that would overflow an Int sum are still unmeetable") {
    assertEquals(
      wellFormed(partition.copy(sections = Vector(
        favorSection.copy(minRequired = Int.MaxValue),
        secretSection.copy(minRequired = Int.MaxValue)))),
      invalid(s"decision $decisionId declares section minimums no answer " +
        "can meet"))
  }

  test("a partition query whose section takes every option is malformed") {
    assertEquals(
      wellFormed(partition.copy(sections = Vector(
        favorSection.copy(minRequired = 3),
        secretSection.copy(minRequired = 0)))),
      invalid(s"decision $decisionId declares section 'favor' as taking " +
        "every option, leaving nothing to decide"))
  }

  test("minimums that fix each section's size still leave a real decision") {
    val exact = DecisionQuery.Partition(
      sections = Vector(favorSection.copy(minRequired = 2),
        secretSection.copy(minRequired = 2)),
      options = Vector(denizenOption("d1"), denizenOption("d2"),
        denizenOption("d3"), denizenOption("d4")))
    assertEquals(wellFormed(exact), Right(()))
  }

  test("a single-option choose-one query is a consent step, not a forced " +
      "decision") {
    assertEquals(
      wellFormed(DecisionQuery.ChooseOne(Vector(continueOption))),
      Right(()))
  }

  test("a well-formed partition query passes the structural check") {
    assertEquals(wellFormed(partition), Right(()))
  }

  // (g) Neither query shape matches the other's answer loosely.

  test("a choose-one query rejects a partition answer") {
    assertEquals(
      accepts(chooseOne, DecisionAnswer.PartitionAnswer(Vector(
        DecisionPlacement(continueRef, "favor")))),
      invalid(s"decision $decisionId expects a single-choice answer"))
  }

  test("a partition query rejects a choose-one answer") {
    assertEquals(
      accepts(partition, DecisionAnswer.ChooseOneAnswer(denizenRef("d1"))),
      invalid(s"decision $decisionId expects a partition answer"))
  }

  // (h) Task 5b: panel copy is prompt copy, and neither function reads it.

  test("a choose-one answer is accepted whatever heading the query wears") {
    val titled = chooseOne.copy(heading = Some("Recover"))
    val retitled = chooseOne.copy(heading = Some("Press your luck"))
    assertEquals(wellFormed(titled), wellFormed(retitled))
    Vector(chooseOne, titled, retitled).foreach { query =>
      assertEquals(accepts(query, DecisionAnswer.ChooseOneAnswer(continueRef)),
        Right(()), s"heading ${query.heading} must not change legality")
      assertEquals(accepts(query, DecisionAnswer.ChooseOneAnswer(relicRef)),
        invalid(s"decision $decisionId does not offer the selected option"))
    }
  }

  test("a partition answer is accepted whatever heading and confirm label " +
      "the query wears") {
    val titled = partition.copy(heading = Some("Forge a relic"),
      confirmLabel = Some("Complete Forge"))
    val retitled = partition.copy(heading = Some("Pay for the relic"),
      confirmLabel = Some("Pay"))
    assertEquals(wellFormed(titled), wellFormed(retitled))
    Vector(partition, titled, retitled).foreach { query =>
      assertEquals(accepts(query, legalPlacement), Right(()),
        s"copy ${query.heading} / ${query.confirmLabel} must not change " +
          "legality")
    }
  }

  private def bank(suit: Suit) = DecisionOptionRef.FavorBank(suit)
  private def slot(suit: Suit, min: Int, max: Int,
      suggested: Option[Int] = None) = DistributeSlot(bank(suit), min, max,
    suggested)
  private def dist(slots: Vector[DistributeSlot], total: Int,
      heading: Option[String] = Some("League Treaty"),
      confirm: String = "Move favor") =
    DecisionQuery.Distribute.exactly(slots, total, heading, confirm)

  private def ranged(slots: Vector[DistributeSlot], min: Int, max: Int) =
    DecisionQuery.Distribute(slots, min, max, Some("Place force"), "Place")

  /** The spec's worked example: three source suits of two favor each, and a
    * destination that may take all six.
    */
  private val distribute = dist(Vector(
    slot(Suit.Arcane, 0, 2, Some(2)), slot(Suit.Discord, 0, 2, Some(2)),
    slot(Suit.Hearth, 0, 2, Some(2)), slot(Suit.Nomad, 0, 6, Some(0))), 6)

  private def amounts(values: (Suit, Int)*) = DecisionAnswer.DistributeAnswer(
    values.toVector.map { case (suit, n) => DistributeAmount(bank(suit), n) })

  private def violation(detail: String) =
    Left(OathViolation.InvalidEventOrder(s"decision $decisionId $detail"))

  test("a distribution with two or more open slots and a reachable total is well formed") {
    assertEquals(DecisionQueries.wellFormed(decisionId, distribute), Right(()))
  }

  test("a malformed distribution names its own defect") {
    val two = Vector(slot(Suit.Arcane, 0, 2), slot(Suit.Nomad, 0, 2))
    val cases = Vector(
      dist(two, 1, heading = None) -> "declares no heading",
      dist(two, 1, heading = Some("  ")) -> "declares no heading",
      dist(two, 1, confirm = " ") -> "declares a blank confirm label",
      dist(Vector(slot(Suit.Arcane, 0, 2)), 1) ->
        "declares fewer than two slots",
      dist(Vector(slot(Suit.Arcane, 0, 2), slot(Suit.Arcane, 0, 2)), 1) ->
        "declares duplicate options",
      dist(Vector(slot(Suit.Arcane, 0, 2), DistributeSlot(
        DecisionOptionRef.Button("stop"), 0, 2, None)), 1) ->
        "declares slot button/stop, which has no presentable option",
      dist(Vector(slot(Suit.Arcane, -1, 2), slot(Suit.Nomad, 0, 2)), 1) ->
        "declares slot favor-bank/arcane with bounds -1..2",
      dist(Vector(slot(Suit.Arcane, 3, 2), slot(Suit.Nomad, 0, 2)), 1) ->
        "declares slot favor-bank/arcane with bounds 3..2",
      dist(two, 5) -> "declares a total no answer can meet",
      dist(Vector(slot(Suit.Arcane, 1, 2), slot(Suit.Nomad, 1, 2)), 2) ->
        "declares minimums that already make its total, leaving nothing to decide",
      dist(two, 4) ->
        "declares maximums that already make its total, leaving nothing to decide",
      dist(Vector(slot(Suit.Arcane, 0, 2), slot(Suit.Nomad, 0, 0)), 1) ->
        "declares fewer than two variable slots, leaving nothing to decide",
      dist(Vector(slot(Suit.Arcane, 0, 2, Some(1)), slot(Suit.Nomad, 0, 2)), 1) ->
        "suggests amounts for some slots but not all",
      dist(Vector(slot(Suit.Arcane, 0, 2, Some(2)),
        slot(Suit.Nomad, 0, 2, Some(2))), 1) ->
        "suggests a distribution it would not accept")
    cases.foreach { case (query, detail) =>
      assertEquals(DecisionQueries.wellFormed(decisionId, query),
        violation(detail), detail)
    }
  }

  test("the worked example's answer is accepted") {
    assertEquals(DecisionQueries.accepts(decisionId, distribute, amounts(
      Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2, Suit.Nomad -> 3), anyone),
      Right(()))
  }

  test("a ranged distribution is well formed when its range is reachable and open") {
    val two = Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3))
    assertEquals(DecisionQueries.wellFormed(decisionId, ranged(two, 0, 3)),
      Right(()))
    assertEquals(DecisionQueries.wellFormed(decisionId, ranged(two, 1, 4)),
      Right(()))
  }

  test("a malformed range names its own defect") {
    val two = Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3))
    val cases = Vector(
      ranged(two, 4, 3) -> "declares a total no answer can meet",
      ranged(two, -1, 3) -> "declares a total no answer can meet",
      ranged(two, 7, 8) -> "declares a total no answer can meet",
      ranged(Vector(slot(Suit.Arcane, 2, 3), slot(Suit.Nomad, 1, 3)), 0, 3) ->
        "declares minimums that already make its total, leaving nothing to decide",
      ranged(two, 6, 9) ->
        "declares maximums that already make its total, leaving nothing to decide")
    cases.foreach { case (query, detail) =>
      assertEquals(DecisionQueries.wellFormed(decisionId, query),
        violation(detail), detail)
    }
  }

  test("a ranged distribution accepts any sum inside its range and no other") {
    val query = ranged(Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3)),
      0, 3)
    Vector(0 -> 0, 1 -> 0, 2 -> 1, 3 -> 0).foreach { case (a, n) =>
      assertEquals(DecisionQueries.accepts(decisionId, query,
        amounts(Suit.Arcane -> a, Suit.Nomad -> n), anyone), Right(()))
    }
    assertEquals(DecisionQueries.accepts(decisionId, query,
      amounts(Suit.Arcane -> 2, Suit.Nomad -> 2), anyone),
      violation("distributes an amount outside 0..3"))
    val floor = ranged(Vector(slot(Suit.Arcane, 0, 3), slot(Suit.Nomad, 0, 3)),
      2, 4)
    assertEquals(DecisionQueries.accepts(decisionId, floor,
      amounts(Suit.Arcane -> 1, Suit.Nomad -> 0), anyone),
      violation("distributes an amount outside 2..4"))
  }

  test("a mismatched distribution answer names its own defect") {
    val full = Vector(Suit.Arcane -> 0, Suit.Discord -> 1, Suit.Hearth -> 2,
      Suit.Nomad -> 3)
    val cases = Vector(
      amounts(full :+ (Suit.Order -> 0): _*) ->
        "does not offer a distributed option",
      amounts(full :+ (Suit.Nomad -> 0): _*) ->
        "distributes to an option more than once",
      amounts(full.init: _*) -> "leaves an option undistributed",
      amounts(Suit.Arcane -> 3, Suit.Discord -> 0, Suit.Hearth -> 0,
        Suit.Nomad -> 3) -> "distributes to favor-bank/arcane outside 0..2",
      amounts(Suit.Arcane -> 0, Suit.Discord -> 0, Suit.Hearth -> 0,
        Suit.Nomad -> 3) -> "distributes an amount other than its total of 6")
    cases.foreach { case (answer, detail) =>
      assertEquals(DecisionQueries.accepts(decisionId, distribute, answer, anyone),
        violation(detail), detail)
    }
    assertEquals(DecisionQueries.accepts(decisionId, distribute,
      DecisionAnswer.ChooseOneAnswer(bank(Suit.Nomad)), anyone),
      violation("expects a distribution answer"))
    assertEquals(DecisionQueries.accepts(decisionId, chooseOne,
      amounts(full: _*), anyone), violation("expects a single-choice answer"))
  }

  private def siteRef(id: String) = DecisionOptionRef.Site(SiteId(id))
  private val sites = Vector("a", "b", "c").map(id =>
    DecisionOption.Site(siteRef(id)))
  private def many(min: Int, options: Vector[DecisionOption] = sites,
      max: Option[Int] = None) =
    DecisionQuery.ChooseMany(min, max.getOrElse(min), options,
      Some("Choose sites"))

  test("a choose-many query needs 0 <= min <= max <= options, one pickable option, and is not forced") {
    assertEquals(wellFormed(many(1)), Right(()))
    assertEquals(wellFormed(many(2)), Right(()))
    assertEquals(wellFormed(many(1, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(2, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(0, max = Some(1))), Right(()))
    assertEquals(wellFormed(many(0, max = Some(3))), Right(()))
    assertEquals(wellFormed(many(0)),
      invalid("decision recover.choice declares no selection to make"))
    assertEquals(wellFormed(many(-1, max = Some(2))), invalid(
      "decision recover.choice declares a selection range -1..2"))
    assertEquals(wellFormed(many(3, max = Some(2))), invalid(
      "decision recover.choice declares a selection range 3..2"))
    assertEquals(wellFormed(many(1, max = Some(4))), invalid("decision " +
      "recover.choice declares a maximum above its option count"))
    assertEquals(wellFormed(many(3)), invalid("decision recover.choice " +
      "declares a selection that already takes every option, leaving " +
      "nothing to decide"))
    assertEquals(wellFormed(many(1, sites :+ sites.head)),
      invalid("decision recover.choice declares duplicate options"))
  }

  test("a choose-many answer names distinct offered options within its range") {
    val q = many(2)
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("c")))), Right(()))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a")))), invalid(
      "decision recover.choice selects 1 options instead of 2"))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("a")))), invalid(
      "decision recover.choice selects an option more than once"))
    assertEquals(accepts(q, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("z")))), invalid(
      "decision recover.choice does not offer a selected option"))
    assertEquals(accepts(q, DecisionAnswer.ChooseOneAnswer(siteRef("a"))),
      invalid("decision recover.choice expects a multiple-choice answer"))
    val range = many(1, max = Some(3))
    assertEquals(accepts(range, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a")))), Right(()))
    assertEquals(accepts(range, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("b"), siteRef("c")))), Right(()))
    assertEquals(accepts(range, DecisionAnswer.ChooseManyAnswer(Vector.empty)),
      invalid("decision recover.choice selects 0 options outside 1..3"))
  }

  test("an optional choose-many accepts the empty selection and any subset") {
    val optional = many(0, max = Some(3))
    assertEquals(accepts(optional, DecisionAnswer.ChooseManyAnswer(Vector.empty)),
      Right(()))
    assertEquals(accepts(optional, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("b")))), Right(()))
    assertEquals(accepts(optional, DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("b"), siteRef("c")))), Right(()))
    assertEquals(accepts(many(0, max = Some(2)), DecisionAnswer.ChooseManyAnswer(
      Vector(siteRef("a"), siteRef("b"), siteRef("c")))), invalid(
      "decision recover.choice selects 3 options outside 0..2"))
  }

  private def amount(min: Int, max: Int, heading: Option[String] = Some("Amount"),
      confirm: String = "Place") = DecisionQuery.ChooseAmount(min, max, heading, confirm)

  test("a choose-amount query needs a heading, a confirm label and 0 <= min <= max") {
    assertEquals(wellFormed(amount(3, 3)), Right(()))
    assertEquals(wellFormed(amount(1, 6)), Right(()))
    assertEquals(wellFormed(amount(4, 3)), invalid(
      "decision recover.choice declares an amount range 4..3"))
    assertEquals(wellFormed(amount(-1, 3)), invalid(
      "decision recover.choice declares an amount range -1..3"))
    assertEquals(wellFormed(amount(1, 3, heading = None)),
      invalid("decision recover.choice declares no heading"))
    assertEquals(wellFormed(amount(1, 3, confirm = " ")),
      invalid("decision recover.choice declares a blank confirm label"))
  }

  test("a choose-amount answer is accepted exactly inside its range") {
    val q = amount(3, 5)
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(3)), Right(()))
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(5)), Right(()))
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(2)), invalid(
      "decision recover.choice amount 2 is outside 3..5"))
    assertEquals(accepts(q, DecisionAnswer.ChooseAmountAnswer(6)), invalid(
      "decision recover.choice amount 6 is outside 3..5"))
    assertEquals(accepts(q, DecisionAnswer.ChooseOneAnswer(siteRef("a"))),
      invalid("decision recover.choice expects an amount answer"))
  }

  // Negotiate: legality depends on who answers.

  private val red = PlayerId("red")
  private val blue = PlayerId("blue")
  private val green = PlayerId("green")
  private val heldRelic = RelicId("relic-1")
  private val adviser = NegotiationDisclosureRef.Adviser(red, DenizenId("d1"))
  private val redBounds = NegotiationBounds(Vector(blue), 5, Vector(heldRelic),
    Vector(adviser))
  private val blueBounds = NegotiationBounds(Vector(red), 2, Vector.empty,
    Vector.empty)

  private def deal(accepted: Set[PlayerId] = Set.empty,
      acceptors: Set[PlayerId] = Set.empty) = DecisionQuery.Negotiate(
    Vector(red, blue), Map(red -> NegotiationTerms(), blue -> NegotiationTerms()),
    accepted, Map(red -> redBounds, blue -> blueBounds), acceptors,
    Some("Negotiation"))

  private def acceptsBy(by: PlayerId, answer: DecisionAnswer,
      query: DecisionQuery = deal(acceptors = Set(red, blue))) =
    DecisionQueries.accepts(decisionId, query, answer, by)

  test("a negotiate query needs two distinct participants and consistent maps") {
    assertEquals(wellFormed(deal()), Right(()))
    assertEquals(wellFormed(deal().copy(participants = Vector(red))),
      invalid("decision recover.choice declares fewer than two distinct participants"))
    assertEquals(wellFormed(deal().copy(participants = Vector(red, red))),
      invalid("decision recover.choice declares fewer than two distinct participants"))
    assertEquals(wellFormed(deal().copy(bounds = Map(red -> redBounds))),
      invalid("decision recover.choice declares bounds for players outside the deal"))
    assertEquals(wellFormed(deal().copy(terms = Map(red -> NegotiationTerms()))),
      invalid("decision recover.choice declares terms for players outside the deal"))
    assertEquals(wellFormed(deal(accepted = Set(green))),
      invalid("decision recover.choice declares an acceptance from outside the deal"))
  }

  test("proposed terms must sit inside the author's bounds") {
    val fine = NegotiationTerms(
      Vector(NegotiationTransfer(blue, 3, Vector(heldRelic))),
      Vector(NegotiationDisclosure(blue, adviser)))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(fine)), Right(()))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms())),
      Right(()))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector(NegotiationTransfer(blue, 6, Vector.empty))))),
      invalid("decision recover.choice offers more than 5 favor"))
    assertEquals(acceptsBy(blue, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector(NegotiationTransfer(red, 1, Vector(heldRelic)))))),
      invalid("decision recover.choice offers a relic its author does not hold"))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector(NegotiationTransfer(green, 1, Vector.empty))))),
      invalid("decision recover.choice offers terms to a player outside the deal"))
    assertEquals(acceptsBy(red, DecisionAnswer.ProposeTerms(NegotiationTerms(
      Vector.empty, Vector(NegotiationDisclosure(blue,
        NegotiationDisclosureRef.HeldRelic(red, heldRelic)))))),
      invalid("decision recover.choice promises a disclosure its author cannot make"))
    assertEquals(acceptsBy(green, DecisionAnswer.ProposeTerms(fine)),
      invalid("decision recover.choice is not open to this player"))
  }

  test("only an acceptor accepts and any participant declines") {
    assertEquals(acceptsBy(red, DecisionAnswer.AcceptDeal), Right(()))
    assertEquals(acceptsBy(red, DecisionAnswer.AcceptDeal, deal()),
      invalid("decision recover.choice does not let this player accept now"))
    assertEquals(acceptsBy(green, DecisionAnswer.AcceptDeal),
      invalid("decision recover.choice does not let this player accept now"))
    assertEquals(acceptsBy(blue, DecisionAnswer.DeclineDeal, deal()), Right(()))
    assertEquals(acceptsBy(green, DecisionAnswer.DeclineDeal, deal()),
      invalid("decision recover.choice is not open to this player"))
  }

  test("negotiation answers and queries only meet each other") {
    assertEquals(acceptsBy(red, DecisionAnswer.ChooseOneAnswer(continueRef)),
      invalid("decision recover.choice expects a negotiation answer"))
    assertEquals(acceptsBy(red, DecisionAnswer.AcceptDeal, chooseOne),
      invalid("decision recover.choice expects a single-choice answer"))
  }
}
