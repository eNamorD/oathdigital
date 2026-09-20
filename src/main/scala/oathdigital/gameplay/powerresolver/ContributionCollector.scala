package oathdigital.gameplay.powerresolver

import oathdigital.model.PowerId
import oathdigital.model.PowerWindow

/** The result of one gather at a hooked node: transforms, restrictions and
  * option restrictions declared by the surviving powers, tagged with the power that declared
  * each, plus the deterministic order those powers were resolved in.
  */
final case class GatheredContributions(
    transforms: Vector[(PowerId, Transform)],
    restrictions: Vector[(PowerId, Restriction)],
    order: Vector[PowerId],
    optionRestrictions: Vector[(PowerId, OptionRestriction)] = Vector.empty
)

/** Turns "which powers hook this window" into "which transforms, restrictions
  * and option restrictions apply, in what order" (spec decision 10, steps a-d; the
  * walker executes leaves and records the event -- steps e-f -- separately).
  *
  * Pure: reads only the `PowerCtx` values `ctxFor` produces and the powers
  * it is handed. No state mutation, no catalog lookups, no walker imports.
  */
object ContributionCollector {

  def gather(
      window: PowerWindow,
      powers: Vector[ContributingPower],
      ctxFor: ContributingPower => PowerCtx
  ): GatheredContributions = {
    // Step 1: discovery -- keep powers whose contributions declare this window.
    val discovered = powers.filter(_.contributions.contains(window))

    // Step 2: applicability -- keep those applicable in their own context.
    val applicable = discovered.filter(power => power.applicable(ctxFor(power)))

    // Step 3: named ignore, one pass, no transitivity. Every applicable
    // power's votes are collected against the full applicable set before
    // any power is dropped, so a dropped power's votes still count.
    val ignored: Set[PowerId] = applicable.flatMap { power =>
      applicable.filter(power.shouldIgnore).map(_.id)
    }.toSet
    val survivors = applicable.filterNot(power => ignored.contains(power.id))

    // Step 4: deterministic order.
    val ordered = survivors.sortBy(ContributingPower.sortKey)

    // Step 5: split each survivor's contributions at this window into
    // transforms, restrictions and option restrictions, tagged with the owning power id, and
    // record each survivor's id once, in order.
    val transforms = Vector.newBuilder[(PowerId, Transform)]
    val restrictions = Vector.newBuilder[(PowerId, Restriction)]
    val optionRestrictions = Vector.newBuilder[(PowerId, OptionRestriction)]

    ordered.foreach { power =>
      power.contributions(window).foreach {
        case transform: Transform => transforms += power.id -> transform
        case restriction: Restriction => restrictions += power.id -> restriction
        case option: OptionRestriction => optionRestrictions += power.id -> option
      }
    }

    GatheredContributions(
      transforms = transforms.result(),
      restrictions = restrictions.result(),
      order = ordered.map(_.id),
      optionRestrictions = optionRestrictions.result()
    )
  }
}
