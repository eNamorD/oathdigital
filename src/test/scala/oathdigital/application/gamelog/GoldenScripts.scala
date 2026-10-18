package oathdigital.application.gamelog

/** Every script with a golden file, for the suites that hold for each.
  * `LogScripts` is at its size limit, so the registry lives here and the
  * script files contribute their own vectors. */
object GoldenScripts:
  val named: Vector[(String, () => Script)] =
    LogScripts.named ++ CampaignLogScripts.named

  def all: Vector[Script] = named.map(_._2())
