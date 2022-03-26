//> using:
//>  scala "3.1"
//>  lib "org.typelevel::cats-effect:3.3.8"
//>  lib "co.fs2::fs2-core:3.2.5"

import cats.{FlatMap, FunctorFilter}
import cats.effect.{Clock, Concurrent, Ref, Resource}
import cats.syntax.apply.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.functorFilter.*
import fs2.Stream
import fs2.concurrent.Channel

import scala.concurrent.duration.FiniteDuration

object log:
  inline def error[F[_]](message: String, error: Option[Throwable] = None, context: Map[String, String] = Map())
                        (using logger: Logger[F]): F[Unit] = logger.error(message, error, context)

  inline def warning[F[_]](message: String, error: Option[Throwable] = None, context: Map[String, String] = Map())
                          (using logger: Logger[F]): F[Unit] = logger.warning(message, error, context)

  inline def info[F[_]](message: String, error: Option[Throwable] = None, context: Map[String, String] = Map())
                       (using logger: Logger[F]): F[Unit] = logger.info(message, error, context)

  inline def debug[F[_]](message: String, error: Option[Throwable] = None, context: Map[String, String] = Map())
                        (using logger: Logger[F]): F[Unit] = logger.debug(message, error, context)

  inline def withContext[F[_]](context: Map[String, String])(using logger: Logger[F]): Logger[F] = logger.withContext(context)
end log

trait Logger[F[_]]:
  def log(level: LogLevel, message: String, error: Option[Throwable], context: Map[String, String]): F[Unit]

  def withContext(context: Map[String, String]): Logger[F]

  inline def error(message: String, error: Option[Throwable] = None, context: Map[String, String] = Map()): F[Unit] =
    log(LogLevel.Error, message, error, context)

  inline def warning(message: String, error: Option[Throwable] = None, context: Map[String, String] = Map()): F[Unit] =
    log(LogLevel.Warning, message, error, context)

  inline def info(message: String, error: Option[Throwable] = None, context: Map[String, String] = Map()): F[Unit] =
    log(LogLevel.Info, message, error, context)

  inline def debug(message: String, error: Option[Throwable] = None, context: Map[String, String] = Map()): F[Unit] =
    log(LogLevel.Debug, message, error, context)
end Logger

object Logger:
  inline def apply[F[_]: Logger] = summon


enum LogLevel:
  case Error
  case Warning
  case Info
  case Debug


class ChannelLogger[F[_]: Clock: FlatMap] private(
  private val channel: Channel[F, ChannelLogger.Entry],
  defaultContext: Map[String, String]
) extends Logger[F]:
  import ChannelLogger.Entry

  def log(level: LogLevel, message: String, error: Option[Throwable], context: Map[String, String]): F[Unit] =
    for
      time <- Clock[F].realTime
      thr   = Thread.currentThread()
      entry = Entry(level, message, error, defaultContext ++ context, thr.getName, thr.getId, time)
      _    <- channel.send(entry)
    yield ()

  def closeAndWait: F[Unit] = channel.close *> channel.closed
  def stream: Stream[F, Entry] = channel.stream

  def withContext(context: Map[String, String]): ChannelLogger[F] = new ChannelLogger(channel, context)
end ChannelLogger

object ChannelLogger:
  def apply[F[_]: Concurrent: Clock]: Resource[F, ChannelLogger[F]] =
    Resource.make(
      Channel.unbounded[F, Entry].map(new ChannelLogger(_, Map()))
    )(
      _.channel.close.void
    )

  case class Entry(
    level: LogLevel,
    message: String,
    error: Option[Throwable],
    context: Map[String, String],
    threadName: String,
    threadId: Long,
    timestamp: FiniteDuration
  )
end ChannelLogger
