package part2_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {

  implicit val system: ActorSystem = ActorSystem("MaterializingStreams")

  import system.dispatcher

  //what is materializer?
  //  - its one of these objects that allocates the right resources to running an akka stream
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  //it is statis because weraph: RunnableGraph[NotUsed] = Source(1 to 10).to(Sink.foreach(println))
  //
  //    //here NotUsed means a glorified unit
  //    //  val simpleMaterializedValue: NotUsed = simpleGraph.run()
  //
  //    val source = Source(1 to 10)
  //    val sink = Sink.reduce[Int]((a, b) => a + b)
  //    //  val sumFuture: Future[Int] = source.runWith(sink)
  //    //
  //    //  sumFuture.onComplete {
  //    //    case Success(value) => println(s"The sum of all value is $value")
  //    //    case Failure(ex) => println(s"The sum of the elements could not be computer: $ex")
  //    //  }
  //
  //    //choosing materialized values
  //    val simpleSource: Source[Int, NotUsed] = Source(1 to 10)
  //    val simpleFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1)
  //    val simpleSink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)
  //
  //    //BIG take away. keep doing this
  //    val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
  //
  //    graph.run().onComplete {
  //      case Success(_) => println("Stream processing finished")
  //      case Failure(ex) => println(s"Stream processing failed: $ex")
  //    }
  //
  //    //sugars
  //    Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) //source.to(Sink.reduce)(Keep.right)
  //    Source(1 to 10).runReduce[Int](_ + _) //same
  //
  //    //backwards
  //    Sink.foreach[Int](println).runWith(Source.single[Int](42)) //source(..).to(sink..).run()
  //
  //    //both ways
  //    Flow[Int].map(x => x + 2).runWith( still need to call run
  //  val simpleGsimpleSource, simpleSink)

  /**
    * - Ex1 : return the last element out of a source (use Sink.last)
    * - Ex2 : compute the total word count of a stream of sentences
    *         - map, fold, reduce
    */


  val source1: Source[Int, NotUsed] = Source(1 to 10)
  val sink1 = Sink.last[Int]
  val graph1: RunnableGraph[Future[Int]] = source1.toMat(sink1)(Keep.right)
  graph1.run().onComplete {
    case Success(value) => println(s"last element: $value")
    case Failure(ex) => println(s"failed to compute the last element of the source: $ex")
  }

  val sentences = List(
    "hello world.",
    "my name is Karan.",
    "How are you?"
  )

  val source2 = Source(sentences)
  val flowWithMap: Flow[String, Int, NotUsed] = Flow[String].map(x => x.split(" ").length)
  val flowWithReduce: Flow[Int, Int, NotUsed] = Flow[Int].reduce[Int]((x, y) => x + y)
  val flowWithFold: Flow[Int, Int, NotUsed] = Flow[Int].fold[Int](0) { (acc, x) => acc + x }
  val sinkWithReduce = Sink.reduce[Int]((x, y) => x + y)
  val sinkWithFold = Sink.fold[Int, Int](0) { (acc, x) => acc + x }
  val sinkWithFoldAll = Sink.fold[Int, String](0) { (acc, x) => acc + x.split(" ").length }


  //use the source with flowMap and flowReduce
  //  flowMap: this splits each sentence into words and then counts the length generated
  //  flowReduce: this performs a sum on streams coming in into 1 number
  val wordCountWithFlowMapFlowReduceSinkHead: Future[Int] = source2
    .viaMat(flowWithMap)(Keep.right)
    .viaMat(flowWithReduce)(Keep.right)
    .toMat(Sink.head[Int])(Keep.right)
    .run()

  wordCountWithFlowMapFlowReduceSinkHead.onComplete {
    case Success(value) => println(s"total word count is $value")
    case Failure(ex) => println(s"could not compute total word count $ex")
  }

  //this uses flow map to split and get lengths
  //then used sink with fold to combine all the results into 1
  val graph3: Future[Int] = source2
    .viaMat(flowWithMap)(Keep.right)
    .toMat(sinkWithFold)(Keep.right)
    .run()

  //  source2
  //      .viaMat(flowWithReduce)

  graph3.onComplete {
    case Success(value) => println(s"total word count is $value")
    case Failure(ex) => println(s"could not compute total word count $ex")
  }

  //this uses flow map to split and get lengths
  //then used sink with reduce to combine all the results into 1
  val graph4: Future[Int] = source2
    .viaMat(flowWithMap)(Keep.right)
    .toMat(sinkWithReduce)(Keep.right)
    .run()

  graph4.onComplete {
    case Success(value) => println(s"total word count is $value")
    case Failure(ex) => println(s"could not compute total word count $ex")
  }

  //this uses flow map to split and get lengths
  //then used sink with reduce to combine all the results into 1
  val graph5 = source2
    .toMat(sinkWithFoldAll)(Keep.right)
    .run()

  graph5.onComplete {
    case Success(value) => println(s"total word count is $value")
    case Failure(ex) => println(s"could not compute total word count $ex")
  }

}
