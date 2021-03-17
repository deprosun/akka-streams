package part2_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {

  implicit val system: ActorSystem = ActorSystem("FirstPrinciples")

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  //sources
  val source = Source(1 to 10)

  //sinks
  val sink = Sink.foreach[Int](println)

  val graph = source.to(sink)

  //  graph.run()

  //flows transform elements
  val flow = Flow[Int].map(x => x + 1)
  val sourceWithFlow = source.via(flow)
  val flowWithSink = flow.to(sink)

  //  sourceWithFlow.to(sink).run()
  //  source.to(flowWithSink).run()

  //  source.via(flow).to(sink).run()

  // Sources can emit all the message as
  // long as the objects are serialization, like actor messages
  // BUT.. nulls are NOT allowed

  //  val illegalSource = Source.single[String](null)
  //  illegalSource.to(Sink.foreach(println)).run()
  //Exception in thread "main" java.lang.NullPointerException: Element must not be null, rule 2.13
  //use options instead

  //various kinds of sources
  val finiteSource = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3))
  val emptySource = Source.empty[Int]
  val infiniteSource = Source(Stream.from(1)) //do not confuse akka stream with "collection" stream

  import scala.concurrent.ExecutionContext.Implicits.global

  val futureSource = Source.fromFuture(Future(42))

  //sinks
  val theMostBoringSink = Sink.ignore
  val foreachSink = Sink.foreach[Int](println)
  val headSink = Sink.head[Int] //retrieves the head and closes the stream
  val foldSink = Sink.fold[Int, Int](0)((a, b) => a + b)

  //flows - usually mapped to collection operators
  val mapFlow = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(5) //turns any stream to finite stream. takes first 5 and closes the stream
  //drop, filter
  // NOT have flatMap

  //source -> flow -> flow -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
  //  doubleFlowGraph.run()


  //syntactic sugars
  val mapSource = Source(1 to 10).map(x => x * 2) //Source(1 to 10).via(Flow[Int].map(x => x * 2))
  //run streams directly
  //  mapSource.runForeach(println) //mapSource.to(Sink.foreach[Int](println))

  // OPERATORS = components
  /**
    * Exercise: create a stream that takes the name of persons, then you will keep the first 2 names with length > 5 characters
    */

  val lenGreaterThan5FirstTwo = Flow[String].filter(x => x.length > 5).take(2)
  val printIt = Sink.foreach[String](println)
  Source("skdjnc" :: "38er" :: "skjdhcdsv" :: "232fd" :: Nil).via(lenGreaterThan5FirstTwo).to(printIt).run()
}
