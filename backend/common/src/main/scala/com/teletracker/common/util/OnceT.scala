package com.teletracker.common.util

object OnceT {

  /**
    * Returns a function that runs `fn` the first time it's called and then never
    * again.  If the returned function is invoked while another thread is running
    * `fn` for the first (and only) time, the interloping thread blocks until
    * `fn` has finished.
    *
    * If `fn` throws, it may be run more than once.
    */
  def apply[T <: AnyRef](fn: => T): () => T = {
    new Function0[T] {
      // we can't use method synchronization because of https://issues.scala-lang.org/browse/SI-9814
      // this `val self = this` indirection convinces the scala compiler to use object
      // synchronization instead of method synchronization
      val self = this
      @volatile var x: T = _
      def apply(): T =
        if (x eq null) {
          self.synchronized {
            if (x eq null) {
              x = fn
            }

            x
          }
        } else {
          x
        }
    }
  }
}
