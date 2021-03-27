package part2_primer

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps

object BackPressureBasics extends App {

  //Elements flow as response to DEMAND from consumers

  // producer --> flow --> consumer

  //Fast Consumer: all well

  // if the consumer is slow, consumer will send a signal to producer to slow down.
  // if the flow cannot comply, it will tell the producer to slow down. This will limit
  // the rate of the elements at the source

  implicit val actorSystem: ActorSystem = ActorSystem("BackPressureBasics")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val fastSource = Source(1 to 1000)

  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000)
    println("Sink: " + x)
  }

  //so the whole idea of back pressure is to
  //slow down producers when there is a slow consumer
  //but..
  //  fastSource.to(slowSink).run()
  // the above is not back pressure. This is because
  // the operators are fuses! Both the source and
  // the sink are running on the same actor.
  // This before next element is sent to flow by source,
  // it has to wait slows 1 second wait for the current element.
  // no back pressure here..

  //  fastSource.async.to(slowSink).run()
  //this is back pressure because fastSource and slowSink
  //run on different actors and there has to be a protocol
  //between them in place to slow down the fastSource.

  val simpleFlow = Flow[Int] map { x =>
    println(s"Incoming $x")
    x + 1
  }

  //  fastSource.async
  //    .via(simpleFlow).async
  //    .to(slowSink)
  //    .run()

  /*
    * Reactions to back pressure:
    *   - try to slow down if possible
    *   - buffer elements until there is more demand
    *   - drop down elements from the buffer if it overflows
    *   - tear down/kill the whole stream
    */

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)

  /*
     flow prints all the elements from 1 to 1000. it receives all the
     elements from the source. Now because we set buffer size to 10 and
     overflowStrategy to dropHead which drops oldest elements in the buffer
     most of these 1000 numbers get dropped.

     And the elements that got to the sink were 2 - 17 (1 - 16 minus the computation)
     and 992 - 101 (991 - 1000). The last 10 elements are easy to explain. They are the newest
     last 10 elements that exited the flow to sink. If the same is true for 2 - 17, it means all the numbers
     in between 16 and 991 (exclusive) were dropped because of dropHead strategy.

     * 1 - 16 messages are sent to sink from flow and they are actually buffered in sink.
     * after filling up sink, sink signals the flow to chill out
     * flow, then, realized that it can contain up to 10 LAST elements. therefore it buffers only 991 - 1000.
     * then, when sink is ready to process more elements it sends a demand signal to the flow.
     * flow, then, sends what is buffered which is elements 991 - 1000.

     Analysis:
     * so the first 16 number nobody is back pressured. This is because sink is able to
       buffer first 16 numbers without sending a back pressure signal to the flow.
     * when sink sends the back pressure signal on the 17th element, flow buffers elements from
       17 - 26.
     * source is fast. it emits 1 to 1000 in a blink of an eye. Sink takes 16 seconds for processing
       elements 1 - 16. On the 17th second, source is done emitting all elements to flow. And because
       flow can keep only 10 elements in the buffer, we only have 991 - 1000.

     So the numbers that exited the flow are from
   */
  //  fastSource.async
  //    .via(bufferedFlow).async //flow buffers 10 elements
  //    .to(slowSink) //sink buffers 16 elements
  //    .run()

  /*
    overflow strategies
      - drop head = drops the oldest
      - drop tail = drops the newest
      - drop new = drop the exact element to be added = keeps the buffer
      - drop the entire buffer
      - back pressure signal
      - fail
   */

  //we can also throttle or ask the producer to limit its production rate
  fastSource.throttle(20, 1 second).runWith(Sink.foreach[Int](println))

}
