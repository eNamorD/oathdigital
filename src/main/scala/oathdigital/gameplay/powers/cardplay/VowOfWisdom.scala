package oathdigital.gameplay.powers.cardplay

import oathdigital.catalog.{AdviserOnly, Denizen, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OperationRewrite, PowerCtx, Transform}
import oathdigital.model._

object VowOfWisdomCard extends Denizen(DenizenId("203"), "Vow of Wisdom",
    Suit.Arcane) with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.vow-of-wisdom"),
    persistent = true, cost = Cost.free,
    text = "You **must** gain [secret] instead of [favor] when you play " +
      "to a site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Vow of Wisdom (card 203, adviser-only), a persistent rule of a faceup
  * adviser: "You must gain secret instead of favor when you play to a
  * site."
  *
  * While its holder has it faceup, a rewrite at `SearchPlayAdviser` follows
  * the holder's `Play` to a site with a gain of 1 secret, and drops the
  * play's favor gain. The secret is gained whether or not the card's suit
  * bank holds favor. There is no choice. A gain another power gives for
  * the play, a WHEN PLAYED power's or Welcoming Party's, sits under its own
  * window, so the rewrite leaves it alone: "instead of" replaces only the
  * play's own favor.
  *
  * It writes no line: the play's generic line reads "Gained 1 secret".
  */
case object VowOfWisdom extends ContributingPower:
  val cardId: DenizenId = VowOfWisdomCard.id
  val id: PowerId = VowOfWisdomCard.power.id
  val Secrets: Int = 1
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, children) =>
      OperationRewrite.ops(ctx, children)((_, _) => {
        case play @ Play(_, _, Location.Site(_), _, _) =>
          Vector[CoreOperation](play,
            Gain.Secrets(ctx.activePlayer, Secrets))
        case Gain.Favor(player, _, _) if player == ctx.activePlayer =>
          Vector.empty
      }))))

  override def applicable(ctx: PowerCtx): Boolean =
    ctx.state.game.current.players.find(_.player == ctx.activePlayer)
      .exists(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      })
