package part5_advanced

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, Graph, KillSwitches, UniqueKillSwitch}

import scala.concurrent.duration._
import scala.language.postfixOps

object DynamicStreamHandling extends App {

  implicit val system = ActorSystem("DynamicStreamHandling")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  // dynamically start/abort a stream
  // #1: Kill Switch
  val killSwitchFlow: Graph[FlowShape[Int, Int], UniqueKillSwitch] = KillSwitches.single[Int]

  val counter = Source(Stream.from(1)).throttle(1, 1 seconds).log("counter")
  val sink = Sink.ignore


  //  val killSwitch = counter
  //    .viaMat(killSwitchFlow)(Keep.right)
  //    .to(sink)
  //    .run()
  //
  //
  //  system.scheduler.scheduleOnce(3 seconds){
  //    killSwitch.shutdown()
  //  }

  val anotherCounter = Source(Stream.from(1)).throttle(2, 1 seconds).log("anotherCounter")
  val sharedKillSwitch = KillSwitches.shared("oneButtonToRuleThemAll")

  //  counter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  //  anotherCounter.via(sharedKillSwitch.flow).runWith(Sink.ignore)
  //
  //  system.scheduler.scheduleOnce(3 seconds) {
  //    sharedKillSwitch.shutdown()
  //  }

  // add fan in and fan out components
  // MergeHub

  val dynamicMerge: Source[Int, Sink[Int, NotUsed]] = MergeHub.source[Int]
  val materializedSink: Sink[Int, NotUsed] = dynamicMerge.to(Sink.foreach[Int](println)).run()

  // good for plugging sources to the same consumer
  // we can use this sink anytime we like
  //  Source(1 to 10).runWith(materializedSink)
  //  counter.runWith(materializedSink)

  // BroadcastHub
  val dynamicBroadcast = BroadcastHub.sink[Int]
  val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)

  //  materializedSource.runWith(Sink.ignore)
  //  materializedSource.runWith(Sink.foreach[Int](println))

  /**
    * Challenge - combine a mergeHub and a broadcastHub
    *
    * A publisher-subscriber component
    */
  val merge = MergeHub.source[String]
  val bcast = BroadcastHub.sink[String]
  val (publisherPort, subscriberPort) = merge.toMat(bcast)(Keep.both).run()

  subscriberPort.runWith(Sink.foreach(e => println(s"I received: $e")))
  subscriberPort.map(s => s.length).runWith(Sink.foreach(n => println(s"I got a number: $n")))

  Source(List("Akka", "is", "amazing")).runWith(publisherPort)
  Source(List("I", "love", "Scala")).runWith(publisherPort)
  Source.single("STREEEEAMS").runWith(publisherPort)


}
