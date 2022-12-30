package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, Graph, SinkShape, UniformFanOutShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {

  implicit val system: ActorSystem = ActorSystem("GraphMaterializedValues")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val wordSource = Source(List("Akka", "is", "awesome", "rock", "the", "jvm"))
  val printer = Sink.foreach[String](println)
  val counter: Sink[String, Future[Int]] = Sink.fold[Int, String](0) { (count, _) => count + 1 }

  /*
    A composite component (sink)
      - prints out all the strings which are lowerware
      - COUNT the strings that are short (< 5 chars) <-- materialize this
   */

  //step 1
  val complexWordSink = GraphDSL.create(printer, counter)((printerMaterializedValue, counterMaterializedValue) => counterMaterializedValue) { implicit builder =>
    (printerShape, counterShape) =>
      import GraphDSL.Implicits._

      //step 2 define SHAPES/components/operators
      val broadcast = builder.add(Broadcast[String](2))
      val isShort = builder.add(Flow[String].filter(_.length < 5))
      val isLowercase = builder.add(Flow[String].filter(x => x.toLowerCase == x))

      //step 3 tie them together
      broadcast.out(0) ~> isShort ~> counterShape
      broadcast.out(1) ~> isLowercase ~> printerShape

      //step 4
      SinkShape(broadcast.in)
  }

//  val shortStringsCountFuture = wordSource.toMat(complexWordSink)(Keep.right).run()
//
  import system.dispatcher
//
//  shortStringsCountFuture.onComplete {
//    case Success(count) => println(s"The total number of short strings is: $count")
//    case Failure(exception) => println(s"The count of short strings failed: $exception")
//  }

  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {

    val counter: Sink[B, Future[Int]] = Sink.fold[Int, B](0) { (count, _) => count + 1 }

    Flow.fromGraph(

      //step 1
      GraphDSL.create(counter) { implicit builder =>
        counterSinkShape =>
          import GraphDSL.Implicits._

          //step 2: define components/SHAPES
          val originalFlow = builder.add(flow)
          val broadcast = builder.add(Broadcast[B](2)) //fan-out operator

          //step 3: tie them together
          originalFlow ~> broadcast
          broadcast ~> counterSinkShape

          FlowShape(originalFlow.in, broadcast.out(1))
      }
    )

  }

  val simpleSource = Source(1 to 42)
  val simpleFlow = Flow[Int].map(x => x)
  val simpleSink = Sink.ignore

  val enhancedCountFuture = simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(simpleSink)(Keep.left).run()

  enhancedCountFuture.onComplete {
    case Success(value) => println(s"Count elements went through the enhanced flow: $value")
    case Failure(ex) => println("Something went wrong")
  }
}
