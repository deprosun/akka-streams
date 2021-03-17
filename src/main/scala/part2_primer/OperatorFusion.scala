package part2_primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Source}

object OperatorFusion extends App {

  implicit val system: ActorSystem = ActorSystem("OperatorFusion")

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  //create a simple source
  val simpleSource: Source[Int, NotUsed] = Source(1 to 10)

  //create a flow that maps an integer as input and output another integer by adding 1
  val simpleFlow: Flow[Int, Int, NotUsed] = Flow[Int].map(x => x + 1)

}
