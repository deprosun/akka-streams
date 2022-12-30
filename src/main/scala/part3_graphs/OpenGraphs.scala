package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, SinkShape, SourceShape}
import akka.stream.javadsl.RunnableGraph
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source, Zip}

object OpenGraphs extends App {
  implicit val system = ActorSystem("OpenGraphs")
  implicit val materializer = ActorMaterializer()

  /*
    A composite source that concatenates 2 sources
    - emits ALL the elements from the first source
    - then ALL the elements from the second source
   */

  val firstSource = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  // step 1
  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      //step 2: declaring components
      val concat = builder.add(Concat[Int](2))

      //step 3: tying the components
      firstSource ~> concat
      secondSource ~> concat

      //step 4
      SourceShape(concat.out)
    }
  )

  //  sourceGraph.to(Sink.foreach(println)).run()

  /*
    Complex Sink
   */

  val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))

  // step 1
  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      //step 2: declaring components
      val broadcast = builder.add(Broadcast[Int](2))

      //step 3: tying the components
      broadcast ~> sink1
      broadcast ~> sink2

      //step 4
      SinkShape(broadcast.in)
    }
  )

  //  firstSource.to(sinkGraph).run()

  /**
    * Challenge - complex flow?
    * Write your own flow that's composed of two other flows
    * - one that adds 1 to a number
    * - one that does number * 10
    */

  val flow1 = Flow[Int].map(x => x + 1)
  val flow2 = Flow[Int].map(x => x * 10)

  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      //everything operates on SHAPES
      //step 2: declaring components
      val incrementerShape = builder.add(flow1)
      val multiplierShape = builder.add(flow1)

      //step 3: tying the components
      incrementerShape ~> multiplierShape

      //step 4
      FlowShape(incrementerShape.in, multiplierShape.out)
    } // static graph
  ) //component

  firstSource.via(flowGraph).to(Sink.foreach(println)).run()

  /**
    * Exercise: flow from a sink and a source
    */
  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] = {
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._


        //step 2: declaring components
        val sourceShape = builder.add(source)
        val sinkShape = builder.add(sink)
        //step 3: tying the components

        //step 4
        FlowShape(sinkShape.in, sourceShape.out)
      } // static graph
    )
  }

  val f = Flow.fromSinkAndSource(Sink.foreach[String](println), Source(1 to 10))

}
