package part2_primer

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.joda.time.Instant

import scala.concurrent.Future

object OperatorFusion extends App {

  implicit val system: ActorSystem = ActorSystem("OperatorFusion")

  implicit val materialize: ActorMaterializer = ActorMaterializer()

  def print(n: Int): Unit = {
    val instant = Instant.now()
    println(instant + s" $n")
  }

  //create a simple source
  val simpleSource: Source[Int, NotUsed] = Source(1 to 10)

  //create a flow that maps an integer as input and output another integer by adding 1
  val simpleFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1)
  val simpleFlow2: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x * 10)
  val simpleSink: Sink[Int, Future[Done]] = Sink.foreach[Int](print)

  //this runs on the SAME ACTOR
  //  simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()

  //
  //  2021-03-18T18:56:27.499Z 20
  //  2021-03-18T18:56:27.533Z 30
  //  2021-03-18T18:56:27.533Z 40
  //  2021-03-18T18:56:27.533Z 50
  //  2021-03-18T18:56:27.533Z 60
  //  2021-03-18T18:56:27.533Z 70
  //  2021-03-18T18:56:27.533Z 80
  //  2021-03-18T18:56:27.533Z 90
  //  2021-03-18T18:56:27.533Z 100
  //  2021-03-18T18:56:27.534Z 110
  //

  // when you compose your components into a stream by using methods `via`, `viaMat`, `to`, or `toMat`
  // the composed stream is run on the same actor. This phenomenon is called Operator Fusion or Component Fusion.

  //for example the above stream can be thought of how it would have been written with actor made by you and execution
  //pattern.

  //example----------------------------------------------------------------------------
  // create an actor
  // - this actor does the same three processing of the three flows mentioned above
  //  - first flow is x2 where we increment the input by 1
  //  - seconds flow is y where the result of first flow, x2, is then multiple by 10
  //  - then finally the sink operation where we print the value of last flow y
  //              class SimpleActor extends Actor {
  //                override def receive: Receive = {
  //                  case x: Int =>
  //                    val x2 = x + 1
  //                    val y = x2 * 10
  //
  //                    //sink operation
  //                    println(y)
  //                }
  //              }
  //
  //              val simpleActor = system.actorOf(Props[SimpleActor])
  //
  //              (1 to 1000) foreach (simpleActor ! _)
  //-------------------------------------------------------------------------------------

  // simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()
  // This is by default what Akka Streams does (Operator Fusion that is) and it's nice!
  // It is nice because if the messages were sent to different actors for simpleFlow and simpleFlow2 to execute
  // there would be a delay. That delay is the very thing of sending the message from 1 actor to another.
  // In that sense it honestly is a not so great idea to want that capability.

  // However, it does become a problem when the operations within the flow/sink have some computational overhead.
  // Lets understand this by an example

  val complexFlow = Flow[Int].map { x =>
    //some computational overhead
    Thread.sleep(1000)
    x + 1
  }

  val complexFlow2 = Flow[Int].map { x =>
    //some computational overhead
    Thread.sleep(1000)
    x * 10
  }

  // now lets see if can connect these two complex flows to the original source
  //  simpleSource.via(complexFlow).via(complexFlow2).to(simpleSink).run()

  //  2021-03-18T18:58:07.586Z 20
  //  2021-03-18T18:58:09.618Z 30
  //  2021-03-18T18:58:11.625Z 40
  //  2021-03-18T18:58:13.632Z 50
  //  2021-03-18T18:58:15.634Z 60
  //  2021-03-18T18:58:17.640Z 70
  //  2021-03-18T18:58:19.647Z 80
  //  2021-03-18T18:58:21.651Z 90
  //  2021-03-18T18:58:23.658Z 100
  //  2021-03-18T18:58:25.663Z 110

  // notice the timestamp between each line. While the first non complex example shows that
  // all the lines essentially have the same timestamp - aka super fast, the complex example with computation
  // shows two second difference between each line. One second is coming from complexFlow1 and the other complexFlow2.
  // This gap in time between each line demonstrate very drawback of having Operator Fusion. The actor that executes each element
  // essentially looks like this

  //              class SimpleActor extends Actor {
  //                override def receive: Receive = {
  //                  case x: Int =>
  //                    Thread.sleep(1000)
  //                    val x2 = x + 1
  //
  //                    Thread.sleep(1000)
  //                    val y = x2 * 10
  //
  //                    //sink operation
  //                    println(y)
  //                }
  //              }

  // so how can we make this better?
  // When the operators are expensive we can run these operations parallel
  // on different actors. Lets try it out

  //  simpleSource.via(complexFlow).async //runs on one actor
  //    .via(complexFlow2).async //runs on another actor
  //    .to(simpleSink) //runs on another actor
  //    .run()

  //  2021-03-18T19:26:25.720Z 20
  //  2021-03-18T19:26:26.718Z 30
  //  2021-03-18T19:26:27.718Z 40
  //  2021-03-18T19:26:28.722Z 50
  //  2021-03-18T19:26:29.723Z 60
  //  2021-03-18T19:26:30.726Z 70
  //  2021-03-18T19:26:31.727Z 80
  //  2021-03-18T19:26:32.728Z 90
  //  2021-03-18T19:26:33.729Z 100
  //  2021-03-18T19:26:34.733Z 110

  // You can see that we have now only 1 second difference between the lines.
  // This is because complexFlow and complexFlow2 are running is separate actors
  // and the sleep routine is only blocking their respective actors. In other words,
  // the actor that is complexFlow is on the next element after waiting 1 second, instead of 2!
  // Hence it sped up the whole execution by double!


  //ordering
  //  Source(1 to 3)
  //    .map(e => {println("Flow A: " + e);e})
  //    .map(e => {println("Flow B: " + e);e})
  //    .map(e => {println("Flow C: " + e);e})
  //    .to(Sink.ignore)
  //    .run()

  //  Flow A: 1
  //  Flow B: 1
  //  Flow C: 1
  //  Flow A: 2
  //  Flow B: 2
  //  Flow C: 2
  //  Flow A: 3
  //  Flow B: 3
  //  Flow C: 3

  //relative order is intact

  Source(1 to 3)
    .map(e => {println("Flow A: " + e);e}).async
    .map(e => {println("Flow B: " + e);e}).async
    .map(e => {println("Flow C: " + e);e}).async
    .to(Sink.ignore)
    .run()

  //-- run 1 --
  //  Flow A: 1
  //  Flow A: 2
  //  Flow A: 3
  //  Flow B: 1
  //  Flow B: 2
  //  Flow C: 1
  //  Flow B: 3
  //  Flow C: 2
  //  Flow C: 3

  // -- run 2 --
  //  Flow A: 1
  //  Flow A: 2
  //  Flow B: 1
  //  Flow A: 3
  //  Flow B: 2
  //  Flow C: 1
  //  Flow B: 3
  //  Flow C: 2
  //  Flow C: 3


}
