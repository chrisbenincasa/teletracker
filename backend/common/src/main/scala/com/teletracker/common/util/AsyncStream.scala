package com.teletracker.common.util

import java.util.concurrent.ScheduledExecutorService
import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * A representation of a lazy (and possibly infinite) sequence of asynchronous
  * values. We provide combinators for non-blocking computation over the sequence
  * of values.
  *
  * It is composable with Future, Seq and Option.
  *
  * {{{
  * val ids = Seq(123, 124, ...)
  * val users = fromSeq(ids).flatMap(id => fromFuture(getUser(id)))
  *
  * // Or as a for-comprehension...
  *
  * val users = for {
  *   id <- fromSeq(ids)
  *   user <- fromFuture(getUser(id))
  * } yield user
  * }}}
  *
  * All of its operations are lazy and don't force evaluation, unless otherwise
  * noted.
  *
  * The stream is persistent and can be shared safely by multiple threads.
  */
sealed abstract class AsyncStream[+A] {
  import AsyncStream._

  /**
    * Returns true if there are no elements in the stream.
    */
  def isEmpty(implicit executionContext: ExecutionContext): Future[Boolean] =
    this match {
      case Empty      => Future.successful(true)
      case Embed(fas) => fas.flatMap(_.isEmpty)
      case _          => Future.successful(false)
    }

  /**
    * Returns the head of this stream if not empty.
    */
  def head(implicit executionContext: ExecutionContext): Future[Option[A]] =
    this match {
      case Empty          => Future.successful(None)
      case FromFuture(fa) => fa.map(Some(_))
      case Cons(fa, _)    => fa.map(Some(_))
      case Embed(fas)     => fas.flatMap(_.head)
    }

  /**
    * Note: forces the first element of the tail.
    */
  def tail(
    implicit executionContext: ExecutionContext
  ): Future[Option[AsyncStream[A]]] = this match {
    case Empty | FromFuture(_) => Future.successful(None)
    case Cons(_, more)         => Future.successful(Some(more()))
    case Embed(fas)            => fas.flatMap(_.tail)
  }

  /**
    * The head and tail of this stream, if not empty. Note the tail thunk which
    * preserves the tail's laziness.
    *
    * {{{
    * empty.uncons     == Future.None
    * (a +:: m).uncons == Future.value(Some(a, () => m))
    * }}}
    */
  def uncons(
    implicit executionContext: ExecutionContext
  ): Future[Option[(A, () => AsyncStream[A])]] = this match {
    case Empty          => Future.successful(None)
    case FromFuture(fa) => fa.map(a => Some((a, () => empty)))
    case Cons(fa, more) => fa.map(a => Some((a, more)))
    case Embed(fas)     => fas.flatMap(_.uncons)
  }

  /**
    * Note: forces the stream. For infinite streams, the future never resolves.
    */
  def foreach(
    f: A => Unit
  )(implicit executionContext: ExecutionContext
  ): Future[Unit] =
    foldLeft(()) { (_, a) =>
      f(a)
    }

