package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, OverflowStrategy, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}

object GraphCycles extends App {

  implicit val system = ActorSystem("GraphCycles")
  implicit val materializer = ActorMaterializer()

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

  //  RunnableGraph.fromGraph(accelerator).run()
  // the problem is that we always increase the number in the graph
  // and never get rid of any. the elements that are generated within the
  // graph start to fill up the buffer and elements are back-pressured all
  // the way till the source where the source stops to emit elements all together.
  // This is called graph cycle deadlock!

  /*
    Solution 1: MergePreferred
   */

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }
  //
  // the reason why this graph works is because merge
  // waits for an element on its preferred port. at the beginning
  // there is no elements in the preferred port, so it's fine to take
  // an element out of the source.  after the first element is emitted
  // out of source and into incrementer and then incrementer to preferred
  // port of merge, then the merge will always take elements from the preferred
  // port and feed it into the incrementer.  this always accelerates that
  // number.
  //
  // CONS: this approach only accelerates first emitted number from source.
  //       source is stuck.
  //

  //  RunnableGraph.fromGraph(actualAccelerator).run()

  /*
    Solution 2: buffers
   */

  val bufferedRepeater = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape = builder.add(MergePreferred[Int](1))
    val repeaterShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Repeatibg $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeaterShape
    mergeShape.preferred <~ repeaterShape

    ClosedShape
  }
  //
  // some of the numbers are repeated quite of few times, but
  // on average the numbers are increasing. which means, new elements
  // are fed from the source. and this is how we break the deadlock with
  // buffers.
  //

  /*
    cycles risks deadlocking
      - add bounds to the number of elements in the cycle
   */

  //  RunnableGraph.fromGraph(bufferedRepeater).run()

  /**
    * Challenge: create a fan-in shape
    * - two inputs which will be fed with EXACTLY ONE number
    * - output will emit an INFINITE FIBONACCI SEQUENCE based off 2 numbers
    */

  val fibonacciGenerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val zip = builder.add(Zip[BigInt, BigInt])
    val mergeShape = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val newNumberFlowShape = builder.add(Flow[(BigInt, BigInt)].map { case (last, previous) =>

      // for demo slow down
      Thread.sleep(100)
      (last + previous, last)
    })
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map { case (last, _) =>
      last
    })
    val broadcast = builder.add(Broadcast[(BigInt, BigInt)](2))

    zip.out ~> mergeShape

    mergeShape.out ~> newNumberFlowShape

    newNumberFlowShape.out ~> broadcast

    broadcast.out(0) ~> extractLast
    broadcast.out(1) ~> mergeShape.preferred

    UniformFanInShape(extractLast.out, zip.in0, zip.in1)
  }

  val fiboGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val source1Shape = builder.add(Source.single[BigInt](1))
      val source2Shape = builder.add(Source.single[BigInt](1))
      val sink = builder.add(Sink.foreach[BigInt](println))
      val fibo = builder.add(fibonacciGenerator)

      source1Shape ~> fibo.in(0)
      source2Shape ~> fibo.in(1)
      fibo.out ~> sink

      ClosedShape
    }
  )
  fiboGraph.run()


}
