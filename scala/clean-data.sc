//> using:
//>  scala "3.1"
//>  lib "co.fs2::fs2-io:3.2.5"

import cats.effect.{IO, Sync}
import cats.instances.lazyList.*
import cats.syntax.applicativeError.*
import cats.syntax.apply.*
import cats.syntax.either.*
import cats.syntax.flatMap.*
import cats.syntax.traverse.*
import fs2.{INothing, Pipe, Stream, text}
import fs2.io.file.{Files, Path}

import scala.xml.*

import lib.log.{log, ChannelLogger, Logger, LogLevel}
import lib.xml.Xml


def cleanAndFlatten(xml: Xml[LazyList])(using Logger[IO]): IO[LazyList[String]] =
  xml.hyloF {
    case l@Xml.Leaf(Text(_))          => IO.pure(l)
    case Xml.Node.Elem("p", _, child) => IO.pure(Xml.Node(None, child))
    case other                        => log.warning(s"unknown ${other.mapC([B] => (_: LazyList[B]).toList)}")
                                      *> IO.pure(Xml.Node.empty)
  } {
    case Xml.Leaf(Text(txt))    => IO.pure(LazyList(txt))
    case Xml.Node.Empty()       => IO.pure(LazyList.empty)
    case Xml.Node.Group(child)  => IO.pure(child.flatten)
    case other                  => IO.raiseError(new MatchError(other))
  }

def clean(using Logger[IO]): Pipe[IO, String, String] =
  _.evalMapFilter{
     case "" => IO.pure(None)
     case s  => Xml.parse(s, LazyList).liftTo[IO].map(Some(_))
   }
   .evalMap(cleanAndFlatten(_))
   .map{ _.filter(_.nonEmpty).mkString("").trim }
   .filter(_.nonEmpty)

def mainIO(inputDir: String, ext: String, outputDir: String, logFile: String, maxPar: Int)(using fs: Files[IO]): IO[Unit] =
  ChannelLogger[IO].use { case log0 @ (given Logger[IO]) =>
    val dataS =
      for
        in <- fs.list(Path(inputDir)).filter(_.extName == ext)
        _  <- Stream.eval{ log.info(s"cleaning $in") }
        out = Path(outputDir) / in.fileName
        given Logger[IO] = log.withContext(Map("file" -> in.fileName.toString))
      yield fs.readAll(in)
              .through(text.utf8.decode)
              .through(text.lines)
              .through(clean)
              .intersperse("\n")
              .through(text.utf8.encode)
              .through(fs.writeAll(out))
              .onError{ case err => Stream.eval{ log.error(err.getMessage, Some(err)) } }
    val logOut = Path(logFile)
    val logFormat = (e: ChannelLogger.Entry) =>
                    val lvl = e.level
                    val ctx = e.context.getOrElse("file", "?")
                    val msg = e.message
                    val err = e.error.fold("")(
                                _.getStackTrace
                                 .map(t => s"  $t")
                                 .mkString("", "\n", "\n")
                              )
                    s"[$lvl] $ctx\n$msg\n$err"
    val logS = log0.stream.broadcastThrough(
                 // Write to file
                 _.filter(e => e.level == LogLevel.Error || e.level == LogLevel.Warning)
                  .map(logFormat)
                  .intersperse("\n")
                  .through(text.utf8.encode)
                  .through(fs.writeAll(logOut)),
                 // Console
                 _.map(logFormat)
                  .through(fs2.io.stdoutLines())
               )
    for
    // Run
      _ <- logS.compile.drain.start
      _ <- dataS.parJoin(maxPar).compile.drain.attempt
      _ <- log0.closeAndWait
    yield ()
  }
  
@main
def main(inputDir: String, suffix: String, outputDir: String): Unit =
  import cats.effect.unsafe.implicits.global
  // TODO
  val logFile = "clean-data.log"
  val maxPar = 4

  mainIO(inputDir, suffix, outputDir, logFile, maxPar).unsafeRunSync()
