package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape2, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, ZipWith}

import java.util.Date

object MoreOpenGraphs extends App {
  implicit val system = ActorSystem("MoreOpenGraphs")
  implicit val materializer = ActorMaterializer()

  /*
    Example: Max3 operator
    - 3 inputs of type int
    - the maximum of the 3
   */

  // step 1
  val max3StaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    //step 2 - define aux SHAPES (components/operators)
    val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
    val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

    // step 3 - combine components (operators)
    max1.out ~> max2.in0

    // step 4 - we have to provide a SHAPE that has 3 inputs and one output
    // notice we first provide output port which is max2.out because FAN-IN only
    // has 1 output port and ZipWith is FAN-IN operator. Then, in the last param,
    // we provide max2.in1 which is the port that HAS NOT BEEN CONNECTED in the SHAPE.
    // IT IS IMPORTANT THAT ALL PORTS ARE TO BE CONNECTED.
    UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
  }

  val source1 = Source(1 to 10)
  val source2 = Source((1 to 10).map(_ => 5))
  val source3 = Source((1 to 10).reverse)

  val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

  // step 1
  val max3RunnableGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      //step 2 - declare SHAPES
      //// you just defined a custom component/operator having
      //// a custom number of ports. congrats!
      val max3Shape = builder.add(max3StaticGraph)

      //step 3 - tie
      source1 ~> max3Shape.in(0)
      source2 ~> max3Shape.in(1)
      source3 ~> max3Shape.in(2)

      max3Shape.out ~> maxSink

      // step 4
      ClosedShape
    }
  )

  //  max3RunnableGraph.run()

  // same for UniformFanOutShape

  /*
    Non-Uniform component for bank transactions

    Processing bank transactions
    Txn suspicious if amount > 10000

    Streams component for txns
    - output 1: let the transaction through
    - output 2: suspicious txn ids
   */

  case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)

  val transactionSource = Source(List(
    Transaction("32e234234234", "Paul", "Jim", 100, new Date),
    Transaction("sef4trce", "Daniel", "Jim", 100000, new Date),
    Transaction("xdfce4rte4t", "Jim", "Alice", 7000, new Date)
  ))

  val bankProcessor = Sink.foreach[Transaction](println)
  val suspiciousAnalysisService = Sink.foreach[String](taxId => println(s"Suspecious transaction ID: $taxId"))

  //step 1
  val suspiciousTxnStaticGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    //step 2 declare components/SHAPES
    val broadcast = builder.add(Broadcast[Transaction](2))
    val suspiciousTxnFilter = builder.add(Flow[Transaction].filter(txn => txn.amount > 10000))
    val txnIdExtractor = builder.add(Flow[Transaction].map[String](txn => txn.id))

    //step 3 tie components
    broadcast.out(0) ~> suspiciousTxnFilter ~> txnIdExtractor

    //step 4
    new FanOutShape2(broadcast.in, broadcast.out(1), txnIdExtractor.out)
  }

  val suspiciousTxnRunnableGraph = RunnableGraph.fromGraph(

    //step 1
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // step 2 define SHAPES
      val suspiciousTxnShape = builder.add(suspiciousTxnStaticGraph)

      //step 3 die them
      transactionSource ~> suspiciousTxnShape.in
      suspiciousTxnShape.out0 ~> bankProcessor
      suspiciousTxnShape.out1 ~> suspiciousAnalysisService

      //step 4: runnable graphs are CLOSED shape
      ClosedShape
    }
  )

  suspiciousTxnRunnableGraph.run()


}
