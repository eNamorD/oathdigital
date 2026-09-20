package oathdigital.frontend

import oathdigital.protocol.{GameIntent => Intent, _}

/** Legacy-shaped test builders only; returned values are shared actorless DTOs.
  * Keeping actor parameters here makes existing assertions prove that identity
  * never reaches the encoded payload.
  */
private[frontend] object GameCommand {
  def PlacePawn(actor: String, site: String) = Intent.PlacePawn(site)
  // Take Wealth starts on the generic walker (batch-1 Task 7): the resource
  // rides the start selection as a button, not an intent of its own.
  def TakeWealth(actor: String, resource: String) =
    Intent.StartWalker("take-wealth", Vector.empty,
      Vector(WalkerStartArgWire("button", resource)))
  def EndWake(actor: String) = Intent.EndWake
  def BeginRest(actor: String) = Intent.BeginRest
  def FinishRest(actor: String) = Intent.FinishRest
  // Travel starts on the generic walker (batch-1 Task 5): its destination
  // rides the start selection, not an intent of its own.
  def Travel(actor: String, site: String) = Intent.StartWalker("travel",
    Vector.empty, Vector(WalkerStartArgWire("site", site)))
  def BeginSearch(actor: String, source: String, region: Option[String]) =
    Intent.StartWalker("search", Vector.empty, Vector(WalkerStartArgWire(
      "button", region.fold("search:world")(r =>
        s"search:regional-discard:$r"))))
  def PeekSiteRelics(actor: String) = Intent.PeekSiteRelics
  def MoveWarbands(actor: String, toSite: Boolean, amount: Int) = Intent.MoveWarbands(toSite, amount)
  def ResolveCardDecision(actor: String, id: String, value: DecisionResolution.Value) = Intent.ResolveCardDecision(id, value.intent)
  def StartWalker(actor: String, action: String, modifiers: Vector[String] = Vector.empty) =
    Intent.StartWalker(action, modifiers)
  def RollWalker(actor: String, pool: String) = Intent.RollWalker(pool)
  def ResolveWalker(actor: String, id: String, payload: DecisionAnswerWire) =
    Intent.ResolveWalker(id, payload)
}

private[frontend] object DecisionResolution {
  sealed trait Value { def intent: oathdigital.protocol.DecisionResolution }
  final case class StartingAdviser(id: String) extends Value { val intent = oathdigital.protocol.DecisionResolution.StartingAdviser(id) }
}
