#!/usr/bin/env -S scala-cli shebang -M=main

//> using:
//>  scala "3.1"
//>  lib "org.typelevel::cats-effect:3.3.8"
//>  lib "co.fs2::fs2-io:3.2.5"
//>  lib "org.scala-lang.modules::scala-xml:2.0.1"

import cats.effect.{IO, Sync}
import cats.instances.lazyList.*
import cats.syntax.traverse.*
import fs2.{text, INothing, Pipe, Stream}
import fs2.io.file.{Files, Path}

import scala.xml.*
import scala.xml.transform.RewriteRule

def logUnsafe(msg: String) = println(msg)
def log(msg: String) = IO{ logUnsafe(msg) }

object CleanAndFoldXml extends RewriteRule:
  override def transform(n: Node): Seq[Node] =
    n match
      case Node("p", _, children*) =>
        children.flatMap(transform)
      case Text(txt) =>
         Text(cleanText(txt))
      case other =>
         logUnsafe(s"unknown ${other.getClass.getSimpleName}: $other")
         Text("")

  private def cleanText(s: String) =
    val s1 = s.filterNot(_ == '\n')
    s1.split(':')
      match
      case Array(h, t*) if h.forall(_.isUpper) => t.mkString(":")
      case _ => s1
end CleanAndFoldXml
  
def clean: Pipe[IO, String, String] =
  _.evalMapFilter{
     case "" => IO.pure(None)
     case s  => IO{ Option(XML.loadString(s)) }
                  .onError{ _ => log(s"Failed to parse xml '$s'")}
   }
   .map(CleanAndFoldXml.transform)
   .evalMap(
     _.to(LazyList)
      .filter(_ != Null)
      .traverse {
        case Text(s) => IO.pure(s)
        case other   => IO.raiseError(Exception(s"not a Text: $other"))
      }
      .map(_.filter(_.nonEmpty).mkString("").trim)
    )

def mainS(inputDir: String, ext: String, outputDir: String)(using fs: Files[IO]): Stream[IO, Stream[IO, INothing]] =
  for
    in <- fs.list(Path(inputDir)).filter(_.extName == ext)
    _  <- Stream.eval{ log(s"cleaning $in") }
    out = Path(outputDir) / in.fileName
  yield
   fs.readAll(in)
     .through(text.utf8.decode)
     .through(text.lines)
     .through(clean)
     .intersperse("\n")
     .through(text.utf8.encode)
     .through(fs.writeAll(out))
  
@main
def main(inputDir: String, suffix: String, outputDir: String): Unit =
  import cats.effect.unsafe.implicits.global
  
  // TODO
  val maxPar = 4
  
  mainS(inputDir, suffix, outputDir)
   .parJoin(maxPar)
   .debug()
   .compile
   .drain
   .unsafeRunSync()
