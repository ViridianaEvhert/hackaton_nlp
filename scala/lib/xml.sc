//> using:
//>  scala "3.1"
//>  options "-Ykind-projector:underscores"
//>  lib "org.scala-lang.modules::scala-xml:2.0.1"
//>  lib "io.higherkindness::droste-core:0.9.0-M3"

import cats.{Applicative, Eval, Eq, Functor, Monad, Monoid, MonoidK, Traverse}
import cats.syntax.either.*
import cats.syntax.foldable.*
import cats.syntax.functor.*
import cats.syntax.monoid.*
import cats.syntax.traverse.*
import higherkindness.droste.data.Fix
import higherkindness.droste.{Algebra, AlgebraM, Coalgebra, CoalgebraM, scheme}

import scala.collection.IterableFactory
import scala.xml.*
  
enum Xml0[C[_], A]:
  case Leaf(xml: SpecialNode)
  case Node(xml0: Option[Elem], child: C[A])

object Xml0 extends Xml.Syntax:
  object Node:
    def empty[C[_]: MonoidK, A]: Node[C[_], A] = Node(None, MonoidK[C].empty[A])

    object Elem:
      def unapply[C[_], A](node: Node[C, A]): Option[(String, MetaData, C[A])] =
        node.xml0.map(e => (e.label, e.attributes, node.child))

    object Group:
      def unapply[C[_], A](node: Node[C, A]): Option[C[A]] =
        Option.when(node.xml0.isEmpty)(node.child)

    object Empty:
      def unapply[C[_], A](node: Node[C, A])(using Monoid[C[A]], Eq[C[A]]): Boolean =
        node.xml0.isEmpty && node.child.isEmpty
  end Node

  extension [C[_], A](xml: Xml0[C, A])
    def mapC[D[_]](fk: [B] => C[B] => D[B]): Xml0[D, A] =
      xml match
        case l@Leaf(_)   => l.asInstanceOf[Xml0[D, A]]
        case Node(n, xs) => Node(n, fk(xs))

  given xml0Functor[C[_]: Functor]: Functor[Xml0[C, _]] with
    def map[A, B](fa: Xml0[C, A])(f: A => B): Xml0[C, B] =
      fa match
        case l@Leaf(_)   => l.asInstanceOf[Xml0[C, B]]
        case Node(n, xs) => Node(n, xs.map(f))

  given xml0Traverse[C[_]: Traverse]: Traverse[Xml0[C, _]] with
    def traverse[G[_], A, B](fa: Xml0[C, A])(f: A => G[B])(using A: Applicative[G]) =
      fa match
        case l@Leaf(_)   => A.pure(l.asInstanceOf[Xml0[C, B]])
        case Node(n, xs) => xs.traverse(f).map(Node(n, _))

    def foldLeft[A, B](fa: Xml0[C, A], b: B)(f: (B, A) => B): B =
      fa match
        case l@Leaf(_)   => b
        case Node(n, xs) => xs.foldLeft(b)(f)

    def foldRight[A, B](fa: Xml0[C, A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      fa match
        case l@Leaf(_)   => lb
        case Node(n, xs) => xs.foldRight(lb)(f)

end Xml0


type Xml[C[_]] = Fix[Xml0[C, _]]

object Xml:
  export Xml0.{Leaf, Node}

  def apply[C[_]: Functor](xml: scala.xml.Node, it: IterableFactory[C]): Xml[C] =
    unfold(xml) {
      case l: SpecialNode => Leaf(l)
      case n: Elem        => Node(Some(n.copy(child = Nil)), it.from(n.child))
      case g: Group       => Node(None, it.from(g.nodes))
    }

  def parse[C[_]: Functor](s: String, it: IterableFactory[C]): Either[Throwable, Xml[C]] =
    Either.catchNonFatal { apply(XML.loadString(s), it) }

  def unfold[C[_]: Functor, A](a: A)(ana: A => Xml0[C, A]): Xml[C] =
    scheme.ana(Coalgebra(ana)).apply(a)

  def unfoldF[F[_]: Monad, C[_]: Traverse, A](a: A)(ana: A => F[Xml0[C, A]]): F[Xml[C]] =
    scheme.anaM(CoalgebraM(ana)).apply(a)


  protected[lib] trait Syntax:
    extension [C[_]: Functor](xml: Xml[C])
      def fold[A](cata: Xml0[C, A] => A): A =
        scheme.cata(Algebra(cata)).apply(xml)

      def hylo[A](ana: Xml0[C, Xml[C]] => Xml0[C, Xml[C]])(cata: Xml0[C, A] => A): A =
        scheme.hylo(Algebra(cata), Coalgebra(ana compose Fix.un)).apply(xml)

    extension [C[_]: Traverse](xml: Xml[C])
      def foldF[F[_]: Monad, A](cata: Xml0[C, A] => F[A]): F[A] =
        scheme.cataM(AlgebraM(cata)).apply(xml)

      def hyloF[F[_]: Monad, A](ana: Xml0[C, Xml[C]] => F[Xml0[C, Xml[C]]])(cata: Xml0[C, A] => F[A]): F[A] =
        scheme.hyloM(AlgebraM(cata), CoalgebraM(ana compose Fix.un)).apply(xml)
  end Syntax

end Xml
