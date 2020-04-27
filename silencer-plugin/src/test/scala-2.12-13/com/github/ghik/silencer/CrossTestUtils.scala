package com.github.ghik.silencer

import scala.tools.nsc.Settings

object CrossTestUtils {
  def enableUnusedImports(settings: Settings): Unit = {
    settings.warnUnused.enable(settings.UnusedWarnings.Imports)
  }
}
