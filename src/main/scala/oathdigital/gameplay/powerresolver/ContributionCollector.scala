package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerId
import oathdigital.model.PowerWindow

/** The result of one gather at a hooked node: transforms, restrictions, option
  * restrictions and offers declared by the surviving powers, tagged with the
  * power that declared each, plus the deterministic order those powers were
  * resolved in.
  */
final case class GatheredContributions(
    transforms: Vector[(PowerId, Transform)],
    restrictions: Vector[(PowerId, Restriction)],
    order: Vector[PowerId],
    optionRestrictions: Vector[(PowerId, OptionRestriction)] = Vector.empty,
    offers: Vector[(PowerId, Offer)] = Vector.empty,
    rerolls: Vector[(PowerId, OfferReroll)] = Vector.empty
)

/** Turns "which powers hook this window" into "which transforms, restrictions
  * and option restrictions apply, in what order" (spec decision 10, steps a-d; the
  * walker executes leaves and records the event -- steps e-f -- separately).
  *
  * Pure: reads only the `PowerCtx` values `ctxFor` produces and the index it
  * is handed. No state mutation, no catalog lookups, no walker imports.
  */
object ContributionCollector:

  def gather(
      window: PowerWindow,
      index: PowerWindowIndex,
      ctxFor: ContributingPower => PowerCtx
  ): GatheredContributions =
    // Step 1: discovery -- the powers whose contributions declare this
    // window, with those contributions, from the index.
    val discovered = index.at(window)

    // Step 2: applicability -- keep those applicable in their own context.
    val applicable = discovered.filter(hooked =>
      hooked.power.applicable(ctxFor(hooked.power)))

    // Step 3: named ignore, one pass, no transitivity. Every applicable
    // power's votes are collected against the full applicable set before
    // any power is dropped, so a dropped power's votes still count.
    val ignored: Set[PowerId] = applicable.flatMap { hooked =>
      applicable.filter(other =>
        hooked.power.ignores(ctxFor(hooked.power), other.power))
        .map(_.power.id)
    }.toSet
    val survivors = applicable.filterNot(hooked =>
      ignored.contains(hooked.power.id))

    // Step 4: deterministic order: by the earliest fold phase of each
    // power's transforms here, then by sort key. Without a `Late`
    // transform this is the sort key alone.
    val ordered = survivors.sortBy(hooked =>
      (earliestPhase(hooked), ContributingPower.sortKey(hooked.power)))

    // Step 5: split each survivor's contributions at this window into
    // transforms, restrictions and option restrictions, tagged with the owning power id, and
    // record each survivor's id once, in order.
    val transforms = Vector.newBuilder[(PowerId, Transform)]
    val restrictions = Vector.newBuilder[(PowerId, Restriction)]
    val optionRestrictions = Vector.newBuilder[(PowerId, OptionRestriction)]
    val offers = Vector.newBuilder[(PowerId, Offer)]
    val rerolls = Vector.newBuilder[(PowerId, OfferReroll)]

    ordered.foreach { hooked =>
      val id = hooked.power.id
      hooked.contributions.foreach:
        case transform: Transform => transforms += id -> transform
        case restriction: Restriction => restrictions += id -> restriction
        case option: OptionRestriction => optionRestrictions += id -> option
        case offer: Offer => offers += id -> offer
        case reroll: OfferReroll => rerolls += id -> reroll
    }

    GatheredContributions(
      // Stable, so priority still orders the transforms within a phase.
      transforms = transforms.result().sortBy(_._2.phase.ordinal),
      restrictions = restrictions.result(),
      order = ordered.map(_.power.id),
      optionRestrictions = optionRestrictions.result(),
      offers = offers.result(),
      rerolls = rerolls.result()
    )

  /** The earliest phase of `hooked`'s transforms at the window, as its
    * ordinal; `Normal` when it has none. */
  private def earliestPhase(hooked: HookedPower): Int =
    hooked.contributions.collect { case transform: Transform =>
      transform.phase.ordinal }.minOption
      .getOrElse(FoldPhase.Normal.ordinal)
