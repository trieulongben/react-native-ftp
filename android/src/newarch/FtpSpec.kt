package com.ftp

import com.facebook.react.bridge.ReactApplicationContext

abstract class FtpSpec internal constructor(context: ReactApplicationContext) :
  NativeFtpSpec(context) {
}
