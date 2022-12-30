package part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape, SinkShape}
import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, RunnableGraph, Sink, Source}

object BidirectionalFlows extends App {
  implicit val system = ActorSystem("BidirectionalFlows")
  implicit val materializer = ActorMaterializer()

  /*
    EXAMPLE: cryptography
   */
  def encrypt(n: Int)(string: String) = string.map(c => (c + n).toChar)

  def decrypt(n: Int)(string: String) = string.map(c => (c - n).toChar)

  val n = 3
  val encryptFlow = Flow[String].map[String](x => encrypt(n)(x))
  val decryptFlow = Flow[String].map[String](x => decrypt(n)(x))

  //bidiFlow (bidirectional flows)
  //step 1
  val bidirectionalFlow = GraphDSL.create() { implicit builder =>

    //step 2 define SHAPES
    val encryptFlowShape = builder.add(encryptFlow)
    val decryptFlowShape = builder.add(decryptFlow)

    //step 3 tie them together
    BidiShape.fromFlows(encryptFlowShape, decryptFlowShape)
  }

  val unecryptedStrings = List("akka", "is", "awesome", "testing", "bidirectional", "flows")
  val unencryptedSource = Source(unecryptedStrings)
  val encryptedSource = Source(unecryptedStrings.map(encrypt(n)))

  val cryptoBidiGraph = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val bidi = builder.add(bidirectionalFlow)
      val unecryptedStringsShape = builder.add(unencryptedSource)
      val encryptedSourceShape = builder.add(encryptedSource)
      val encryptedSink = Sink.foreach[String](string => println(s"Encrypted $string"))
      val decryptedSink = Sink.foreach[String](string => println(s"Decrypted $string"))

      unecryptedStringsShape ~> bidi.in1
      bidi.out1 ~> encryptedSink

      //      encryptedSourceShape ~> bidi.in2
      //      bidi.out2 ~> decryptedSink
      // both tilde arrows
      decryptedSink <~ bidi.out2;
      bidi.in2 <~ encryptedSourceShape

      ClosedShape
    }
  )

  cryptoBidiGraph.run()

  /*
  - encrypting/decrypting
  - encoding/decoding
  - serializing/deserializing
   */

}
