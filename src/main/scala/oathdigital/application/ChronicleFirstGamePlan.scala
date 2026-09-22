package oathdigital.application

import oathdigital.gameplay.setup.FirstGameRulesData
import oathdigital.model._

/**
 * Derives the concrete per-game deal (2026-09-21 Chronicle design, slice 2):
 * the five fixed Vision identities spliced into `chronicle.worldDeck`'s
 * 10+2/15+3 packets, dealt after `config.participants.size * 3 + 6` cards
 * are set aside for hands and the seeded regional discards. Total, unlike
 * slice 1's bridge: Setup itself validates card counts against the live
 * catalog (`GameStartRules`); this is pure arithmetic over whatever
 * `chronicle`/`config` it is given, correct-by-construction even when the
 * result later fails that validation (e.g. too few cards).
 */
object ChronicleFirstGamePlan {
  def dealOrder(chronicle: Chronicle, config: FirstGameBootstrapConfig)
      : SetupOrders = {
    val dealt = 6 + config.participants.size * 3
    val remaining = chronicle.worldDeck.drop(dealt)
    val worldDeckOrder: Vector[WorldCardId] =
      remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
        remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
        remaining.drop(25)
    SetupOrders(config.participants, config.firstPlayer, worldDeckOrder,
      chronicle.relicDeck)
  }
}
