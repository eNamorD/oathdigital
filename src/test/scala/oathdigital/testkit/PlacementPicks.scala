package oathdigital.testkit

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.powerresolver.PhasePowers
import oathdigital.gameplay.setup.FirstGameSetupFixture
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._

/** A card play's placement answer, picked by how it plays the card. A test
  * says "play it faceup"; which option of the placement decision does that is
  * the engine's to say (`CardPlayProcedure.playedAs`), so a test that only
  * needs the play made never spells an option.
  */
object PlacementPicks:
  /** How each option of `options` that may be chosen plays the card, in
    * order. */
  def offered(options: Vector[DecisionOption]): Vector[PlayedAs] =
    options.filter(_.available).flatMap(option =>
      CardPlayProcedure.playedAs(option.ref))

  /** The first option of `options` that plays the card `way`, preferring one
    * that may be chosen: a test that expects a refusal still reaches the
    * engine, with the option it shows unavailable. */
  def find(options: Vector[DecisionOption], way: PlayedAs)
      : Option[DecisionOptionRef] =
    val matching = options.filter(option =>
      CardPlayProcedure.playedAs(option.ref).contains(way))
    matching.find(_.available).orElse(matching.headOption).map(_.ref)

  /** As `find`, failing the test when no option plays the card `way`. */
  def pick(options: Vector[DecisionOption], way: PlayedAs)(
      using munit.Location): DecisionOptionRef =
    find(options, way).getOrElse(munit.Assertions.fail(
      s"no option plays the card $way; offered ${options.map(_.ref)}"))

  /** As `pick`, over the choose-one `decide` asks. */
  def pick(decide: Decide, way: PlayedAs)(using munit.Location)
      : DecisionOptionRef =
    pick(chooseOne(decide), way)

  /** The options of the choose-one `state` is parked on, after the
    * restriction look-ahead, rebuilt with the powers the rules that parked it
    * were given; empty when it is parked on no choose-one. */
  def parkedOptions(state: OathState, powers: WalkerPowers,
      phasePowers: PhasePowers = PhasePowers.empty)(using munit.Location)
      : Vector[DecisionOption] =
    ParkedNode.of(state, FirstGameSetupFixture.catalog, powers,
        phasePowers) match
      case Left(reason) => munit.Assertions.fail(reason.toString)
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide.query match
        case DecisionQuery.ChooseOne(options, _) => options
        case _ => Vector.empty
      case Right(_) => Vector.empty

  /** The answer that plays the card `way` at the placement `state` is parked
    * on. */
  def parkedAnswer(state: OathState, way: PlayedAs, powers: WalkerPowers,
      phasePowers: PhasePowers = PhasePowers.empty)(using munit.Location)
      : DecisionAnswer.ChooseOneAnswer =
    DecisionAnswer.ChooseOneAnswer(
      pick(parkedOptions(state, powers, phasePowers), way))

  /** The card `option` discards to make room, read through any
    * availability, price or badge mark. */
  def replaces(option: DecisionOption): Option[CardId] = option match
    case DecisionOption.Replacing(_, card) => Some(card)
    case DecisionOption.Unavailable(inner, _) => replaces(inner)
    case DecisionOption.Priced(inner, _) => replaces(inner)
    case DecisionOption.Badged(inner, _) => replaces(inner)
    case _ => None

  /** The option of `options` that plays the card `way` in the place of
    * `card`, whether or not it may be chosen. */
  def findReplacing(options: Vector[DecisionOption], way: PlayedAs,
      card: CardId): Option[DecisionOptionRef] =
    options.find(option => CardPlayProcedure.playedAs(option.ref)
      .contains(way) && replaces(option).contains(card)).map(_.ref)

  private def chooseOne(decide: Decide)(using munit.Location)
      : Vector[DecisionOption] = decide.query match
    case DecisionQuery.ChooseOne(options, _) => options
    case other => munit.Assertions.fail(s"not a choose-one: $other")
