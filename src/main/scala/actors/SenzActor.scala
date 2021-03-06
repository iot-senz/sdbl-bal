package actors

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import config.Configuration
import crypto.RSAUtils
import org.slf4j.LoggerFactory
import protocols.{Senz, SenzType}
import utils.{BalUtils, SenzParser, SenzUtils}

object SenzActor {

  case class InitSenz()

  case class SenzMsg(msg: String)

  def props: Props = Props(new SenzActor)

}

class SenzActor extends Actor with Configuration {

  import SenzActor._
  import context._

  def logger = LoggerFactory.getLogger(this.getClass)

  // connect to senz tcp
  val remoteAddress = new InetSocketAddress(InetAddress.getByName(switchHost), switchPort)
  IO(Tcp) ! Connect(remoteAddress)

  override def preStart() = {
    logger.debug("Start actor: " + context.self.path)
  }

  override def receive: Receive = {
    case c@Connected(remote, local) =>
      logger.debug("TCP connected")

      // tcp conn
      val connection = sender()
      connection ! Register(self)

      // send reg message
      val regSenzMsg = SenzUtils.getRegistrationSenzMsg
      connection ! Write(ByteString(regSenzMsg))

      // wait register
      context.become(registering(connection))
    case CommandFailed(_: Connect) =>
      // failed to connect
      logger.error("CommandFailed[Failed to connect]")
  }

  def registering(connection: ActorRef): Receive = {
    case CommandFailed(w: Write) =>
      logger.error("CommandFailed[Failed to write]")
    case Received(data) =>
      val senzMsg = data.decodeString("UTF-8")
      logger.debug("Received senzMsg : " + senzMsg)

      // wait for REG status
      // parse senz first
      val senz = SenzParser.getSenz(senzMsg)
      senz match {
        case Senz(SenzType.DATA, `switchName`, receiver, attr, signature) =>
          attr.get("msg") match {
            case Some("REG_DONE") =>
              logger.info("Registration done")

              // senz listening
              context.become(listening(connection))
            case Some("REG_ALR") =>
              logger.info("Already registered, continue system")

              // senz listening
              context.become(listening(connection))
            case Some("REG_FAIL") =>
              logger.error("Registration fail, stop system")
              context.stop(self)
            case other =>
              logger.error("UNSUPPORTED DATA message " + other)
          }
        case any =>
          logger.debug(s"Not support other messages $data this stats")
      }
  }

  def listening(connection: ActorRef): Receive = {
    case CommandFailed(w: Write) =>
      logger.error("CommandFailed[Failed to write]")
    case Received(data) =>
      val senzMsg = data.decodeString("UTF-8")
      logger.debug("Received senzMsg : " + senzMsg)

      // only handle bal here
      // parse senz first
      val senz = SenzParser.getSenz(senzMsg)
      senz match {
        case Senz(SenzType.GET, sender, receiver, attr, signature) =>
          // handle request via bal actor
          val bal = BalUtils.getBal(senz)
          context.actorOf(BalHandler.props(bal))
        case any =>
          logger.debug(s"Not support other messages $data this stats")
      }
    case _: ConnectionClosed =>
      logger.debug("ConnectionClosed")
      context.stop(self)
    case SenzMsg(msg) =>
      // sign senz
      val senzSignature = RSAUtils.signSenz(msg.trim.replaceAll(" ", ""))
      val signedSenz = s"$msg $senzSignature"

      logger.info("Senz: " + msg)
      logger.info("Signed senz: " + signedSenz)

      connection ! Write(ByteString(signedSenz))
  }

}
