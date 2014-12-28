package com.m3.octoparts

import play.api.Application
import play.api.libs.ws.{WS, WSClient}

package object ws {
  import scala.language.implicitConversions

  implicit def application2WSClient(app: Application): WSClient = {
    WS.client(app)
  }
}
