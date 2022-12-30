package part4_techniques

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.{ActorAttributes, ActorMaterializer}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object FaultTolerance extends App {

  implicit val system = ActorSystem("FaultTolerance")
  implicit val materializer = ActorMaterializer()

  // 1 - logging
  // when you want to know what's happening in your stream, log them.
  val faultySource = Source(1 to 10).map(e => if (e == 6) throw new RuntimeException else e)
  //  faultySource.log("trackingElementxs").to(Sink.ignore).run()

  // 2 - gracefully terminating a stream
  faultySource.recover {
    // this replaces the error element with a new value
    case _: RuntimeException => Int.MinValue
  }.log("gracefulSource")
    .to(Sink.ignore)
  //    .run()

  // 3 - recover with another stream
  // this technique replaces the whole source
  // when an error occurs
  faultySource.recoverWithRetries(3, {
    case _: RuntimeException => Source(90 to 99)
  })
    .log("recoverWithRetries")
    .to(Sink.ignore)
  //    .run()

  // 4 - backoff supervision
  val restartSource = RestartSource
    .onFailuresWithBackoff(
      minBackoff = 1 second,
      maxBackoff = 30 seconds,
      randomFactor = 0.2)(() => {
      val randomNumber = new Random().nextInt(20)
      Source(1 to 10).map(elem => if (elem == randomNumber) throw new RuntimeException else elem)
    })
    .log("restartBackoff")
    .to(Sink.ignore)
  //    .run()

  // 5 - supervision strategy
  val numbers = Source(1 to 20).map(n => if (n == 13) throw new RuntimeException("bad luck") else n)
    .log("supervision")

  val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
    /*
      Resume = skips the faulty element
      Stop = stop the stream
      Restart = resume + clears internal state
     */
    case _: RuntimeException => Resume
    case _ => Stop
  })

  supervisedNumbers.to(Sink.ignore).run()
}
