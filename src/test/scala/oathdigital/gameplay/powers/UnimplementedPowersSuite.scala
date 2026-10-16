package oathdigital.gameplay.powers

import java.nio.file.{Files, Path, Paths}
import oathdigital.catalog.CatalogHandlerInventory
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model.PowerId

/** Every printed power of the catalog that `PowerImplementationStatus`
  * reports unimplemented, exactly. The status reads only the registries a
  * power is wired into, so a power that runs through some other seam reads
  * as unimplemented and nothing fails. This fixture makes each change to
  * the status a reviewed diff: a slice that implements a power names the
  * line it expects to drop, and a line that stays shows the power is not
  * wired where the status looks.
  *
  * `UNIMPLEMENTED_SNAPSHOT=write` rewrites the file instead of comparing; a
  * rewritten snapshot is reviewed line by line before it is committed.
  */
class UnimplementedPowersSuite extends munit.FunSuite:
  private val file: Path =
    Paths.get("src/test/resources/powers/unimplemented.txt")
  private val writing =
    sys.env.get("UNIMPLEMENTED_SNAPSHOT").contains("write")

  private def rendered: String =
    val implemented = PowerImplementationStatus.implemented(catalog)
    CatalogHandlerInventory.handlerIds(catalog)
      .filterNot(id => implemented(PowerId(id)))
      .sorted.mkString("", "\n", "\n")

  test("exactly the powers the snapshot records are unimplemented"):
    if writing then
      Files.createDirectories(file.getParent)
      Files.writeString(file, rendered)
    else
      assert(Files.exists(file), s"missing snapshot $file; run with " +
        "UNIMPLEMENTED_SNAPSHOT=write and review it")
      assertNoDiff(rendered, Files.readString(file))
