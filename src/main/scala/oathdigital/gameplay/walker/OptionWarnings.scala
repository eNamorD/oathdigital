package oathdigital.gameplay.walker

import oathdigital.model.{Decide, DecisionOption}

/** The warnings the offered powers give a choose-one's options (follow-up
  * actions design, ruling 10). The first power's warning wins. An option
  * that cannot be chosen keeps only its own reason. */
object OptionWarnings:
  def marked(powers: WalkerPowers, decide: Decide,
      options: Vector[DecisionOption]): Vector[DecisionOption] =
    options.map(option =>
      if !option.available then option
      else powers.powers.iterator.flatMap(_.warning(decide, option.ref))
        .nextOption().fold(option)(DecisionOption.Warned(option, _)))
