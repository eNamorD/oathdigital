package oathdigital.gameplay.actions.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.model._

/** Trade on the walker. The start selection is one resource button, `favor`
  * or `secret`, naming what the actor gains: Trade for favor pays one secret
  * and yields favor of the card's suit; Trade for secrets pays two favor, one
  * of them burnt, and yields secrets. Both spend one Supply, and both ask for
  * their modifiers in the `trade.modifiers` decision, then draw on the
  * `trade.source` decision.
  */
object TradeProcedure:
  val decisionId: String = "trade.source"

  private def kind(resource: TradeResource): EconomyTree.Kind = resource match
    case TradeResource.Favor => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for favor", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(secret = 1),
      (actor, source, matching, _) =>
        Some(Gain.Favor(actor, source.suit, 1 + matching)),
      selection = selection(resource))
    case TradeResource.Secret => EconomyTree.Kind(decisionId,
      "Choose a card to Trade for secrets", PowerWindow.TradeModifierSelection,
      "Start Trade", PowerWindow.TradeActionEligibility,
      PowerWindow.TradeSourceSelection, PowerWindow.TradeCost,
      PowerWindow.TradeGain, Cost(favor = 1, favorBurnt = 1),
      (actor, _, matching, _) =>
        Option.when(matching > 0)(Gain.Secrets(actor, matching)),
      selection = selection(resource))

  def resourceOf(args: Vector[DecisionOptionRef])
      : Either[OathViolation, TradeResource] = args match
    case Vector(DecisionOptionRef.Button("favor")) => Right(TradeResource.Favor)
    case Vector(DecisionOptionRef.Button("secret")) => Right(TradeResource.Secret)
    case other => Left(OathViolation.InvalidEventOrder(
      "trade takes exactly one resource button, favor or secret, as its start " +
        s"selection, got ${other.map(ref => s"${ref.kind}/${ref.wireId}")
          .mkString(", ")}"))

  /** The start selection that trades for `resource`, as `resourceOf` reads
    * it. */
  def selection(resource: TradeResource): Vector[DecisionOptionRef] =
    resource match
      case TradeResource.Favor => Vector(DecisionOptionRef.Button("favor"))
      case TradeResource.Secret => Vector(DecisionOptionRef.Button("secret"))

  def build(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).flatMap(resource =>
      EconomyTree.build(catalog, state, actor, kind(resource)))

  def rebuild(catalog: ExecutableCatalog, state: ReadyGame, actor: PlayerId,
      args: Vector[DecisionOptionRef]): Either[OathViolation, Operation] =
    resourceOf(args).map(resource =>
      EconomyTree.tree(catalog, state, actor, kind(resource)))
