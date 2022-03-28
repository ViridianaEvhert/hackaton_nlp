//> using:
//>  scala "3.1"
//>  lib "co.fs2::fs2-io:3.2.5"

import cats.effect.{IO, Sync}
import cats.instances.lazyList.*
import cats.syntax.applicativeError.*
import cats.syntax.apply.*
import cats.syntax.either.*
import cats.syntax.foldable.*
import cats.syntax.traverse.*
import fs2.{INothing, Pipe, Stream, text}
import fs2.io.file.{Files, Path}

import scala.xml.*

import lib.log.{log, ChannelLogger, Logger, LogLevel}
import lib.xml.Xml

def cleanText(txt: String): String =
  txt.replace("&nbsp;", " ")
     .replace("&amp;", "&")
     .replace("&lt;", "<")
     .replace("&gt;", ">")
     .replace(" ", " ")

def cleanAndFlatten(xml: Xml[LazyList])(using Logger[IO]): IO[LazyList[String]] =
  val accept = Set("stub", "p", "em", "s", "u")
  val drop = Set("url", "h1", "h2", "id", "strong", "a", "br")
  xml.hyloF {
    case l@Xml.Leaf(Text(_)) =>
      IO.pure(l)
    case Xml.Node.Elem(t, _, child) if accept contains t =>
      IO.pure(Xml.Node(None, child))
    case Xml.Node.Elem(t, _, child) if drop contains t =>
      IO.pure(Xml.Node.empty)
    case other =>
      log.warning(s"unknown ${other.mapC([B] => (_: LazyList[B]).toList)}")
      *> IO.pure(Xml.Node.empty)
  } {
    case Xml.Leaf(Text(txt))    => IO.pure(LazyList(cleanText(txt)))
    case Xml.Node.Empty()       => IO.pure(LazyList.empty)
    case Xml.Node.Group(child)  => IO.pure(child.flatten)
    case other                  => IO.raiseError(new MatchError(other))
  }

def clean(using Logger[IO]): Pipe[IO, String, String] =
  def escape(raw: String): String = s"<stub>$raw</stub>"

  _.evalMapFilter{
     case "" => IO.pure(None)
     case s  => Xml.parse(escape(s), LazyList)
                   .leftMap(err => Exception(s"${err.getMessage}\nXML: $s", err))
                   .map(Some(_))
                   .liftTo[IO]
   }
   .evalMap(cleanAndFlatten(_))
   .map{ _.filter(_.nonEmpty).mkString("").trim }
   .filter(_.nonEmpty)
end clean

def escapeXml: Pipe[IO, String, String] = _.map(_.replace("&", "&amp;"))

def mergeNonXmlNewline: Pipe[IO, String, String] =
  def notXml = !(_: String).trim.startsWith("<")

  _.zipWithNext
   .scan(Vector.empty[String], Option.empty[String]) {
     case ((acc, _),      (str, Some(next))) if notXml(next) => (acc :+ str) -> None
     case ((Vector(), _), (str, _))                          => Vector()     -> Some(str)
     case ((acc, _),      (str, _))                          => Vector()     -> Some((acc :+ str).mkString)
   }.map(_._2).unNone

def mainIO(inputDir0: String, suffix: String, outputDir0: String, logFile: String, maxPar: Int)(using fs: Files[IO]): IO[Unit] =
  ChannelLogger[IO].use { case log0 @ (given Logger[IO]) =>
    val inputDir = Path(inputDir0)
    val outputDir = Path(outputDir0)
    val dataS =
      for
        in <- fs.walk(inputDir).filter(_.toString endsWith suffix)
        rel = inputDir.relativize(in)
        out = outputDir.resolve(rel)
        _  <- Stream.eval{ log.info(s"cleaning $rel") }
        _  <- Stream.eval{ out.parent.traverse_(fs.createDirectories) }
        given Logger[IO] = log.withContext(Map("file" -> rel.toString))
      yield fs.readAll(in)
              .through(text.utf8.decode)
              .through(text.lines)
              .through(escapeXml)
              .through(mergeNonXmlNewline)
              .through(clean)
              .intersperse("\n")
              .through(text.utf8.encode)
              .through(fs.writeAll(out))
              .onError{ case err => Stream.eval{ log.error(err.getMessage, Some(err)) } }
              .attempt
    val logOut = Path(logFile)
    val logFormat = (e: ChannelLogger.Entry) =>
                    val lvl = e.level
                    val ctx = e.context.getOrElse("file", "")
                    val msg = e.message
                    // val err = e.error.fold("")(
                    //             _.getStackTrace
                    //              .map(t => s"  $t")
                    //              .mkString("", "\n", "\n")
                    //           )
                    s"[$lvl] $ctx\n$msg\n"
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
      _ <- dataS.parJoin(maxPar).compile.drain
      _ <- log0.closeAndWait
    yield ()
  }
  
@main
def CleanData(inputDir: String, suffix: String, outputDir: String): Unit =
  import cats.effect.unsafe.implicits.global
  // TODO
  val logFile = "clean-data.log"
  val maxPar = 4

  mainIO(inputDir, suffix, outputDir, logFile, maxPar).unsafeRunSync()
