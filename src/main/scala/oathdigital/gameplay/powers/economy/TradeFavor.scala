package oathdigital.gameplay.powers.economy

import oathdigital.model.{CoreOperation, Defer, DeferralKey, Gain, ReadyGame}

/** The favor a Trade's trader gains, held under one key as it runs, for a
  * power that counts it (Secret Signal). The counting power records the
  * Trade's own gain; Moving Market records its own. The walker drops what
  * is left when the action ends.
  */
private[economy] object TradeFavor:
  val key: DeferralKey = DeferralKey("trade.favor-gained")

  /** `gain` recorded at what its bank can give now: run before the gain.
    * Nothing when the bank is empty, since a gain of none is no operation. */
  def record(ready: ReadyGame, gain: Gain.Favor): Vector[CoreOperation] =
    val available = gain.amount min ready.banks.favor.getOrElse(gain.suit, 0)
    Option.when(available > 0)(
      Defer(key, Vector(gain.copy(amount = available)))).toVector
