package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Balance, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape, Inlet, Outlet, Shape}

import scala.collection.immutable
import scala.concurrent.duration._
import scala.language.postfixOps

object CustomGraphShapes extends App {

  implicit val system = ActorSystem("CustomGraphShapes")
  implicit val materializer = ActorMaterializer()

  // balance 2x3 shape
  case class Balance2x3(
                         in0: Inlet[Int],
                         in1: Inlet[Int],
                         out0: Outlet[Int],
                         out1: Outlet[Int],
                         out2: Outlet[Int]
                       ) extends Shape {
    override val inlets = List(in0, in1)
    override val outlets = List(out0, out1, out2)

    override def deepCopy(): Shape = Balance2x3(
      in0.carbonCopy(),
      in1.carbonCopy(),
      out0.carbonCopy(),
      out1.carbonCopy(),
      out2.carbonCopy()
    )
  }

  val balance2x3Impl = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](3))

    merge ~> balance

    Balance2x3(merge.in(0), merge.in(1), balance.out(0), balance.out(1), balance.out(2))
  }

  val balance2x3Graph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
      val fastSource = Source(Stream.from(1)).throttle(2, 1 second)

      def createSink(index: Int) = Sink.fold(0)((count: Int, element: Int) => {
        println(s"Received from sink[$index] $element, current count is $count")
        count + 1
      })

      val sink0 = builder.add(createSink(0))
      val sink1 = builder.add(createSink(1))
      val sink2 = builder.add(createSink(2))

      val balance2x3 = builder.add(balance2x3Impl)

      slowSource ~> balance2x3.in0
      fastSource ~> balance2x3.in1

      balance2x3.out0 ~> sink0
      balance2x3.out1 ~> sink1
      balance2x3.out2 ~> sink2

      ClosedShape
    }
  )

  //balance2x3Graph.run()

  // balance 2x3 shape
  case class BalanceMxN[I](
                            ins: List[Inlet[I]],
                            outs: List[Outlet[I]],
                          ) extends Shape {
    override val inlets: immutable.Seq[Inlet[I]] = ins
    override val outlets: immutable.Seq[Outlet[I]] = outs

    override def deepCopy(): Shape = BalanceMxN(
      ins.map(_.carbonCopy()),
      outs.map(_.carbonCopy())
    )
  }

  def MxNBalanceGraph[I](n: Int, sources: List[Source[I, NotUsed]]): RunnableGraph[NotUsed] = {
    val balanceMxNImpl = GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge = builder.add(Merge[I](sources.length))
      val balance = builder.add(Balance[I](n))

      merge ~> balance

      BalanceMxN(merge.inlets.toList, balance.outlets.toList)
    }

    val balanceMxNGraph = RunnableGraph.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        def createSink(index: Int) = Sink.fold(0)((count: Int, element: I) => {
          println(s"Received from sink[$index] $element, current count is $count")
          count + 1
        })

        val sinks = (0 until n).map { i =>
          builder.add(createSink(i))
        }

        val balanceMxN = builder.add(balanceMxNImpl)

        sources.zipWithIndex foreach { case (s, i) =>
          s ~> balanceMxN.inlets(i)
        }

        sinks.zipWithIndex foreach { case (s, i) =>
          balanceMxN.outlets(i) -> s
        }

        ClosedShape
      }
    )
    balanceMxNGraph
  }


}