  /**
    * Execute the specified effect as each element of the resulting
    * stream is demanded. This method does '''not''' force the
    * stream. Since the head of the stream is not lazy, the effect will
    * happen to the first item in the stream (if any) right away.
    *
    * The effects will occur as the '''resulting''' stream is demanded
    * and will not occur if the original stream is demanded.
    *
    * This is useful for e.g. counting the number of items that were
    * consumed from a stream by a consuming process, regardless of
    * whether the entire stream is consumed.
    */
  def withEffect(
    f: A => Unit
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    map { a =>
      f(a); a
    }

  /**
    * Maps each element of the stream to a Future action, resolving them from
    * head to tail. The resulting Future completes when the action completes
    * for the last element.
    *
    * Note: forces the stream. For infinite streams, the future never resolves.
    */
  def foreachF(
    f: A => Future[Unit]
  )(implicit executionContext: ExecutionContext
  ): Future[Unit] =
    foldLeftF(()) { (_, a) =>
      f(a)
    }

  /**
    * Map over this stream with the given concurrency. The items will
    * likely not be processed in order. `concurrencyLevel` specifies an
    * "eagerness factor", and that many actions will be started when this
    * method is called. Forcing the stream will yield the results of
    * completed actions, and will block if none of the actions has yet
    * completed.
    *
    * This method is useful for speeding up calculations over a stream
    * where executing the actions in order is not important. To implement
    * a concurrent fold, first call `mapConcurrent` and then fold that
    * stream. Similarly, concurrent `foreachF` can be achieved by
    * applying `mapConcurrent` and then `foreach`.
    *
    * @param concurrencyLevel: How many actions to execute concurrently. This
    *   many actions will be started and "raced", with the winner being
    *   the next item available in the stream.
    */
  def mapConcurrent[B](
    concurrencyLevel: Int
  )(
    f: A => Future[B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] = {
    if (concurrencyLevel == 1) {
      mapF(f)
    } else if (concurrencyLevel < 1) {
      throw new IllegalArgumentException(
        s"concurrencyLevel must be at least one. got: $concurrencyLevel"
      )
    } else {
      this match {
        case Empty => Empty
        case _ =>
          embed(AsyncStream.mapConcStep(concurrencyLevel, f, Nil, () => this))
      }
    }
  }

  /**
    * Given a predicate `p`, returns the longest prefix (possibly empty) of this
    * stream that satisfes `p`:
    *
    * {{{
    * AsyncStream(1, 2, 3, 4, 1).takeWhile(_ < 3) = AsyncStream(1, 2)
    * AsyncStream(1, 2, 3).takeWhile(_ < 5) = AsyncStream(1, 2, 3)
    * AsyncStream(1, 2, 3).takeWhile(_ < 0) = AsyncStream.empty
    * }}}
    */
  def takeWhile(
    p: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    this match {
      case Empty => empty
      case FromFuture(fa) =>
        Embed(fa.map { a =>
          if (p(a)) this else empty
        })
      case Cons(fa, more) =>
        Embed(fa.map { a =>
          if (p(a)) Cons(fa, () => more().takeWhile(p))
          else empty
        })
      case Embed(fas) => Embed(fas.map(_.takeWhile(p)))
    }

  /**
    * Given a predicate `p` returns the suffix remaining after `takeWhile(p)`:
    *
    * {{{
    * AsyncStream(1, 2, 3, 4, 1).dropWhile(_ < 3) = AsyncStream(3, 4, 1)
    * AsyncStream(1, 2, 3).dropWhile(_ < 5) = AsyncStream.empty
    * AsyncStream(1, 2, 3).dropWhile(_ < 0) = AsyncStream(1, 2, 3)
    * }}}
    */
  def dropWhile(
    p: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    this match {
      case Empty => empty
      case FromFuture(fa) =>
        Embed(fa.map { a =>
          if (p(a)) empty else this
        })
      case Cons(fa, more) =>
        Embed(fa.map { a =>
          if (p(a)) more().dropWhile(p)
          else Cons(fa, () => more())
        })
      case Embed(fas) => Embed(fas.map(_.dropWhile(p)))
    }

  /**
    * Concatenates two streams.
    *
    * Note: If this stream is infinite, we never process the concatenated
    * stream; effectively: m ++ k == m.
    *
    * @see [[concat]] for Java users.
    */
  def ++[B >: A](
    that: => AsyncStream[B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    concatImpl(() => that)

  protected def concatImpl[B >: A](
    that: () => AsyncStream[B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    this match {
      case Empty => that()
      case FromFuture(fa) =>
        Cons(fa, that)
      case Cons(fa, more) =>
        Cons(fa, () => more().concatImpl(that))
      case Embed(fas) =>
        Embed(fas.map(_.concatImpl(that)))
    }

  /**
    * @see ++
    */
  def concat[B >: A](
    that: => AsyncStream[B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] = ++(that)

  /**
    * Map a function `f` over the elements in this stream and concatenate the
    * results.
    */
  def flatMap[B](
    f: A => AsyncStream[B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    this match {
      case Empty          => empty
      case FromFuture(fa) => Embed(fa.map(f))
      case Cons(fa, more) => Embed(fa.map(f)) ++ more().flatMap(f)
      case Embed(fas)     => Embed(fas.map(_.flatMap(f)))
    }

  /**
    * `stream.flatMapOption(f)` is the stream obtained by applying `f` to all elements of stream
    * and then removing results that evaluate to `None`
    */
  def flatMapOption[B](
    f: A => Option[B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    flatMap(f(_) match {
      case None    => empty
      case Some(b) => of(b)
    })

  /**
    * `stream.collect(pf)` is the stream obtained by applying PartialFunction `pf` to items of the stream
    * that are in `pf`'s domain.
    */
  def collect[B](
    pf: PartialFunction[A, B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] = {
    flatMapOption(pf.lift)
  }

  /**
    * `stream.map(f)` is the stream obtained by applying `f` to each element of
    * `stream`.
    */
  def map[B](
    f: A => B
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    this match {
      case Empty          => empty
      case FromFuture(fa) => FromFuture(fa.map(f))
      case Cons(fa, more) => Cons(fa.map(f), () => more().map(f))
      case Embed(fas)     => Embed(fas.map(_.map(f)))
    }

  /**
    * Returns a stream of elements that satisfy the predicate `p`.
    *
    * Note: forces the stream up to the first element which satisfies the
    * predicate. This operation may block forever on infinite streams in which
    * no elements match.
    */
  def filter(
    p: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    this match {
      case Empty => empty
      case FromFuture(fa) =>
        Embed(fa.map { a =>
          if (p(a)) this else empty
        })
      case Cons(fa, more) =>
        Embed(fa.map { a =>
          if (p(a)) Cons(fa, () => more().filter(p))
          else more().filter(p)
        })
      case Embed(fas) => Embed(fas.map(_.filter(p)))
    }

  /**
    * @see filter
    */
  def withFilter(
    f: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] = filter(f)

  /**
    * Returns the prefix of this stream of length `n`, or the stream itself if
    * `n` is larger than the number of elements in the stream.
    */
  def take(
    n: Int
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    if (n < 1) empty
    else
      this match {
        case Empty         => empty
        case FromFuture(_) => this
        // If we don't handle this case specially, then the next case
        // would return a stream whose full evaluation will evaulate
        // cons.tail.take(0), forcing one more effect than necessary.
        case Cons(fa, _) if n == 1 => FromFuture(fa)
        case Cons(fa, more)        => Cons(fa, () => more().take(n - 1))
        case Embed(fas)            => Embed(fas.map(_.take(n)))
      }

  def safeTake(
    n: Int
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    if (n < 0) this else take(n)

  /**
    * Returns the suffix of this stream after the first `n` elements, or
    * `AsyncStream.empty` if `n` is larger than the number of elements in the
    * stream.
    *
    * Note: this forces all of the intermediate dropped elements.
    */
  def drop(
    n: Int
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    if (n < 1) this
    else
      this match {
        case Empty | FromFuture(_) => empty
        case Cons(_, more)         => more().drop(n - 1)
        case Embed(fas)            => Embed(fas.map(_.drop(n)))
      }

  /**
    * Constructs a new stream by mapping each element of this stream to a
    * Future action, evaluated from head to tail.
    */
  def mapF[B](
    f: A => Future[B]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    this match {
      case Empty          => empty
      case FromFuture(fa) => FromFuture(fa.flatMap(f))
      case Cons(fa, more) => Cons(fa.flatMap(f), () => more().mapF(f))
      case Embed(fas)     => Embed(fas.map(_.mapF(f)))
    }

  def delayedMapF[B](
    f: A => Future[B],
    perElementWait: FiniteDuration,
    scheduledService: ScheduledExecutorService
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] = {
    def waitAfter(value: B): Future[B] = {
      val promise = Promise[B]
      scheduledService.schedule(new Runnable {
        override def run(): Unit = promise.success(value)
      }, perElementWait.length, perElementWait.unit)
      promise.future
    }

    val transform: Try[B] => Future[B] = {
      case Success(value)     => waitAfter(value)
      case Failure(exception) => Future.failed(exception)
    }

    this match {
      case Empty          => empty
      case FromFuture(fa) => FromFuture(fa.flatMap(f).transformWith(transform))
      case Cons(fa, more) =>
        Cons(
          fa.flatMap(f).transformWith(transform),
          () => more().delayedMapF(f, perElementWait, scheduledService)
        )
      case Embed(fas) =>
        Embed(fas.map(_.delayedMapF(f, perElementWait, scheduledService)))
    }
  }

  /**
    * Similar to foldLeft, but produces a stream from the result of each
    * successive fold:
    *
    * {{{
    * AsyncStream(1, 2, ...).scanLeft(z)(f) == z +:: f(z, 1) +:: f(f(z, 1), 2) +:: ...
    * }}}
    *
    * Note that for an `AsyncStream as`:
    *
    * {{{
    * as.scanLeft(z)(f).last == as.foldLeft(z)(f)
    * }}}
    *
    * The resulting stream always begins with the initial value `z`,
    * not subject to the fate of the underlying future, i.e.:
    *
    * {{{
    * val never = AsyncStream.fromFuture(Future.never)
    * never.scanLeft(z)(f) == z +:: never // logical equality
    * }}}
    */
  def scanLeft[B](
    z: B
  )(
    f: (B, A) => B
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    this match {
      case Embed(fas) =>
        Cons(Future.successful(z), () => Embed(fas.map(_.scanLeftEmbed(z)(f))))
      case Empty => FromFuture(Future.successful(z))
      case FromFuture(fa) =>
        Cons(Future.successful(z), () => FromFuture(fa.map(f(z, _))))
      case Cons(fa, more) =>
        Cons(
          Future.successful(z),
          () => Embed(fa.map(a => more().scanLeft(f(z, a))(f)))
        )
    }

  /**
    * Helper method used to avoid scanLeft being one behind in case of Embed AsyncStream.
    *
    * scanLeftEmbed, unlike scanLeft, does not return the initial value `z` and is there to
    * prevent the Embed case from returning duplicate initial `z` values for scanLeft.
    *
    */
  private def scanLeftEmbed[B](
    z: B
  )(
    f: (B, A) => B
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[B] =
    this match {
      case Embed(fas) => Embed(fas.map(_.scanLeftEmbed(z)(f)))
      case Empty      => Empty
      case FromFuture(fa) =>
        FromFuture(fa.map(f(z, _)))
      case Cons(fa, more) =>
        Embed(fa.map(a => more().scanLeft(f(z, a))(f)))
    }

  /**
    * Applies a binary operator to a start value and all elements of the stream,
    * from head to tail.
    *
    * Note: forces the stream. If the stream is infinite, the resulting future
    * is equivalent to Future.never.
    *
    * @param z the starting value.
    * @param f a binary operator applied to elements of this stream.
    */
  def foldLeft[B](
    z: B
  )(
    f: (B, A) => B
  )(implicit executionContext: ExecutionContext
  ): Future[B] = this match {
    case Empty          => Future.successful(z)
    case FromFuture(fa) => fa.map(f(z, _))
    case Cons(fa, more) => fa.map(f(z, _)).flatMap(more().foldLeft(_)(f))
    case Embed(fas)     => fas.flatMap(_.foldLeft(z)(f))
  }

  /**
    * Like `foldLeft`, except that its result is encapsulated in a Future.
    * `foldLeftF` works from head to tail over the stream.
    *
    * Note: forces the stream. If the stream is infinite, the resulting future
    * is equivalent to Future.never.
    *
    * @param z the starting value.
    * @param f a binary operator applied to elements of this stream.
    */
  def foldLeftF[B](
    z: B
  )(
    f: (B, A) => Future[B]
  )(implicit executionContext: ExecutionContext
  ): Future[B] =
    this match {
      case Empty          => Future.successful(z)
      case FromFuture(fa) => fa.flatMap(a => f(z, a))
      case Cons(fa, more) =>
        fa.flatMap(a => f(z, a)).flatMap(b => more().foldLeftF(b)(f))
      case Embed(fas) => fas.flatMap(_.foldLeftF(z)(f))
    }

  /**
    * A recursive implementation of find for a AsyncStream
    *
    * WARNING: materializes the AsyncStream up to and including the first element that matches the predicate
    * If the AsyncStream is infinite, the resulting Future will never resolve
    *
    * @param p The predicate function to match
    * @return  The first item to return true from the passed predicate function
    */
  def find(
    p: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): Future[Option[A]] = {
    this match {
      case Empty => Future.successful(None)
      case FromFuture(fa) =>
        fa.flatMap {
          case a if p(a) => Future.successful(Some(a))
          case _         => Future.successful(None)
        }
      case Cons(fa, more) =>
        fa.flatMap {
          case a if p(a) => Future.successful(Some(a))
          case _         => more().find(p)
        }
      case Embed(fas) => fas.flatMap(_.find(p))
    }
  }

  /**
    *
    * @param p
    * @param executionContext
    * @return
    */
  def findF(
    p: A => Future[Boolean]
  )(implicit executionContext: ExecutionContext
  ): Future[Option[A]] = {
    this match {
      case Empty          => Future.successful(None)
      case FromFuture(fa) => fa.flatMap(a => p(a).map(if (_) Some(a) else None))
      case Cons(fa, more) => {
        for {
          a <- fa
          res <- p(a)
          next <- if (res) Future.successful(Some(a)) else more().findF(p)
        } yield next
      }
      case Embed(fas) => fas.flatMap(_.findF(p))
    }
  }

  /**
    * A "safe" implementation of "find" which gives up after a certain amount of items.
    *
    * NOTE: When limit > 0 is passed in, the function is guaranteed to exit, however
    * a "None" result implies that the a matching element to the predicate function wasn't seen
    * and not that is certainly "doesn't exist"
    *
    * @param limit The max number of items to check
    * @param p     The predicate function to match
    * @return      The first item to return true from the passed predicate function
    */
  def findWithLimit(
    limit: Int,
    p: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): Future[Option[A]] = {
    takeSafe(limit).find(p)
  }

  /**
    * Returns true if an element matching the predicate exists
    *
    * WARNING: materializes the AsyncStream up to and including the first element that matches the predicate
    * If the AsyncStream is infinite, the resulting Future will never resolve
    *
    * @param p The predicate function to match
    * @return  Returns true if an element in the AsyncStream passes predicate function
    */
  def exists(
    p: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): Future[Boolean] = find(p).map(_.isDefined)

  /**
    * A "safe" implementation of "exists" which gives up after a certain amount of items.
    *
    * NOTE: When limit > 0 is passed in, the function is guaranteed to exit, however
    * a "false" result implies that the a matching element to the predicate function wasn't seen
    * and not that is certainly "doesn't match anything"
    *
    * @param p The predicate function to match
    * @return  Returns true if an element in the AsyncStream passes predicate function
    */
  def existsSafe(
    limit: Int,
    p: A => Boolean
  )(implicit executionContext: ExecutionContext
  ): Future[Boolean] = findWithLimit(limit, p).map(_.isDefined)

  /**
    * Transform an AsyncStream into a Future[List]. Equivalent to toSeq but coerces to a List and returns a Scala Future
    *
    * WARNING: Will fully buffer the AsyncStream
    *
    * @return A Future containing a list of the fully materialized AsyncStream
    */
  def toList(implicit executionContext: ExecutionContext): Future[List[A]] = {
    toSeq.map(_.toList)
  }

  /**
    * An implementation of flatMap where f returns a Sequence
    *
    *
    * @param f  The function which maps an element of the AsyncStream to a new Seq
    * @tparam U The new type of element which is returned by the map function
    * @return   A new AsyncStream where each value is threaded through the map function exactly once
    */
  def flatMapSeq[U](
    f: A => Seq[U]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[U] = {
    this match {
      case Empty          => Empty
      case FromFuture(fa) => Embed[U](fa.map(f).map(fromSeq))
      case Cons(fa, more) =>
        Embed[U](fa.map(f).map(fromSeq)) ++ more().flatMapSeq(f)
      case Embed(fas) => Embed[U](fas.map(_.flatMapSeq(f)))
    }
  }

  /**
    * flatMapF returns a new AsyncStream with a function which returns a Future[Seq] applied to each element
    *
    * NOTE: *Materializes the head of the stream due to internal flatMap*
    *
    * @param f  The function which maps an element of the AsyncStream to a Future[Seq]
    * @tparam U The new type of element which is returned by the map function
    * @return   A new AsyncStream where each value is threaded through the flatMap function exactly once
    */
  def flatMapF[U](
    f: A => Future[Seq[U]]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[U] = {
    this match {
      case Empty          => Empty
      case FromFuture(fa) => Embed[U](fa.flatMap(f).map(fromSeq))
      case Cons(fa, more) =>
        Embed[U](fa.flatMap(f).map(fromSeq)) ++ more().flatMapF(f)
      case Embed(fas) => Embed[U](fas.map(_.flatMapF(f)))
    }
  }

  /**
    * An implementation of mapConcurrent which helps handle functions which return Sequences
    *
    * @param concurrencyLevel
    * @param f
    * @tparam U
    * @return
    */
  def flatMapConcurrent[U](
    concurrencyLevel: Int
  )(
    f: A => Future[Seq[U]]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[U] = {
    mapConcurrent(concurrencyLevel)(f(_).map(fromSeq)).flatten
  }

  /**
    * A version of .take that supports passing -1.
    *
    * @param n The limit on items to take. Passing -1 will return the same AsyncStream
    * @return  An AsyncStream limited to the amount of items specified by n
    */
  def takeSafe(
    n: Int
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] = {
    if (n < 0) this else take(n)
  }

  /**
    * This is a powerful and expert level function. A fold operation
    * encapsulated in a Future. Like foldRight on normal lists, it replaces
    * every cons with the folded function `f`, and the empty element with `z`.
    *
    * Note: For clarity, we imagine that surrounding a function with backticks
    * (&#96;) allows infix usage.
    *
    * {{{
    *     (1 +:: 2 +:: 3 +:: empty).foldRight(z)(f)
    *   = 1 `f` flatMap (2 `f` flatMap (3 `f` z))
    * }}}
    *
    * Note: if `f` always forces the second parameter, for infinite streams the
    * future never resolves.
    *
    * @param z the parameter that replaces the end of the list.
    * @param f a binary operator applied to elements of this stream. Note that
    * the second paramter is call-by-name.
    */
  def foldRight[B](
    z: => Future[B]
  )(
    f: (A, => Future[B]) => Future[B]
  )(implicit executionContext: ExecutionContext
  ): Future[B] =
    this match {
      case Empty          => z
      case FromFuture(fa) => fa.flatMap(f(_, z))
      case Cons(fa, more) => fa.flatMap(f(_, more().foldRight(z)(f)))
      case Embed(fas)     => fas.flatMap(_.foldRight(z)(f))
    }

  /**
    * Concatenate a stream of streams.
    *
    * {{{
    * val a = AsyncStream(1)
    * AsyncStream(a, a, a).flatten = AsyncStream(1, 1, 1)
    * }}}
    *
    * Java users see [[AsyncStream.flattens]].
    */
  def flatten[B](
    implicit ev: A <:< AsyncStream[B],
    executionContext: ExecutionContext
  ): AsyncStream[B] =
    this match {
      case Empty          => empty
      case FromFuture(fa) => Embed(fa.map(ev))
      case Cons(fa, more) => Embed(fa.map(ev)) ++ more().flatten
      case Embed(fas)     => Embed(fas.map(_.flatten))
    }

  def distinct(implicit executionContext: ExecutionContext): AsyncStream[A] = {
    def loop(
      seen: Set[A],
      rest: AsyncStream[A]
    ): AsyncStream[A] = {
      rest match {
        case Empty          => empty
        case FromFuture(fa) => Embed(fa.map(a => if (seen(a)) empty else of(a)))
        case Cons(fa, more) =>
          Embed(
            fa.map(
              a =>
                if (seen(a)) empty ++ loop(seen, more())
                else of(a) ++ loop(seen + a, more())
            )
          )
        case Embed(fas) => Embed(fas.map(loop(seen, _)))
      }
    }

    loop(Set(), this)
  }

  /**
    * A Future of the stream realized as a list. This future completes when all
    * elements of the stream are resolved.
    *
    * Note: forces the entire stream. If one asynchronous call fails, it fails
    * the aggregated result.
    */
  def toSeq()(implicit executionContext: ExecutionContext): Future[Seq[A]] =
    observe().flatMap {
      case (s, None)      => Future.successful(s)
      case (_, Some(exc)) => Future.failed(exc)
    }

  /**
    * Attempts to transform the stream into a Seq, and in the case of failure,
    * `observe` returns whatever was able to be transformed up to the point of
    * failure along with the exception. As a result, this Future never fails,
    * and if there are errors they can be accessed via the Option.
    *
    * Note: forces the stream. For infinite streams, the future never resolves.
    */
  def observe(
  )(implicit executionContext: ExecutionContext
  ): Future[(Seq[A], Option[Throwable])] = {
    val buf: mutable.ListBuffer[A] = mutable.ListBuffer.empty

    def go(as: AsyncStream[A]): Future[Unit] =
      as match {
        case Empty => Future.successful(Unit)
        case FromFuture(fa) =>
          fa.flatMap { a =>
            buf += a
            Future.successful(Unit)
          }
        case Cons(fa, more) =>
          fa.flatMap { a =>
            buf += a
            go(more())
          }
        case Embed(fas) =>
          fas.flatMap(go)
      }

    // TODO: Use transform when we drop 2.10
    go(this).map(_ => buf.toList -> None).recover {
      case t => buf.toList -> Some(t)
    }
  }

  /**
    * Buffer the specified number of items from the stream, or all
    * remaining items if the end of the stream is reached before finding
    * that many items. In all cases, this method should act like
    * <http://www.scala-lang.org/api/current/index.html#scala.collection.GenTraversableLike@splitAt(n:Int):(Repr,Repr)>
    * and not cause evaluation of the remainder of the stream.
    */
  private[util] def buffer(
    n: Int
  )(implicit executionContext: ExecutionContext
  ): Future[(Seq[A], () => AsyncStream[A])] = {
    // pre-allocate the buffer, unless it's very large
    val buffer = new mutable.ArrayBuffer[A](n.min(1024))

    def fillBuffer(
      sizeRemaining: Int
    )(
      s: => AsyncStream[A]
    ): Future[(Seq[A], () => AsyncStream[A])] =
      if (sizeRemaining < 1) Future.successful((buffer, () => s))
      else
        s match {
          case Empty => Future.successful((buffer, () => s))

          case FromFuture(fa) =>
            fa.flatMap { a =>
              buffer += a
              Future.successful((buffer, () => empty))
            }

          case Cons(fa, more) =>
            fa.flatMap { a =>
              buffer += a
              fillBuffer(sizeRemaining - 1)(more())
            }

          case Embed(fas) =>
            fas.flatMap(as => fillBuffer(sizeRemaining)(as))
        }

    fillBuffer(n)(this)
  }

  /**
    * Convert the stream into a stream of groups of items. This
    * facilitates batch processing of the items in the stream. In all
    * cases, this method should act like
    * <http://www.scala-lang.org/api/current/index.html#scala.collection.IterableLike@grouped(size:Int):Iterator[Repr]>
    * The resulting stream will cause this original stream to be
    * evaluated group-wise, so calling this method will cause the first
    * `groupSize` cells to be evaluated (even without examining the
    * result), and accessing each subsequent element will evaluate a
    * further `groupSize` elements from the stream.
    * @param groupSize must be a positive number, or an IllegalArgumentException will be thrown.
    */
  def grouped(
    groupSize: Int
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[Seq[A]] =
    if (groupSize > 1) {
      Embed(buffer(groupSize).map {
        case (items, _) if items.isEmpty => empty
        case (items, remaining) =>
          Cons(Future.successful(items), () => remaining().grouped(groupSize))
      })
    } else if (groupSize == 1) {
      map(Seq(_))
    } else {
      throw new IllegalArgumentException(
        s"groupSize must be positive, but was $groupSize"
      )
    }

  /**
    * Add up the values of all of the elements in this stream. If you
    * hold a reference to the head of the stream, this will cause the
    * entire stream to be held in memory.
    *
    * Note: forces the stream. If the stream is infinite, the resulting future
    * is equivalent to Future.never.
    */
  def sum[B >: A](
    implicit numeric: Numeric[B],
    executionContext: ExecutionContext
  ): Future[B] =
    foldLeft(numeric.zero)(numeric.plus)

  /**
    * Eagerly consume the entire stream and return the number of elements
    * that are in it. If you hold a reference to the head of the stream,
    * this will cause the entire stream to be held in memory.
    *
    * Note: forces the stream. If the stream is infinite, the resulting future
    * is equivalent to Future.never.
    */
  def size(implicit executionContext: ExecutionContext): Future[Int] =
    foldLeft(0)((n, _) => n + 1)

  /**
    * Force the entire stream. If you hold a reference to the head of the
    * stream, this will cause the entire stream to be held in memory. The
    * resulting Future will be satisfied once the entire stream has been
    * consumed.
    *
    * This is useful when you want the side-effects of consuming the
    * stream to occur, but do not need to do anything with the resulting
    * values.
    */
  def force(implicit executionContext: ExecutionContext): Future[Unit] =
    foreach { _ =>
    }
}

object AsyncStream {
  private case object Empty extends AsyncStream[Nothing]
  private case class Embed[A](val fas: Future[AsyncStream[A]])
      extends AsyncStream[A]
  private case class FromFuture[A](fa: Future[A]) extends AsyncStream[A]
  private class Cons[A](
    val fa: Future[A],
    next: () => AsyncStream[A])
      extends AsyncStream[A] {
    private[this] lazy val _more: AsyncStream[A] = next()
    def more(): AsyncStream[A] = _more
    override def toString(): String = s"Cons($fa, $next)"
  }

  object Cons {
    def apply[A](
      fa: Future[A],
      next: () => AsyncStream[A]
    ): AsyncStream[A] =
      new Cons(fa, next)

    def unapply[A](as: Cons[A]): Option[(Future[A], () => AsyncStream[A])] =
      // note: pattern match returns the memoized value
      Some((as.fa, () => as.more()))
  }

  implicit class Ops[A](tail: => AsyncStream[A]) {

    /**
      * Right-associative infix Cons constructor.
      *
      * Note: Because of https://issues.scala-lang.org/browse/SI-1980 we can't
      * define +:: as a method on AsyncStream without losing tail laziness.
      */
    def +::[B >: A](b: B): AsyncStream[B] = mk(b, tail)
  }

  def empty[A]: AsyncStream[A] = Empty.asInstanceOf[AsyncStream[A]]

  /**
    * Var-arg constructor for AsyncStreams.
    *
    * {{{
    * AsyncStream(1,2,3)
    * }}}
    *
    * Note: we can't annotate this with varargs because of
    * https://issues.scala-lang.org/browse/SI-8743. This seems to be fixed in a
    * more recent scala version so we will revisit this soon.
    */
  def apply[A](as: A*): AsyncStream[A] = fromSeq(as)

  /**
    * An AsyncStream with a single element.
    */
  def of[A](a: A): AsyncStream[A] = FromFuture(Future.successful(a))

  /**
    * Like `Ops.+::`.
    */
  def mk[A](
    a: A,
    tail: => AsyncStream[A]
  ): AsyncStream[A] =
    Cons(Future.successful(a), () => tail)

  /**
    * A failed AsyncStream
    */
  def exception[A](e: Throwable): AsyncStream[A] =
    fromFuture[A](Future.failed(e))

  /**
    * Transformation (or lift) from [[scala.Seq]] into `AsyncStream`.
    */
  def fromSeq[A](seq: Seq[A]): AsyncStream[A] = seq match {
    case Nil                                          => empty
    case _ if seq.hasDefiniteSize && seq.tail.isEmpty => of(seq.head)
    case _                                            => seq.head +:: fromSeq(seq.tail)
  }

  /**
    * Transformation (or lift) from [[scala.Stream]] into `AsyncStream`
    */
  def fromStream[A](stream: Stream[A]): AsyncStream[A] = stream match {
    case _ if stream.isEmpty => empty
    case _                   => stream.head +:: fromStream(stream.tail)
  }

  /**
    * Transformation (or lift) from [[scala.concurrent.Future]] into `AsyncStream`.
    */
  def fromFuture[A](f: Future[A]): AsyncStream[A] =
    FromFuture(f)

  /**
    * Transformation (or lift) from [[scala.Option]] into `AsyncStream`.
    */
  def fromOption[A](o: Option[A]): AsyncStream[A] =
    o match {
      case None    => empty
      case Some(a) => of(a)
    }

  /**
    * Lift from [[scala.concurrent.Future]] into `AsyncStream` and then flatten.
    */
  private[util] def embed[A](fas: Future[AsyncStream[A]]): AsyncStream[A] =
    Embed(fas)

  /**
    * Java friendly [[AsyncStream.flatten]].
    */
  def flattens[A](
    as: AsyncStream[AsyncStream[A]]
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] =
    as.flatten

  /**
    * Combinator, merges multiple [[AsyncStream]]s into a single stream. The
    * resulting stream contains elements in FIFO order per input stream but order
    * between streams is not guaranteed. The resulting stream is completed when
    * all input streams are completed. The stream is failed when any input stream
    * is failed
    */
  def merge[A](
    s: AsyncStream[A]*
  )(implicit executionContext: ExecutionContext
  ): AsyncStream[A] = {
    def step(
      next: Seq[Future[Option[(A, () => AsyncStream[A])]]]
    ): AsyncStream[A] = {
      fromFuture(FutureUtils.select(next)).flatMap {
        case (Success(Some((head, tail))), tails) =>
          head +:: step(tail().uncons +: tails)
        case (Failure(cause), _) =>
          fromFuture(Future.failed(cause))
        case (Success(None), Nil) =>
          empty
        case (Success(None), tails) =>
          step(tails)
      }
    }

    if (s.isEmpty) {
      empty
    } else {
      step(s.map(_.uncons))
    }
  }

  /**
    * This must exist here, otherwise scalac will be greedy and hold a reference to the head of the stream
    */
  private def mapConcStep[A, B](
    concurrencyLevel: Int,
    f: (A => Future[B]),
    pending: Seq[Future[Option[B]]],
    inputs: () => AsyncStream[A]
  )(implicit executionContext: ExecutionContext
  ): Future[AsyncStream[B]] = {
    // We only invoke inputs().uncons if there is space for more work
    // to be started.
    val inputReady: Future[Left[Option[(A, () => AsyncStream[A])], Nothing]] = {
      if (pending.size >= concurrencyLevel) {
        Promise[Nothing]().future
      } else {
        inputs().uncons.flatMap {
          case None if pending.nonEmpty => Promise[Nothing]().future
          case other                    => Future.successful(Left(other))
        }
      }
    }

    // The inputReady.isDefined check is an optimization to avoid the
    // wasted allocation of calling Future.select when we know that
    // there is more work that is ready to be started.
    val workDone
      : Future[Right[Nothing, (Try[Option[B]], Seq[Future[Option[B]]])]] = {
      if (pending.isEmpty || inputReady.isCompleted) {
        Promise[Nothing]().future
      } else {
        FutureUtils.select(pending).map(Right(_))
      }
    }

    // Wait for either the next input to be ready (Left) or for some pending
    // work to complete (Right).
    Future.firstCompletedOf(inputReady :: workDone :: Nil).flatMap {
      case Left(None) =>
        // There is no work pending and we have exhausted the
        // inputs, so we are done.
        Future.successful(empty)

      case Left(Some((a, tl))) =>
        // There is available concurrency and a new input is ready,
        // so start the work and add it to `pending`.
        mapConcStep(concurrencyLevel, f, f(a).map(Some(_)) +: pending, tl)

      case Right((Failure(t), _)) =>
        // Some work finished with failure, so terminate the stream.
        Future.failed(t)

      case Right((Success(None), newPending)) =>
        // A cons cell was forced, freeing up a spot in `pending`.
        mapConcStep(concurrencyLevel, f, newPending, inputs)

      case Right((Success(Some(a)), newPending)) =>
        // Some work finished. Eagerly start the next step, so
        // that all available inputs have work started on them. In
        // the next step, we replace the pending Future for the
        // work that just completed with a Future that will be
        // satisfied by forcing the next element of the result
        // stream. Keeping `pending` full is the mechanism that we
        // use to bound the evaluation depth at `concurrencyLevel`
        // until the stream is forced.
        val cellForced = Promise[Option[B]]()
        val rest = mapConcStep(
          concurrencyLevel,
          f,
          cellForced.future +: newPending,
          inputs
        )
        Future.successful(mk(a, { cellForced.success(None); embed(rest) }))
    }
  }
}
