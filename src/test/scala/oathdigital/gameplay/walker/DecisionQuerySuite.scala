package oathdigital.gameplay.walker

import oathdigital.gameplay.OathViolation
import oathdigital.model.{DecisionAnswer, DecisionOption, DecisionOptionRef,
  DecisionPlacement, DecisionQuery, DecisionSection, DenizenId, RelicId}

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

  private def invalid(detail: String) =
    Left(OathViolation.InvalidEventOrder(detail))

  private def accepts(query: DecisionQuery, answer: DecisionAnswer) =
    DecisionQueries.accepts(decisionId, query, answer)

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
}
