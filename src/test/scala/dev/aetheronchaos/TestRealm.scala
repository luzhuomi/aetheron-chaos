package dev.aetheronchaos

import java.util.Date

import net.minecraft.{SharedConstants, WorldVersion}
import net.minecraft.server.Bootstrap
import net.minecraft.server.packs.PackType
import net.minecraft.world.level.storage.DataVersion

/**
 * Makes Minecraft classes usable in unit tests: set a game version, then run the same
 * vanilla bootstrap the dedicated server runs at startup. Registries load; no world,
 * no server loop. Call TestRealm.boot() once per suite - it is idempotent.
 */
object TestRealm:
  private var ready = false

  def boot(): Unit =
    if ready then return
    ready = true
    SharedConstants.setVersion(new WorldVersion:
      override def getDataVersion: DataVersion = new DataVersion(3953, "main")
      override def getId: String = "1.21"
      override def getName: String = "aetheron-chaos-test"
      override def getProtocolVersion: Int = 767
      override def getPackVersion(packType: PackType): Int = 48
      override def getBuildTime: Date = Date.from(java.time.Instant.EPOCH)
      override def isStable: Boolean = true
    )
    Bootstrap.bootStrap()
