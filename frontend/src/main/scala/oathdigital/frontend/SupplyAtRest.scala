package oathdigital.frontend

/** The text behind a board's Supply figure: what the bag holds, the Rest
  * table with the current band marked, and the Supply the next Rest
  * returns. The numbers are the server's (`PlayerBoard`), so this never
  * re-derives a rule; it only says it.
  */
private[frontend] object SupplyAtRest:
  def lines(role: String, board: PlayerBoard, bands: Vector[SupplyBand],
      maximum: Int): Vector[String] =
    if !role.equalsIgnoreCase("exile") then
      Vector("Citizens copy the Chancellor's Supply.")
    else
      val bag = s"Warbands in bag: ${board.bankedWarbands} " +
        s"(${board.printedWarbands} printed, ${board.warbands} on board, " +
        s"${board.siteWarbands} at sites)."
      val table = bands.sortBy(_.from).map { band =>
        val range = band.to.fold(s"${band.from} or more warbands")(to =>
          s"${band.from}–$to warbands")
        val now = band.from <= board.bankedWarbands &&
          band.to.forall(board.bankedWarbands <= _)
        s"$range: Supply ${band.supply}" + (if now then " (now)" else "")
      }
      val rest = s"Next Rest: ${board.restSupplyBase} from the band, " +
        s"plus unspent Supply, capped at $maximum."
      (bag +: table) :+ rest
