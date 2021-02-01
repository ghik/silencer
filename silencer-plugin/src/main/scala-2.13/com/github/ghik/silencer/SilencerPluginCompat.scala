package com.github.ghik.silencer

trait SilencerPluginCompat { this: SilencerPlugin =>
  import global._

  protected object MaybeNamedArg {
    def unapply(tree: Tree): OptRef[Tree] = tree match {
      case Assign(_, rhs) => OptRef(rhs)
      case _ => OptRef(tree)
    }
  }

}
