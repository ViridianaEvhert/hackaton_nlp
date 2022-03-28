//> using:
//>  scala "3.1"
//>  lib "co.fs2::fs2-io:3.2.5"

import cats.{Eq, Show}
import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.std.Semaphore
import cats.syntax.foldable.*
import cats.syntax.functor.*
import fs2.{Compiler, Pipe, Pure, Stream, text}
import fs2.io.file.{Files, Flags, Path}

opaque type Word = String
object Word:
  def apply(s: String): Option[Word] =
    val s1 = s.trim
    Option.unless(s1.nonEmpty)(s1)

  extension (w: Word)
    def asString: String = w
end Word

type WordSeq = NonEmptyList[Word]

def splitWords(sentence: String): Stream[Pure, Word] =
  val splitAt = Array('?', '!', ',', ';', ':', '-')
  val separator = "--"
  val filterChar = Set('¿', '¡', '\"', '\'', '`', '‘', '’')
  Stream.emits(sentence.split(splitAt))
        .filter(_.nonEmpty)
        .intersperse(separator)
        .flatMap(s => Stream.emits(s.split("""\s+""")))
        .map(_.filterNot(filterChar.contains))
        .map(_.trim)
        .filter(_.nonEmpty)

def sentences[F[_]]: Pipe[F, String, String] =
  _.flatMap { s =>
    Stream.emits(s"$s ".split("""\.\s+"""))
          .map(_.trim)
          .filter(_.nonEmpty)
  }

def capitalizedSequences[F[_]]: Pipe[F, Word, WordSeq] =
  _.drop(1) // First word of a sentence is expected to be always capitalized; skipping it for now
   .scan(Left(Nil): Either[List[Word], List[Word]]) { (acc, word) =>
     if word.head.isUpper // && word.tail.forall(_.isLower)
     then Left(word :: acc.left.getOrElse(Nil))
     else acc.as(Nil).swap.map(_.reverse)
   }.map(_.toOption.flatMap(NonEmptyList.fromList))
    .unNone


def extractKeywords[F[_]]: Pipe[F, Word, WordSeq] = capitalizedSequences[F]


def keywords(inputDir: Path, suffix: String)(using fs: Files[IO]): Stream[IO, (Path, Stream[IO, WordSeq])] =
  for
    in <- fs.walk(inputDir)
            .filter(_.toString endsWith suffix)
    _  <- Stream.eval { IO.println(s"Processing ${inputDir.relativize(in)}") }
    kws = fs.readAll(in)
            .through(text.utf8.decode)
            .through(text.lines)
            .through(sentences)
            .flatMap(splitWords(_).through(extractKeywords))
  yield in -> kws

def evalFrequencies[F[_], A](s: Stream[F, A])(using Compiler[F, F]): F[Map[A, Int]] =
  s.map(a => Map(a -> 1)).compile.foldMonoid


def mainStream(inputDir0: String, suffix: String, outputFile: String, limFreq: Int)(using fs: Files[IO]): Stream[IO, Stream[IO, Unit]] =
  val inputDir = Path(inputDir0)
  for
    fh   <- Stream.resource { fs.open(Path(outputFile), Flags.Write) }
    sem  <- Stream.eval { Semaphore[IO](1) }
    main <- keywords(inputDir, suffix).map { case (path, s) =>
              for
                freq <- Stream.eval { evalFrequencies(s.map(_.mkString_(" "))) }
                pathS = Stream("\n", "-" * 16, inputDir.relativize(path).toString, "")
                freqS = Stream.emits {
                                given Ordering[Int] = Ordering.Int.reverse
                                freq.view.filter(_._2 >= limFreq)
                                    .toList.sortBy(_.swap)
                              }
                              .map { case (k, n) => s"$k : $n" }
                outS  = (pathS ++ freqS).intersperse("\n")
                _    <- Stream.resource(sem.permit)
                cur  <- Stream.eval { fs.writeCursorFromFileHandle(fh, append = true) }
                _    <- cur.writeAll(outS.through(text.utf8.encode)).void.stream
              yield ()
            }
  yield main

def mainIO(inputDir: String, suffix: String, outputFile: String, maxPar: Int, limFreq: Int)(using Files[IO]): IO[Unit] =
  mainStream(inputDir, suffix, outputFile, limFreq)
    .parJoin(maxPar)
    .compile.drain

@main
def FindKeywords(inputDir: String, suffix: String, outputDir: String): Unit =
  import cats.effect.unsafe.implicits.global
  // TODO
  val maxPar = 4
  val limFreq = 3

  mainIO(inputDir, suffix, outputDir, maxPar, limFreq).unsafeRunSync()
