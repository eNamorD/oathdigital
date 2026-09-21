package oathdigital.model

final case class NegotiationTransfer(
    recipient: PlayerId,
    favor: Int,
    relics: Vector[RelicId]
) {
  require(favor >= 0, "Negotiation favor must be non-negative")
  require(relics.distinct.size == relics.size,
    "Negotiation relic transfers must be distinct")
}

sealed trait NegotiationDisclosureRef extends Product with Serializable
object NegotiationDisclosureRef {
  final case class Adviser(owner: PlayerId, card: WorldCardId)
      extends NegotiationDisclosureRef
  final case class HeldRelic(owner: PlayerId, relic: RelicId)
      extends NegotiationDisclosureRef
  final case class SiteRelic(site: SiteId, relic: RelicId)
      extends NegotiationDisclosureRef
}
final case class NegotiationDisclosure(
    recipient: PlayerId,
    information: NegotiationDisclosureRef
)
final case class NegotiationTerms(
    transfers: Vector[NegotiationTransfer] = Vector.empty,
    disclosures: Vector[NegotiationDisclosure] = Vector.empty
) {
  require(transfers.map(_.recipient).distinct.size == transfers.size,
    "Negotiation transfers must have one row per recipient")
  require(disclosures.distinct.size == disclosures.size,
    "Negotiation disclosures must be distinct")
}

/** What one author may still put into a deal, computed from live state each
  * time the deal is asked: the other participants they may offer to, the
  * favor they hold, the relics they hold, and the information they may
  * currently promise to disclose (their facedown advisers and held relics,
  * and site relics they know that are still at their site).
  */
final case class NegotiationBounds(
    recipients: Vector[PlayerId],
    maxFavor: Int,
    relics: Vector[RelicId],
    disclosures: Vector[NegotiationDisclosureRef]
) {
  require(maxFavor >= 0, "negotiation favor bound must be non-negative")
}
